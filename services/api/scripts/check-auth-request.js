const assert = require('node:assert/strict')
process.env.SIGNUP_ALLOWLIST_PATH ??= require('node:path').join(__dirname, 'signup-allowlist.txt')
const { AUTH_CODE_ITEMS, createAuthCode, isAllowedEmail } = require('../dist/auth/auth.util')
const { AuthService } = require('../dist/auth/auth.service')
const { PlayerBansService } = require('../dist/auth/player-bans.service')
const { PlayersService } = require('../dist/players/players.service')
const { DatabaseService, sessions, users } = require('../dist/database/database.service')

function assertAuthCode(code) {
	const items = code.split('|')
	assert.equal(items.length, 3)
	assert.ok(items.every((item) => AUTH_CODE_ITEMS.includes(item)))
}

assertAuthCode(createAuthCode())

assert.equal(isAllowedEmail('anything@mmu.ac.uk'), true)
assert.equal(isAllowedEmail('12345678@stu.mmu.ac.uk'), true)
assert.equal(isAllowedEmail('anything@stu.mmu.ac.uk'), false)

async function checkFlows() {
	process.env.DATABASE_URL = ':memory:'
	process.env.RESEND_API_KEY = 'test-key'
	const deliveredCodes = new Map()
	global.fetch = async (_url, options) => {
		const body = JSON.parse(options.body)
		const code = body.text.match(/code is (.+)\. It expires/)?.[1].replaceAll(' → ', '|')
		assertAuthCode(code)
		deliveredCodes.set(body.to[0], code)
		return { ok: true, status: 202 }
	}
	const database = new DatabaseService()
	let pendingJoin
	let pendingJoinUpdates = 0
	const grpc = {
		blacklistPlayer: async () => undefined,
		purchaseExternalPlayerInvite: async () => ({ purchased: true, balance_dabloons: 900 }),
		removePendingJoin: async () => undefined,
		unblacklistPlayer: async () => undefined,
		upsertPendingJoin: async (request) => { pendingJoin = request; pendingJoinUpdates++ },
	}
	const bans = new PlayerBansService(database)
	const auth = new AuthService(database, grpc, bans)
	const now = Date.now()

	const member = database.connection.insert(users).values({
		email: 'member@stu.mmu.ac.uk',
		minecraft_uuid: '0123456789abcdef0123456789abcdef',
		minecraft_username: 'Member',
		whitelisted_at_unix_ms: now,
		rules_accepted_at_unix_ms: now,
		created_at_unix_ms: now,
	}).returning({ id: users.id }).get()

	await assert.rejects(auth.createSignup('guest@example.com', '127.0.0.1'), /numeric @stu\.mmu\.ac\.uk address/)
	await assert.rejects(auth.addEmailToWhitelist({ id: member.id }, 'guest@.example.com', member.id), /valid email address/)
	await assert.rejects(auth.addEmailToWhitelist({ id: member.id }, 'guest@example.com'), /responsible user/)
	await auth.addEmailToWhitelist({ id: member.id }, 'Guest@Example.com', member.id)
	assert.deepEqual(auth.listEmailWhitelist().entries.map((entry) => entry.email), ['guest@example.com'])
	assert.equal(auth.listEmailWhitelist().entries[0].addedByMinecraftUsername, 'Member')
	assert.equal(auth.listEmailWhitelist().entries[0].responsibleMinecraftUsername, 'Member')
	await auth.createSignup('guest@example.com', '127.0.0.1')
	auth.removeEmailFromWhitelist('guest@example.com')

	const signin = await auth.signIn('member@stu.mmu.ac.uk', '127.0.0.1')
	assert.ok((await auth.verifySignIn(signin.flowId, deliveredCodes.get('member@stu.mmu.ac.uk'))).token)

	const admin = database.connection.insert(users).values({
		email: 'admin@mmu.ac.uk',
		minecraft_uuid: '1123456789abcdef0123456789abcdef',
		minecraft_username: 'Admin',
		is_committee: 1,
		whitelisted_at_unix_ms: now,
		rules_accepted_at_unix_ms: now,
		created_at_unix_ms: now,
	}).returning({ id: users.id }).get()
	const managedPlayer = database.connection.insert(users).values({
		email: 'managed@mmu.ac.uk',
		minecraft_username: 'ManagedPlayer',
		whitelisted_at_unix_ms: now,
		rules_accepted_at_unix_ms: now,
		created_at_unix_ms: now,
	}).returning({ id: users.id }).get()
	const players = new PlayersService(database, {}, {}, {})
	await assert.rejects(
		players.updateProfile({ id: member.id, isCommittee: false }, String(managedPlayer.id), {}),
		/Committee access is required/,
	)
	assert.equal((await players.updateProfile({ id: admin.id, isCommittee: true }, String(managedPlayer.id), {
		preferredName: 'Updated by admin',
		color: null,
	})).profile.preferredName, 'Updated by admin')
	await auth.applyPlayerBan({ id: admin.id }, member.id, null)
	assert.equal(database.connection.select().from(sessions).all().length, 0)
	await assert.rejects(auth.signIn('member@stu.mmu.ac.uk', '127.0.0.1'), /permanently banned/)
	await auth.removePlayerBan(String(member.id))
	bans.set(member.id, admin.id, now - 1_000, now - 2_000)
	assert.equal((await auth.signIn('member@stu.mmu.ac.uk', '127.0.0.1')).timeoutEnded, true)

	const signup = await auth.createSignup('12345678@stu.mmu.ac.uk', '127.0.0.1')
	auth.verifyEmailCode(signup.flowId, deliveredCodes.get('12345678@stu.mmu.ac.uk'))
	await auth.setMinecraftUsername(signup.flowId, 'NewMember')
	await auth.setMinecraftUsername(signup.flowId, 'NewMember')
	assert.equal(pendingJoinUpdates, 1)
	assertAuthCode(pendingJoin.code)
	for (let attempt = 0; attempt < 5; attempt++) {
		await assert.rejects(auth.verifyMinecraftCode(signup.flowId, 'Not an item'), /Invalid Minecraft code/)
	}
	await assert.rejects(auth.verifyMinecraftCode(signup.flowId, pendingJoin.code), /Minecraft code expired/)

	database.onModuleDestroy()
}

checkFlows().catch((error) => {
	console.error(error)
	process.exitCode = 1
})
