const assert = require('node:assert/strict')
process.env.SIGNUP_ALLOWLIST_PATH ??= require('node:path').join(__dirname, 'signup-allowlist.txt')
const { AUTH_CODE_ITEMS, createAuthCode, isAllowedEmail, isAuthRequestActive } = require('../dist/auth/auth.util')
const { AuthService } = require('../dist/auth/auth.service')
const { PlayerBansService } = require('../dist/auth/player-bans.service')
const { PlayersService } = require('../dist/players/players.service')
const { DatabaseService, sessions, users } = require('../dist/database/database.service')

const request = { active_code: '123456', completed_at_unix_ms: null, expires_at_unix_ms: 2000 }
assert.equal(isAuthRequestActive(request, 1000), true)
assert.equal(isAuthRequestActive(request, 2000), false)
assert.equal(isAuthRequestActive({ ...request, active_code: null }, 1000), false)
assert.equal(isAuthRequestActive({ ...request, completed_at_unix_ms: 1500 }, 1000), false)

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
	delete process.env.RESEND_API_KEY
	const database = new DatabaseService()
	const minecraft = {
		purchaseExternalPlayerInvite: async () => ({ purchased: true, balance_dabloons: 900 }),
	}
	const bans = new PlayerBansService(database)
	const auth = new AuthService(database, minecraft, bans)
	const now = Date.now()

	const member = database.connection.insert(users).values({
		email: 'member@stu.mmu.ac.uk',
		minecraft_uuid: '0123456789abcdef0123456789abcdef',
		minecraft_username: 'Member',
		whitelisted_at_unix_ms: now,
		rules_accepted_at_unix_ms: now,
		created_at_unix_ms: now,
	}).returning({ id: users.id }).get()

	await assert.rejects(auth.createSignup('guest@example.com'), /numeric @stu\.mmu\.ac\.uk address/)
	await assert.rejects(auth.addEmailToWhitelist({ id: member.id }, 'guest@.example.com', member.id), /valid email address/)
	await assert.rejects(auth.addEmailToWhitelist({ id: member.id }, 'guest@example.com'), /responsible user/)
	await auth.addEmailToWhitelist({ id: member.id }, 'Guest@Example.com', member.id)
	assert.deepEqual(auth.listEmailWhitelist().entries.map((entry) => entry.email), ['guest@example.com'])
	assert.equal(auth.listEmailWhitelist().entries[0].addedByMinecraftUsername, 'Member')
	assert.equal(auth.listEmailWhitelist().entries[0].responsibleMinecraftUsername, 'Member')
	const guestSignup = await auth.createSignup('guest@example.com')
	assert.equal(guestSignup.delivery, 'manual')
	auth.removeEmailFromWhitelist('guest@example.com')

	const signin = await auth.signIn('member@stu.mmu.ac.uk')
	assert.equal(signin.delivery, 'manual')
	let signinRequest = auth.listAuthRequests().requests[0]
	assert.equal(signinRequest.status, 'active')
	assertAuthCode(signinRequest.code)
	assert.ok((await auth.verifySignIn(signin.flowId, signinRequest.code)).token)
	signinRequest = auth.listAuthRequests().requests[0]
	assert.equal(signinRequest.status, 'verified')
	assert.equal(signinRequest.code, null)

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
	await assert.rejects(auth.signIn('member@stu.mmu.ac.uk'), /permanently banned/)
	await auth.removePlayerBan(String(member.id))
	bans.set(member.id, admin.id, now - 1_000, now - 2_000)
	assert.equal((await auth.signIn('member@stu.mmu.ac.uk')).timeoutEnded, true)

	const signup = await auth.createSignup('12345678@stu.mmu.ac.uk')
	assert.equal(signup.delivery, 'manual')
	let signupRequest = auth.listAuthRequests().requests[0]
	assert.equal(signupRequest.kind, 'signup')
	assertAuthCode(signupRequest.code)
	auth.verifyEmailCode(signup.flowId, signupRequest.code)
	signupRequest = auth.listAuthRequests().requests[0]
	assert.equal(signupRequest.status, 'verified')
	assert.equal(signupRequest.code, null)
	await auth.setMinecraftUsername(signup.flowId, 'NewMember')
	await auth.setMinecraftUsername(signup.flowId, 'NewMember')
	const signupFlow = require('../dist/auth/signup-flow').signupFlows.get(signup.flowId)
	assertAuthCode(signupFlow.minecraftCode)
	for (let attempt = 0; attempt < 5; attempt++) {
		await assert.rejects(auth.verifyMinecraftCode(signup.flowId, 'Not an item'), /Invalid Minecraft code/)
	}
	await assert.rejects(auth.verifyMinecraftCode(signup.flowId, signupFlow.minecraftCode), /Minecraft code expired/)

	database.onModuleDestroy()
}

checkFlows().catch((error) => {
	console.error(error)
	process.exitCode = 1
})
