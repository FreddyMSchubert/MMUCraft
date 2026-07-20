const assert = require('node:assert/strict')
const { isAuthRequestActive } = require('../dist/auth/auth.util')
const { AuthService } = require('../dist/auth/auth.service')
const { DatabaseService, users } = require('../dist/database/database.service')

const request = { active_code: '123456', completed_at_unix_ms: null, expires_at_unix_ms: 2000 }
assert.equal(isAuthRequestActive(request, 1000), true)
assert.equal(isAuthRequestActive(request, 2000), false)
assert.equal(isAuthRequestActive({ ...request, active_code: null }, 1000), false)
assert.equal(isAuthRequestActive({ ...request, completed_at_unix_ms: 1500 }, 1000), false)

async function checkFlows() {
	process.env.DATABASE_URL = ':memory:'
	delete process.env.RESEND_API_KEY
	const database = new DatabaseService()
	const grpc = { removePendingJoin: async () => undefined }
	const auth = new AuthService(database, grpc)
	const now = Date.now()

	const member = database.connection.insert(users).values({
		email: 'member@stu.mmu.ac.uk',
		minecraft_uuid: '0123456789abcdef0123456789abcdef',
		minecraft_username: 'Member',
		whitelisted_at_unix_ms: now,
		rules_accepted_at_unix_ms: now,
		created_at_unix_ms: now,
	}).returning({ id: users.id }).get()

	await assert.rejects(auth.createSignup('guest@example.com'), /Only MMU email addresses are allowed/)
	assert.throws(() => auth.addEmailToWhitelist({ id: member.id }, 'guest@.example.com', member.id), /valid email address/)
	assert.throws(() => auth.addEmailToWhitelist({ id: member.id }, 'guest@example.com'), /responsible user/)
	auth.addEmailToWhitelist({ id: member.id }, 'Guest@Example.com', member.id)
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
	assert.match(signinRequest.code, /^\d{6}$/)
	assert.ok(auth.verifySignIn(signin.flowId, signinRequest.code).token)
	signinRequest = auth.listAuthRequests().requests[0]
	assert.equal(signinRequest.status, 'verified')
	assert.equal(signinRequest.code, null)

	const signup = await auth.createSignup('newmember@stu.mmu.ac.uk')
	assert.equal(signup.delivery, 'manual')
	let signupRequest = auth.listAuthRequests().requests[0]
	assert.equal(signupRequest.kind, 'signup')
	assert.match(signupRequest.code, /^\d{6}$/)
	auth.verifyEmailCode(signup.flowId, signupRequest.code)
	signupRequest = auth.listAuthRequests().requests[0]
	assert.equal(signupRequest.status, 'verified')
	assert.equal(signupRequest.code, null)

	database.onModuleDestroy()
}

checkFlows().catch((error) => {
	console.error(error)
	process.exitCode = 1
})
