const assert = require('node:assert/strict')
const sharp = require('sharp')
const { DiscordAvatarController } = require('../dist/discord/discord-avatar.controller')

async function check() {
	const head = await sharp({
		create: { width: 8, height: 8, channels: 4, background: '#ffffff' },
	}).png().toBuffer()
	const originalFetch = global.fetch
	global.fetch = async () => new Response(head, { status: 200, headers: { 'content-type': 'image/png' } })

	try {
		const file = await new DiscordAvatarController().avatar('8667ba71-b85a-4004-af54-457a9734eed7', 'ff00aa')
		const chunks = []
		for await (const chunk of file.getStream()) chunks.push(chunk)
		const metadata = await sharp(Buffer.concat(chunks)).metadata()
		assert.equal(metadata.format, 'png')
		assert.equal(metadata.width, 128)
		assert.equal(metadata.height, 128)
		await assert.rejects(() => new DiscordAvatarController().avatar('not-a-uuid', 'ff00aa'))
	} finally {
		global.fetch = originalFetch
	}
}

check().catch((error) => {
	console.error(error)
	process.exitCode = 1
})
