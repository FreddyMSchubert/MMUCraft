import { BadRequestException, ConflictException, Injectable, NotFoundException, ServiceUnavailableException, UnauthorizedException } from '@nestjs/common'
import { createHash, timingSafeEqual } from 'node:crypto'
import { and, asc, eq, gt, lt, lte } from 'drizzle-orm'
import { AuthenticatedUser } from '../auth/auth.service'
import { isValidMinecraftUsername } from '../auth/auth.util'
import { PlayerBansService } from '../auth/player-bans.service'
import { signupFlows } from '../auth/signup-flow'
import {
	DatabaseService,
	velocitySchedules,
	velocityServers,
	velocitySettings,
} from '../database/database.service'
import { MinecraftIdentityService, normalizeMinecraftUuid } from '../database/minecraft-identity.service'

const PROXY_STALE_AFTER_MS = 10_000
const COMMAND_TTL_MS = 60_000
const SERVER_NAME_PATTERN = /^[a-z0-9][a-z0-9_-]{0,31}$/
const BACKEND_ADDRESS_PATTERN = /^([a-z0-9](?:[a-z0-9._-]{0,251}[a-z0-9])?):([1-9][0-9]{0,4})$/i

interface LiveServer {
	name: string
	online: boolean
	latencyMs: number | null
	error: string | null
}

interface LivePlayer {
	uuid: string
	username: string
	serverName: string
}

interface MoveCommand {
	id: number
	playerUuid: string
	targetServerName: string
	createdAtUnixMs: number
}

interface SyncBody {
	servers?: unknown
	players?: unknown
	acknowledgedCommandIds?: unknown
}

@Injectable()
export class VelocityService {
	private liveServers = new Map<string, LiveServer>()
	private livePlayers: LivePlayer[] = []
	private lastHeartbeatUnixMs: number | null = null
	private readonly commands = new Map<number, MoveCommand>()
	private nextCommandId = 1

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly bans: PlayerBansService,
	) { }

	verifyInternalAuthorization(authorization: string | undefined) {
		const expected = process.env.VELOCITY_API_SECRET ?? ''
		const supplied = authorization?.startsWith('Bearer ') ? authorization.slice(7) : ''
		if (!expected || !constantTimeEquals(supplied, expected)) {
			throw new UnauthorizedException('Invalid Velocity API credentials')
		}
	}

	authorizePlayer(uuidInput: unknown, usernameInput: unknown) {
		const uuid = normalizeMinecraftUuid(typeof uuidInput === 'string' ? uuidInput : '')
		const username = typeof usernameInput === 'string' ? usernameInput.trim() : ''
		if (!uuid || !isValidMinecraftUsername(username)) {
			return { status: 'DENIED', websiteUrl: this.websiteUrl() }
		}

		if (this.settings().maintenance_mode === 1) {
			return { status: 'MAINTENANCE', websiteUrl: this.websiteUrl() }
		}

		const user = this.identities.resolveAndRefresh(uuid, username)
		if (user) {
			const ban = this.bans.resolve(user.id)
			if (ban.active) {
				return {
					status: ban.expiresAtUnixMs === null ? 'BANNED' : 'TIMEOUT',
					expiresAtUnixMs: ban.expiresAtUnixMs,
					websiteUrl: this.websiteUrl(),
				}
			}

			return { status: 'ALLOWED', websiteUrl: this.websiteUrl() }
		}

		const now = Date.now()
		const flow = [...signupFlows.values()].find((candidate) => (
			candidate.step === 'minecraft-code'
			&& candidate.minecraftUsername?.localeCompare(username, 'en', { sensitivity: 'base' }) === 0
			&& (candidate.minecraftCodeExpiresAt ?? 0) > now
			&& candidate.minecraftCode
		))
		if (!flow) return { status: 'SIGNUP_REQUIRED', websiteUrl: this.websiteUrl() }

		const uuidInAnotherFlow = [...signupFlows.values()]
			.some((candidate) => candidate !== flow && candidate.minecraftUuid === uuid)
		if (uuidInAnotherFlow) return { status: 'SIGNUP_REQUIRED', websiteUrl: this.websiteUrl() }

		flow.minecraftUuid = uuid
		flow.minecraftUsername = username
		flow.updatedAt = now
		return {
			status: 'SIGNUP_CODE',
			code: flow.minecraftCode,
			expiresAtUnixMs: flow.minecraftCodeExpiresAt,
			websiteUrl: this.websiteUrl(),
		}
	}

	synchronize(body: SyncBody | undefined) {
		const now = Date.now()
		for (const id of parseAcknowledgedCommandIds(body?.acknowledgedCommandIds)) this.commands.delete(id)
		for (const [id, command] of this.commands) {
			if (command.createdAtUnixMs + COMMAND_TTL_MS <= now) this.commands.delete(id)
		}

		this.liveServers = new Map(parseLiveServers(body?.servers).map((server) => [server.name, server]))
		this.livePlayers = parseLivePlayers(body?.players)
		this.lastHeartbeatUnixMs = now

		const settings = this.settings()
		const servers = this.database.connection.select().from(velocityServers).orderBy(asc(velocityServers.id)).all()
		const activeSchedule = this.activeSchedule(now)
		const defaultServer = servers.find((server) => server.is_default === 1) ?? null
		const targetServer = activeSchedule
			? servers.find((server) => server.id === activeSchedule.server_id) ?? null
			: defaultServer

		const disconnects = this.livePlayers.flatMap((player) => {
			const user = this.identities.findByUuid(player.uuid)
			if (!user) return []
			const ban = this.bans.resolve(user.id, now)
			if (!ban.active) return []
			return [{
				playerUuid: player.uuid,
				status: ban.expiresAtUnixMs === null ? 'BANNED' : 'TIMEOUT',
				expiresAtUnixMs: ban.expiresAtUnixMs,
			}]
		})

		return {
			maintenanceMode: settings.maintenance_mode === 1,
			servers: servers.map((server) => ({
				id: server.id,
				name: server.name,
				address: server.address,
				isDefault: server.is_default === 1,
			})),
			route: targetServer ? {
				revision: activeSchedule ? `schedule:${activeSchedule.id}` : `default:${targetServer.id}`,
				targetServerName: targetServer.name,
				activeScheduleId: activeSchedule?.id ?? null,
			} : null,
			commands: [...this.commands.values()],
			disconnects,
		}
	}

	adminSnapshot() {
		const now = Date.now()
		const proxyOnline = this.proxyIsOnline(now)
		const servers = this.database.connection.select().from(velocityServers).orderBy(asc(velocityServers.id)).all()
		const schedules = this.database.connection.select().from(velocitySchedules)
			.orderBy(asc(velocitySchedules.starts_at_unix_ms)).all()
		const serversById = new Map(servers.map((server) => [server.id, server]))
		const active = this.activeSchedule(now)
		const players = proxyOnline ? this.livePlayers : []

		return {
			nowUnixMs: now,
			proxyOnline,
			lastHeartbeatUnixMs: this.lastHeartbeatUnixMs,
			maintenanceMode: this.settings().maintenance_mode === 1,
			activeScheduleId: active?.id ?? null,
			servers: servers.map((server) => {
				const live = proxyOnline ? this.liveServers.get(server.name) : undefined
				return {
					id: server.id,
					name: server.name,
					address: server.address,
					isDefault: server.is_default === 1,
					health: live ? (live.online ? 'online' : 'offline') : 'unknown',
					latencyMs: live?.latencyMs ?? null,
					error: live?.error ?? null,
					playerCount: players.filter((player) => player.serverName === server.name).length,
				}
			}),
			players,
			schedules: schedules.map((schedule) => ({
				id: schedule.id,
				name: schedule.name,
				serverId: schedule.server_id,
				serverName: serversById.get(schedule.server_id)?.name ?? 'Deleted server',
				startsAtUnixMs: schedule.starts_at_unix_ms,
				endsAtUnixMs: schedule.ends_at_unix_ms,
			})),
		}
	}

	createServer(admin: AuthenticatedUser, nameInput: unknown, addressInput: unknown) {
		const name = typeof nameInput === 'string' ? nameInput.trim().toLowerCase() : ''
		if (!SERVER_NAME_PATTERN.test(name)) {
			throw new BadRequestException('Server name must use 1-32 lowercase letters, numbers, underscores, or hyphens')
		}
		const address = parseBackendAddress(addressInput)
		const now = Date.now()

		try {
			const server = this.database.connection.insert(velocityServers).values({
				name,
				address,
				is_default: 0,
				created_by_user_id: admin.id,
				created_at_unix_ms: now,
				updated_at_unix_ms: now,
			}).returning().get()
			return { ok: true, server: this.publicServer(server) }
		} catch (error) {
			if (String(error).includes('UNIQUE constraint failed')) {
				throw new ConflictException('A server already uses this name or address')
			}
			throw error
		}
	}

	setDefaultServer(idInput: string) {
		const id = parseId(idInput, 'Server not found')
		const server = this.database.connection.select().from(velocityServers).where(eq(velocityServers.id, id)).get()
		if (!server) throw new NotFoundException('Server not found')

		this.database.connection.transaction((tx) => {
			tx.update(velocityServers).set({ is_default: 0 }).run()
			tx.update(velocityServers).set({ is_default: 1, updated_at_unix_ms: Date.now() })
				.where(eq(velocityServers.id, id)).run()
		})
		return { ok: true, serverId: id }
	}

	removeServer(idInput: string) {
		const id = parseId(idInput, 'Server not found')
		const server = this.database.connection.select().from(velocityServers).where(eq(velocityServers.id, id)).get()
		if (!server) throw new NotFoundException('Server not found')
		if (server.is_default === 1) throw new ConflictException('Choose another default server before removing this one')
		if (this.database.connection.select({ id: velocitySchedules.id }).from(velocitySchedules)
			.where(eq(velocitySchedules.server_id, id)).get()) {
			throw new ConflictException('Remove this server\'s schedules before removing the server')
		}
		if (this.proxyIsOnline() && this.livePlayers.some((player) => player.serverName === server.name)) {
			throw new ConflictException('Move all players off this server before removing it')
		}

		this.database.connection.delete(velocityServers).where(eq(velocityServers.id, id)).run()
		return { ok: true, serverId: id }
	}

	createSchedule(admin: AuthenticatedUser, body: {
		name?: unknown
		serverId?: unknown
		startsAtUnixMs?: unknown
		endsAtUnixMs?: unknown
	} | undefined) {
		const name = typeof body?.name === 'string' ? body.name.trim() : ''
		if (!name || name.length > 80) throw new BadRequestException('Schedule name must use 1-80 characters')
		const serverId = parseSafeInteger(body?.serverId, 'Select a server')
		const startsAtUnixMs = parseSafeInteger(body?.startsAtUnixMs, 'Select a valid start time')
		const endsAtUnixMs = parseSafeInteger(body?.endsAtUnixMs, 'Select a valid end time')
		if (endsAtUnixMs <= startsAtUnixMs) throw new BadRequestException('Schedule end must be after its start')
		if (endsAtUnixMs <= Date.now()) throw new BadRequestException('Schedule end must be in the future')
		if (!this.database.connection.select({ id: velocityServers.id }).from(velocityServers)
			.where(eq(velocityServers.id, serverId)).get()) throw new NotFoundException('Server not found')

		const overlap = this.database.connection.select({ id: velocitySchedules.id }).from(velocitySchedules)
			.where(and(
				lt(velocitySchedules.starts_at_unix_ms, endsAtUnixMs),
				gt(velocitySchedules.ends_at_unix_ms, startsAtUnixMs),
			)).get()
		if (overlap) throw new ConflictException('This schedule overlaps another routing schedule')

		const schedule = this.database.connection.insert(velocitySchedules).values({
			name,
			server_id: serverId,
			starts_at_unix_ms: startsAtUnixMs,
			ends_at_unix_ms: endsAtUnixMs,
			created_by_user_id: admin.id,
			created_at_unix_ms: Date.now(),
		}).returning().get()
		return { ok: true, scheduleId: schedule.id }
	}

	removeSchedule(idInput: string) {
		const id = parseId(idInput, 'Schedule not found')
		const removed = this.database.connection.delete(velocitySchedules)
			.where(eq(velocitySchedules.id, id)).run().changes
		if (removed !== 1) throw new NotFoundException('Schedule not found')
		return { ok: true, scheduleId: id }
	}

	setMaintenanceMode(admin: AuthenticatedUser, enabledInput: unknown) {
		if (typeof enabledInput !== 'boolean') throw new BadRequestException('enabled must be a boolean')
		this.database.connection.update(velocitySettings).set({
			maintenance_mode: enabledInput ? 1 : 0,
			updated_by_user_id: admin.id,
			updated_at_unix_ms: Date.now(),
		}).where(eq(velocitySettings.id, 1)).run()
		return { ok: true, maintenanceMode: enabledInput }
	}

	movePlayer(uuidInput: string, serverIdInput: unknown) {
		const uuid = normalizeMinecraftUuid(uuidInput)
		if (!uuid) throw new NotFoundException('Player not found')
		const serverId = parseSafeInteger(serverIdInput, 'Select a server')
		const server = this.database.connection.select().from(velocityServers)
			.where(eq(velocityServers.id, serverId)).get()
		if (!server) throw new NotFoundException('Server not found')
		if (!this.proxyIsOnline()) throw new ServiceUnavailableException('Velocity is not reporting live state')

		const player = this.livePlayers.find((candidate) => candidate.uuid === uuid)
		if (!player) throw new NotFoundException('Player is no longer online')
		if (player.serverName === server.name) throw new ConflictException('Player is already on this server')
		if (!this.liveServers.get(server.name)?.online) {
			throw new ConflictException('Target server is not healthy')
		}

		const command: MoveCommand = {
			id: this.nextCommandId++,
			playerUuid: uuid,
			targetServerName: server.name,
			createdAtUnixMs: Date.now(),
		}
		this.commands.set(command.id, command)
		return { ok: true, commandId: command.id }
	}

	private settings() {
		const settings = this.database.connection.select().from(velocitySettings)
			.where(eq(velocitySettings.id, 1)).get()
		if (!settings) throw new Error('Velocity settings row is missing')
		return settings
	}

	private activeSchedule(now: number) {
		return this.database.connection.select().from(velocitySchedules).where(and(
			lte(velocitySchedules.starts_at_unix_ms, now),
			gt(velocitySchedules.ends_at_unix_ms, now),
		)).get() ?? null
	}

	private proxyIsOnline(now = Date.now()) {
		return this.lastHeartbeatUnixMs !== null && this.lastHeartbeatUnixMs + PROXY_STALE_AFTER_MS > now
	}

	private websiteUrl() {
		return (process.env.PUBLIC_URL ?? 'https://mmuminecraftsociety.co.uk').replace(/\/$/, '')
	}

	private publicServer(server: typeof velocityServers.$inferSelect) {
		return {
			id: server.id,
			name: server.name,
			address: server.address,
			isDefault: server.is_default === 1,
		}
	}
}

export function parseBackendAddress(value: unknown) {
	const address = typeof value === 'string' ? value.trim().toLowerCase() : ''
	const match = BACKEND_ADDRESS_PATTERN.exec(address)
	if (!match || Number(match[2]) > 65_535) {
		throw new BadRequestException('Address must use the Docker host and port format, for example event-server:25565')
	}
	return address
}

export function scheduleOverlaps(
	left: { startsAtUnixMs: number; endsAtUnixMs: number },
	right: { startsAtUnixMs: number; endsAtUnixMs: number },
) {
	return left.startsAtUnixMs < right.endsAtUnixMs && left.endsAtUnixMs > right.startsAtUnixMs
}

function parseLiveServers(value: unknown): LiveServer[] {
	if (!Array.isArray(value)) return []
	return value.slice(0, 200).flatMap((entry) => {
		if (!entry || typeof entry !== 'object') return []
		const input = entry as Record<string, unknown>
		const name = typeof input.name === 'string' ? input.name.trim().toLowerCase() : ''
		if (!SERVER_NAME_PATTERN.test(name) || typeof input.online !== 'boolean') return []
		return [{
			name,
			online: input.online,
			latencyMs: typeof input.latencyMs === 'number' && Number.isSafeInteger(input.latencyMs) && input.latencyMs >= 0
				? input.latencyMs : null,
			error: typeof input.error === 'string' ? input.error.slice(0, 200) : null,
		}]
	})
}

function parseLivePlayers(value: unknown): LivePlayer[] {
	if (!Array.isArray(value)) return []
	const seen = new Set<string>()
	return value.slice(0, 1_000).flatMap((entry) => {
		if (!entry || typeof entry !== 'object') return []
		const input = entry as Record<string, unknown>
		const uuid = normalizeMinecraftUuid(typeof input.uuid === 'string' ? input.uuid : '')
		const username = typeof input.username === 'string' ? input.username.trim() : ''
		const serverName = typeof input.serverName === 'string' ? input.serverName.trim().toLowerCase() : ''
		if (!uuid || seen.has(uuid) || !isValidMinecraftUsername(username) || !SERVER_NAME_PATTERN.test(serverName)) return []
		seen.add(uuid)
		return [{ uuid, username, serverName }]
	})
}

function parseAcknowledgedCommandIds(value: unknown) {
	if (!Array.isArray(value)) return []
	return value.slice(0, 1_000).filter((id): id is number => Number.isSafeInteger(id) && Number(id) > 0)
}

function parseId(value: string, message: string) {
	const id = Number(value)
	if (!Number.isSafeInteger(id) || id <= 0) throw new NotFoundException(message)
	return id
}

function parseSafeInteger(value: unknown, message: string) {
	if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) {
		throw new BadRequestException(message)
	}
	return value
}

function constantTimeEquals(left: string, right: string) {
	const leftHash = createHash('sha256').update(left).digest()
	const rightHash = createHash('sha256').update(right).digest()
	return timingSafeEqual(leftHash, rightHash)
}
