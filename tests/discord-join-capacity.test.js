const assert = require('node:assert/strict');
const { test } = require('node:test');
const { formatDiscordWebhookMessage } = require('../services/api/dist/discord/discord.service');

test('Discord joins show the cap and color the full count red', () => {
	const event = {
		type: 'join',
		minecraft_username: 'Player',
		minecraft_uuid: '',
		content: 'joined the server. (17 online)',
		role: '',
		nickname: '',
		pronouns: '',
		color_hex: '',
		online_players: [],
	};
	assert.match(formatDiscordWebhookMessage(event, 18).content, /\(17\/18 online\)/);
	assert.equal(formatDiscordWebhookMessage(event, 18).content.includes('\u001b[31m'), false);
	assert.equal(
		formatDiscordWebhookMessage(
			{
				...event,
				type: 'first_join',
				content: 'joined the server for the first time. (18 online)',
			},
			18,
		).content.includes('\u001b[31m(18/18 online)\u001b[0m'),
		true,
	);
	assert.match(
		formatDiscordWebhookMessage(
			{ ...event, type: 'leave', content: 'left the server. (17 online)' },
			18,
		).content,
		/\(17 online\)/,
	);
});

test('waiting room events use the main server formatting and committee colors', () => {
	const event = {
		type: 'join',
		minecraft_username: 'Player',
		minecraft_uuid: '',
		content: 'joined the server. (18 online)',
		role: 'Committee',
		nickname: '',
		pronouns: '',
		color_hex: '#00aaff',
		online_players: [],
	};
	const main = formatDiscordWebhookMessage(event, 18);
	const waiting = formatDiscordWebhookMessage({ ...event, type: 'waiting_join' }, 18);
	assert.deepEqual(waiting, main);
	assert.ok(waiting.content.includes('\u001b[36m [Committee]\u001b[0m'));
	assert.ok(waiting.content.includes('\u001b[31m(18/18 online)\u001b[0m'));
	assert.deepEqual(
		formatDiscordWebhookMessage({ ...event, type: 'waiting_chat', content: 'hello' }),
		formatDiscordWebhookMessage({ ...event, type: 'chat', content: 'hello' }),
	);
});
