const assert = require('node:assert/strict')
const { PlayerBansService } = require('../dist/auth/player-bans.service')
const { DatabaseService, users } = require('../dist/database/database.service')
const { MinecraftIdentityService } = require('../dist/database/minecraft-identity.service')
const { signupFlows } = require('../dist/auth/signup-flow')
const { VelocityService, parseBackendAddress, scheduleOverlaps } = require('../dist/velocity/velocity.service')

process.env.DATABASE_URL = ':memory:'
process.env.VELOCITY_API_SECRET = 'test-velocity-secret'

assert.equal(parseBackendAddress(' Event-Server:25565 '), 'event-server:25565')
assert.throws(() => parseBackendAddress('https://event-server:25565'), /Docker host and port/)
assert.equal(scheduleOverlaps(
	{ startsAtUnixMs: 100, endsAtUnixMs: 200 },
	{ startsAtUnixMs: 199, endsAtUnixMs: 300 },
), true)
assert.equal(scheduleOverlaps(
	{ startsAtUnixMs: 100, endsAtUnixMs: 200 },
	{ startsAtUnixMs: 200, endsAtUnixMs: 300 },
), false)

const database = new DatabaseService()
const identities = new MinecraftIdentityService(database)
const bans = new PlayerBansService(database)
const velocity = new VelocityService(database, identities, bans)
const now = Date.now()
const admin = database.connection.insert(users).values({
	email: 'admin@mmu.ac.uk',
	minecraft_uuid: '0123456789abcdef0123456789abcdef',
	minecraft_username: 'Admin',
	is_committee: 1,
	whitelisted_at_unix_ms: now,
	rules_accepted_at_unix_ms: now,
	created_at_unix_ms: now,
}).returning().get()

assert.equal(velocity.authorizePlayer(admin.minecraft_uuid, admin.minecraft_username).status, 'ALLOWED')
assert.equal(velocity.authorizePlayer('11111111111111111111111111111111', 'NewPlayer').status, 'SIGNUP_REQUIRED')
signupFlows.set('velocity-test', {
	email: 'new.player@stu.mmu.ac.uk',
	step: 'minecraft-code',
	emailCodeHash: 'unused',
	emailCodeExpiresAt: now + 60_000,
	minecraftUsername: 'NewPlayer',
	minecraftCode: 'Apple|Axe|Coal',
	minecraftCodeHash: 'unused',
	minecraftCodeExpiresAt: now + 60_000,
	updatedAt: now,
})
const signupDecision = velocity.authorizePlayer('11111111-1111-1111-1111-111111111111', 'NewPlayer')
assert.equal(signupDecision.status, 'SIGNUP_CODE')
assert.equal(signupDecision.code, 'Apple|Axe|Coal')
assert.equal(signupFlows.get('velocity-test').minecraftUuid, '11111111111111111111111111111111')
signupFlows.clear()

bans.set(admin.id, admin.id, null)
assert.equal(velocity.authorizePlayer(admin.minecraft_uuid, admin.minecraft_username).status, 'BANNED')
bans.remove(admin.id)

velocity.setMaintenanceMode({ id: admin.id }, true)
assert.equal(velocity.authorizePlayer(admin.minecraft_uuid, admin.minecraft_username).status, 'MAINTENANCE')
velocity.setMaintenanceMode({ id: admin.id }, false)

const eventServer = velocity.createServer({ id: admin.id }, 'event', 'event-server:25565').server
let control = velocity.synchronize({
	servers: [
		{ name: 'main', online: true, latencyMs: 5 },
		{ name: 'event', online: true, latencyMs: 7 },
	],
	players: [{ uuid: admin.minecraft_uuid, username: admin.minecraft_username, serverName: 'main' }],
})
assert.equal(control.route.targetServerName, 'main')
assert.equal(velocity.adminSnapshot().proxyOnline, true)
const schedule = velocity.createSchedule(admin, {
	name: 'Test event',
	serverId: eventServer.id,
	startsAtUnixMs: now - 1_000,
	endsAtUnixMs: now + 60_000,
})
control = velocity.synchronize({ servers: [], players: [] })
assert.equal(control.route.targetServerName, 'event')
assert.equal(control.route.activeScheduleId, schedule.scheduleId)
velocity.removeSchedule(String(schedule.scheduleId))

velocity.synchronize({
	servers: [
		{ name: 'main', online: true, latencyMs: 5 },
		{ name: 'event', online: true, latencyMs: 7 },
	],
	players: [{ uuid: admin.minecraft_uuid, username: admin.minecraft_username, serverName: 'main' }],
})
const move = velocity.movePlayer(admin.minecraft_uuid, eventServer.id)
control = velocity.synchronize({ servers: [], players: [] })
assert.deepEqual(control.commands.map((command) => command.id), [move.commandId])
velocity.synchronize({ acknowledgedCommandIds: [move.commandId] })
assert.equal(velocity.synchronize({}).commands.length, 0)

database.onModuleDestroy()
