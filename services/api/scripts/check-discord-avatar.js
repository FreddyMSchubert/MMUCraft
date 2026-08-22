const assert = require('node:assert/strict');
const sharp = require('sharp');
const { PlayerAvatarController } = require('../dist/players/player-avatar.controller');
const {
	formatDiscordWebhookMessage,
	formatOnlinePlayers,
} = require('../dist/discord/discord.service');

async function check() {
	const face = await sharp({
		create: { width: 8, height: 8, channels: 4, background: '#112233' },
	})
		.png()
		.toBuffer();
	const hat = await sharp({ create: { width: 8, height: 8, channels: 4, background: '#abcdef' } })
		.png()
		.toBuffer();
	const head = await sharp({
		create: { width: 64, height: 64, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
	})
		.composite([
			{ input: face, left: 8, top: 8 },
			{ input: hat, left: 40, top: 8 },
		])
		.png()
		.toBuffer();
	const originalFetch = global.fetch;
	const texture = Buffer.from(
		JSON.stringify({
			textures: { SKIN: { url: 'https://textures.minecraft.net/texture/test' } },
		}),
	).toString('base64');
	let fetches = 0;
	global.fetch = async () =>
		++fetches === 1
			? new Response(
					JSON.stringify({
						id: '8667ba71b85a4004af54457a9734eed7',
						name: 'Notch',
						properties: [{ name: 'textures', value: texture }],
					}),
					{ status: 200, headers: { 'content-type': 'application/json' } },
				)
			: new Response(head, { status: 200, headers: { 'content-type': 'image/png' } });

	try {
		let selects = 0;
		const database = {
			connection: {
				select: () => ({
					from: () => ({
						where: () => ({
							get: () =>
								++selects === 1
									? { id: 7, minecraft_username: 'Notch' }
									: { color_hex: '#FF00AA' },
						}),
					}),
				}),
			},
		};
		const file = await new PlayerAvatarController(database).discordAvatar(
			'8667ba71-b85a-4004-af54-457a9734eed7',
		);
		const chunks = [];
		for await (const chunk of file.getStream()) chunks.push(chunk);
		const metadata = await sharp(Buffer.concat(chunks)).metadata();
		assert.equal(metadata.format, 'png');
		assert.equal(metadata.width, 128);
		assert.equal(metadata.height, 128);
		const pixels = await sharp(Buffer.concat(chunks)).raw().toBuffer();
		assert.deepEqual(
			[...pixels.subarray((6 * 128 + 64) * 4, (6 * 128 + 64) * 4 + 3)],
			[255, 0, 170],
		);
		assert.equal(pixels[3], 0);
		assert.deepEqual(
			[...pixels.subarray((64 * 128 + 64) * 4, (64 * 128 + 64) * 4 + 3)],
			[171, 205, 239],
		);
		await assert.rejects(() =>
			new PlayerAvatarController(database).discordAvatar('not-a-uuid'),
		);
		assert.deepEqual(
			formatDiscordWebhookMessage({
				type: 'chat',
				minecraft_username: 'Freddy',
				minecraft_uuid: 'id',
				content: 'Hello!',
				role: 'Committee',
				nickname: 'Fred',
				pronouns: 'he/him',
				color_hex: '#ff00aa',
			}),
			{ username: 'Freddy [Committee]', content: 'Hello!', isServer: false },
		);
		assert.deepEqual(
			formatDiscordWebhookMessage({
				type: 'server',
				minecraft_username: 'Freddy',
				minecraft_uuid: 'id',
				content: 'Restarting.',
				role: 'Committee',
				nickname: 'Fred',
				pronouns: 'he/him',
				color_hex: '#ff00aa',
			}),
			{
				username: 'Minecraft Server',
				content:
					'```ansi\n👾 \u001b[35mFreddy\u001b[0m\u001b[36m [Committee]\u001b[0m Restarting.\n```',
				isServer: true,
			},
		);
		assert.deepEqual(
			formatDiscordWebhookMessage({
				type: 'death',
				minecraft_username: 'Freddy',
				minecraft_uuid: 'id',
				content: 'fell from a high place',
				role: 'Committee',
				nickname: 'Fred',
				pronouns: 'he/him',
				color_hex: '#ff00aa',
			}),
			{
				username: 'Minecraft Server',
				content:
					'```ansi\n☠️ \u001b[35mFreddy\u001b[0m\u001b[36m [Committee]\u001b[0m fell from a high place\n```',
				isServer: true,
			},
		);
		assert.equal(
			formatOnlinePlayers([
				{ minecraftUsername: 'Freddy', color: '#ff00aa', role: 'Committee' },
				{ minecraftUsername: 'Merlinspace', color: '#00ff00', role: 'Member' },
			]),
			'```ansi\n\u001b[37mPlayers online:\u001b[0m\n\u001b[35mFreddy\u001b[0m\u001b[36m [Committee]\u001b[0m\n\u001b[32mMerlinspace\u001b[0m\u001b[32m [Member]\u001b[0m\n```',
		);
		assert.equal(
			formatOnlinePlayers([]),
			'```ansi\n\u001b[37mPlayers online:\u001b[0m\nNo players online.\n```',
		);
	} finally {
		global.fetch = originalFetch;
	}
}

check().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
