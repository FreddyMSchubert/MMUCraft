import { once } from 'node:events'
import { existsSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { spawn } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'
import { createHash } from 'node:crypto'
import * as grpc from '@grpc/grpc-js'
import * as protoLoader from '@grpc/proto-loader'
import Database from 'better-sqlite3'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import { playerStats, shopUnlocks } from '../dist/database/schema.js'

const HTTP_PORT = 18080
const API_GRPC_PORT = 15051
const MOD_GRPC_PORT = 15052
const BASE_URL = `http://127.0.0.1:${HTTP_PORT}`
const databasePath = join(process.cwd(), 'data', 'http-smoke.sqlite')
const databasePrefixes = [databasePath, `${databasePath}-wal`, `${databasePath}-shm`]
const results = []
const pendingMinecraftCodes = new Map()
let apiOutput = ''
let apiProcess
let modServer
let apiAuthClient

try {
	removeTestDatabase()
	modServer = await startFakeModServer()
	apiProcess = startApi()
	await waitForApi()
	apiAuthClient = createApiAuthClient()

	await request('health', 'GET', '/api/health', { expected: 200 })

	const player = await completeSignup('drizzle-smoke@stu.mmu.ac.uk', 'DrizzleSmoke')
	const admin = await completeSignup('drizzle-admin@mmu.ac.uk', 'MerlinSpace')

	const signIn = await request('auth signin', 'POST', '/api/auth/signin', {
		body: { email: player.email },
		expected: 201,
	})
	const playerCookie = readCookie(signIn.response)

	const me = await request('auth me', 'GET', '/api/auth/me', {
		cookie: playerCookie,
		expected: 200,
	})
	const playerId = me.data.user.id
	const adminMe = await request('auth admin me', 'GET', '/api/auth/me', {
		cookie: admin.cookie,
		expected: 200,
	})
	seedRuntimeData([
		{ id: playerId, name: 'DrizzleSmoke' },
		{ id: adminMe.data.user.id, name: 'MerlinSpace' },
	])

	await request('players list', 'GET', '/api/players', { cookie: playerCookie, expected: 200 })
	await request('players update profile', 'PATCH', '/api/players/me/profile', {
		cookie: playerCookie,
		body: {
			preferredName: 'Drizzle',
			pronouns: 'they/them',
			courseYear: 'Testing / 1',
			discordUsername: 'drizzle-smoke',
			baseX: 10,
			baseY: 64,
			baseZ: -10,
			bio: 'HTTP smoke test',
		},
		expected: 200,
	})
	await request('players get one', 'GET', `/api/players/${playerId}`, { cookie: playerCookie, expected: 200 })
	await request('knowledge', 'GET', '/api/knowledge', { cookie: playerCookie, expected: 200 })

	const shop = await request('shop list', 'GET', '/api/shop', { cookie: playerCookie, expected: 200 })
	const customItem = shop.data.items.find((item) => item.id === 'cosmetic-woodpecker')
	if (!customItem?.textureUrl?.startsWith('/api/shop/texture/') || !customItem?.modelUrl?.startsWith('/api/shop/model/')) {
		throw new Error('Expected unlocked cosmetic-woodpecker to expose both API asset endpoints')
	}
	await request('shop purchase', 'POST', '/api/shop/purchase', {
		cookie: playerCookie,
		body: { itemId: customItem.id },
		expected: 201,
	})
	await request('shop texture', 'GET', customItem.textureUrl, { expected: 200, binary: true })
	await request('shop model', 'GET', customItem.modelUrl, { expected: 200, binary: true })

	await request('dailies list', 'GET', '/api/dailies', { cookie: playerCookie, expected: 200 })
	await request('dailies login claim', 'POST', '/api/dailies/login-bonus/claim', {
		cookie: playerCookie,
		expected: 201,
	})
	await request('dailies item claim', 'POST', '/api/dailies/item-submission/claim', {
		cookie: playerCookie,
		expected: 201,
	})
	await request('dailies advancement claim', 'POST', '/api/dailies/advancement-bonus/claim', {
		cookie: playerCookie,
		expected: 201,
	})
	await request('dailies completion claim', 'POST', '/api/dailies/completion/claim', {
		cookie: playerCookie,
		expected: 201,
	})

	await request('admin players', 'GET', '/api/admin/players', { cookie: admin.cookie, expected: 200 })
	await request('admin membership', 'PATCH', `/api/admin/players/${playerId}/membership`, {
		cookie: admin.cookie,
		body: { isMember: true },
		expected: 200,
	})
	await request('admin committee', 'PATCH', `/api/admin/players/${playerId}/committee`, {
		cookie: admin.cookie,
		body: { isCommittee: true },
		expected: 200,
	})
	await request('admin create gift code', 'POST', '/api/admin/gift-codes', {
		cookie: admin.cookie,
		body: { code: 'drizzle-smoke', amountDabloons: 25, redemptionMode: 'per_user' },
		expected: 201,
	})
	await request('admin gift codes', 'GET', '/api/admin/gift-codes', { cookie: admin.cookie, expected: 200 })
	await request('gift code redeem', 'POST', '/api/gift-codes/redeem', {
		cookie: playerCookie,
		body: { code: 'drizzle-smoke' },
		expected: 201,
	})

	await reportLoginAttempt('DrizzleRenamed', player.minecraftUuid, true)
	const renamedMe = await request('auth renamed me', 'GET', '/api/auth/me', {
		cookie: playerCookie,
		expected: 200,
	})
	if (renamedMe.data.user.minecraftUsername !== 'DrizzleRenamed') {
		throw new Error('Expected a server login with the same UUID to refresh the stored Minecraft username')
	}

	await request('auth signout', 'POST', '/api/auth/signout', { cookie: playerCookie, expected: 204 })

	for (const result of results) {
		console.log(`${String(result.status).padStart(3)} ${result.method.padEnd(5)} ${result.path} (${result.name})`)
	}
	console.log(`HTTP smoke passed: ${results.length} requests covering all 28 controller routes`)
} catch (error) {
	console.error(error)
	if (apiOutput) {
		console.error('\nAPI process output:\n' + apiOutput)
	}
	process.exitCode = 1
} finally {
	await stopApi()
	modServer?.forceShutdown()
	apiAuthClient?.close()
	removeTestDatabase()
}

async function completeSignup(email, minecraftUsername) {
	const signup = await request('auth signup', 'POST', '/api/auth/signup', {
		body: { email },
		expected: 201,
	})
	const flowId = signup.data.flowId
	await request('auth verify email', 'POST', '/api/auth/verify-email', {
		body: { flowId, code: signup.data.devEmailCode },
		expected: 201,
	})
	await request('auth set minecraft username', 'POST', '/api/auth/minecraft-username', {
		body: { flowId, minecraftUsername },
		expected: 201,
	})
	const minecraftCode = pendingMinecraftCodes.get(minecraftUsername)
	if (!minecraftCode) throw new Error(`Fake mod received no pending code for ${minecraftUsername}`)
	const minecraftUuid = createHash('sha256').update(minecraftUsername).digest('hex').slice(0, 32)
	await reportLoginAttempt(minecraftUsername, minecraftUuid, false)
	await request('auth verify minecraft', 'POST', '/api/auth/verify-minecraft', {
		body: { flowId, code: minecraftCode },
		expected: 201,
	})
	const accepted = await request('auth accept rules', 'POST', '/api/auth/accept-rules', {
		body: { flowId },
		expected: 201,
	})
	return { email, cookie: readCookie(accepted.response), minecraftUuid }
}

function createApiAuthClient() {
	const definition = protoLoader.loadSync(join(process.cwd(), '..', '..', 'proto', 'auth.proto'), loaderOptions())
	const auth = grpc.loadPackageDefinition(definition)
	return new auth.mcstack.auth.v1.AuthEvents(
		`127.0.0.1:${API_GRPC_PORT}`,
		grpc.credentials.createInsecure(),
	)
}

async function reportLoginAttempt(minecraftUsername, uuid, whitelisted) {
	await new Promise((resolve, reject) => {
		apiAuthClient.ReportLoginAttempt({
			minecraft_username: minecraftUsername,
			uuid,
			whitelisted,
			unix_ms: Date.now(),
		}, (error, response) => {
			if (error) reject(error)
			else if (!response?.received) reject(new Error('API rejected fake Minecraft login attempt'))
			else resolve()
		})
	})
}

async function request(name, method, path, options = {}) {
	const headers = {}
	if (options.cookie) headers.Cookie = options.cookie
	if (options.body !== undefined) headers['Content-Type'] = 'application/json'

	const response = await fetch(`${BASE_URL}${path}`, {
		method,
		headers,
		body: options.body === undefined ? undefined : JSON.stringify(options.body),
		signal: AbortSignal.timeout(15_000),
	})

	let data = null
	if (response.status !== 204) {
		if (options.binary) {
			data = new Uint8Array(await response.arrayBuffer())
		} else {
			const text = await response.text()
			try {
				data = text ? JSON.parse(text) : null
			} catch {
				data = text
			}
		}
	}

	if (response.status !== options.expected) {
		throw new Error(`${name}: expected HTTP ${options.expected}, received ${response.status}: ${JSON.stringify(data)}`)
	}
	if (options.binary && data.length === 0) {
		throw new Error(`${name}: endpoint returned an empty response body`)
	}

	results.push({ name, method, path, status: response.status })
	return { response, data }
}

function readCookie(response) {
	const setCookie = response.headers.get('set-cookie')
	if (!setCookie) throw new Error('Expected response to set a session cookie')
	return setCookie.split(';', 1)[0]
}

function seedRuntimeData(players) {
	const client = new Database(databasePath)
	try {
		const database = drizzle(client)
		const now = Date.now()
		for (const player of players) {
			const stats = {
				version: 1,
				money: { earnedDabloons: 0, balanceDabloons: 1000, lastUpdatedAtUnixMs: now, sources: {} },
				minecraft: { stats: {}, lastSyncedAtUnixMs: now, lastPlayedAtUnixMs: now },
				minecraftProfile: {
					uuid: String(player.id).padStart(32, '0'),
					name: player.name,
					skinUrl: null,
					model: null,
					fetchedAtUnixMs: now,
				},
			}
			database.insert(playerStats).values({
				user_id: player.id,
				stats_json: JSON.stringify(stats),
				updated_at_unix_ms: now,
			}).onConflictDoUpdate({
				target: playerStats.user_id,
				set: { stats_json: JSON.stringify(stats), updated_at_unix_ms: now },
			}).run()
		}
		database.insert(shopUnlocks).values({
			user_id: players[0].id,
			item_id: 'cosmetic-woodpecker',
			unlock_type: 'cosmetic',
			unlocked_at_unix_ms: now,
			source: 'http_smoke',
		}).run()
	} finally {
		client.close()
	}
}

function startApi() {
	const child = spawn(process.execPath, ['dist/main.js'], {
		cwd: process.cwd(),
		env: {
			...process.env,
			HOST: '127.0.0.1',
			PORT: String(HTTP_PORT),
			API_GRPC_HOST: '127.0.0.1',
			API_GRPC_PORT: String(API_GRPC_PORT),
			MOD_GRPC_TARGET: `127.0.0.1:${MOD_GRPC_PORT}`,
			DATABASE_URL: databasePath,
			AUTH_CODE_SECRET: 'http-smoke-secret',
			COOKIE_SECURE: 'false',
		},
		stdio: ['ignore', 'pipe', 'pipe'],
	})
	child.stdout.on('data', (chunk) => { apiOutput += chunk.toString() })
	child.stderr.on('data', (chunk) => { apiOutput += chunk.toString() })
	return child
}

async function waitForApi() {
	for (let attempt = 0; attempt < 80; attempt++) {
		if (apiProcess.exitCode !== null) {
			throw new Error(`API exited during startup with code ${apiProcess.exitCode}`)
		}
		try {
			const response = await fetch(`${BASE_URL}/api/health`)
			if (response.ok) return
		} catch {
			// The socket is not listening yet.
		}
		await delay(100)
	}
	throw new Error('API did not become healthy within 8 seconds')
}

async function stopApi() {
	if (!apiProcess || apiProcess.exitCode !== null) return
	apiProcess.kill('SIGTERM')
	await Promise.race([
		once(apiProcess, 'exit'),
		delay(3_000).then(() => apiProcess.exitCode === null && apiProcess.kill('SIGKILL')),
	])
}

async function startFakeModServer() {
	const authDefinition = protoLoader.loadSync(join(process.cwd(), '..', '..', 'proto', 'auth.proto'), loaderOptions())
	const gameplayDefinition = protoLoader.loadSync(join(process.cwd(), '..', '..', 'proto', 'gameplay.proto'), loaderOptions())
	const auth = grpc.loadPackageDefinition(authDefinition)
	const gameplay = grpc.loadPackageDefinition(gameplayDefinition)
	const server = new grpc.Server()

	server.addService(auth.mcstack.auth.v1.ModControl.service, {
		Ping: (_call, callback) => callback(null, { service: 'fake-mod', unix_ms: Date.now() }),
		UpsertPendingJoin: (call, callback) => {
			pendingMinecraftCodes.set(call.request.minecraft_username, call.request.code)
			callback(null, { accepted: true })
		},
		RemovePendingJoin: (call, callback) => {
			pendingMinecraftCodes.delete(call.request.minecraft_username)
			callback(null, {})
		},
		WhitelistPlayer: (_call, callback) => callback(null, { whitelisted: true }),
	})

	server.addService(gameplay.mcstack.gameplay.v1.GameplayControl.service, {
		GrantDailyLoginBonus: (_call, callback) => callback(null, { granted: true, online: true, message: 'Granted' }),
		GrantGiftCodeMoney: (_call, callback) => callback(null, { granted: true, online: true, balance_dabloons: 975, message: 'Granted' }),
		SubmitDailyItems: (call, callback) => callback(null, { submitted: true, online: true, found_count: call.request.count, message: 'Submitted' }),
		PickDailyAdvancement: (_call, callback) => callback(null, {
			selected: true,
			online: true,
			advancement_id: 'minecraft:story/mine_stone',
			title: 'Stone Age',
			tab_title: 'Minecraft',
			icon_item: 'minecraft:stone_pickaxe',
			base_reward_dabloons: 3,
			bonus_reward_dabloons: 5,
			message: 'Selected',
		}),
		ClaimDailyAdvancement: (_call, callback) => callback(null, { claimed: true, online: true, completed: true, message: 'Claimed' }),
		PurchaseShopItem: (_call, callback) => callback(null, { purchased: true, online: true, balance_dabloons: 900, message: 'Purchased' }),
	})

	await new Promise((resolve, reject) => {
		server.bindAsync(`127.0.0.1:${MOD_GRPC_PORT}`, grpc.ServerCredentials.createInsecure(), (error) => {
			if (error) reject(error)
			else resolve()
		})
	})
	return server
}

function loaderOptions() {
	return { keepCase: true, longs: Number, enums: String, defaults: true, oneofs: true }
}

function removeTestDatabase() {
	for (const path of databasePrefixes) {
		if (existsSync(path)) rmSync(path, { force: true })
	}
	const dataDirectory = join(process.cwd(), 'data')
	for (const suffix of ['pre-drizzle', 'failed-drizzle']) {
		for (const path of [databasePath + `.${suffix}`]) {
			if (existsSync(path)) rmSync(path, { force: true })
		}
	}
}
