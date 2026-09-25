import {
	BadRequestException,
	ConflictException,
	ForbiddenException,
	Injectable,
	NotFoundException,
	OnModuleDestroy,
	OnModuleInit,
} from '@nestjs/common';
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { eq, inArray } from 'drizzle-orm';
import { isValidMinecraftUsername } from '../auth/auth.util';
import { PlayerBansService } from '../auth/player-bans.service';
import { signupFlows } from '../auth/signup-flow';
import {
	DatabaseService,
	playerProfiles,
	velocityServers,
	velocityPlayerServers,
	velocitySettings,
	users,
} from '../database/database.service';
import {
	MinecraftIdentityService,
	normalizeMinecraftUuid,
} from '../database/minecraft-identity.service';
import { requireInternalAuthorization } from '../internal-authorization';
import { effectivePlayerColor } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';
import { SurprisingSaturdayService } from './surprising-saturday.service';

const PROXY_STALE_AFTER_MS = 10_000;
const COMMAND_TTL_MS = 30 * 60_000;
const SERVER_NAME_PATTERN = /^[a-z0-9][a-z0-9_-]{0,31}$/;
const EVENT_SERVER = 'surprising-saturday';

interface LiveServer {
	name: string;
	online: boolean;
	latencyMs: number | null;
	error: string | null;
}

interface LivePlayer {
	uuid: string;
	username: string;
	serverName: string;
}

interface MoveCommand {
	id: number;
	playerUuid: string;
	targetServerName: string;
	routeRevision: string | null;
	createdAtUnixMs: number;
}

interface SyncBody {
	servers?: unknown;
	players?: unknown;
	acknowledgedCommandIds?: unknown;
}

@Injectable()
export class VelocityService implements OnModuleInit, OnModuleDestroy {
	private liveServers = new Map<string, LiveServer>();
	private livePlayers: LivePlayer[] = [];
	private lastHeartbeatUnixMs: number | null = null;
	// ponytail: Pending moves live in one API process; persist them if restart-safe delivery is needed.
	private readonly commands = new Map<number, MoveCommand>();
	private nextCommandId = Date.now();
	private eventStateTimer?: NodeJS.Timeout;
	private lastEventState?: boolean;
	private readonly eventStatePath =
		process.env.EVENT_CONTROL_STATE_PATH ??
		join(
			dirname(process.env.DATABASE_URL ?? join(process.cwd(), 'data', 'app.sqlite')),
			'event-control.json',
		);

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly bans: PlayerBansService,
		private readonly events: SurprisingSaturdayService,
	) {}

	onModuleInit() {
		this.importLegacyChoices();
		this.refreshEventState();
		this.eventStateTimer = setInterval(() => {
			try {
				this.refreshEventState();
			} catch (error) {
				console.error('Could not update event server state', error);
			}
		}, 3_000);
	}

	onModuleDestroy() {
		if (this.eventStateTimer) clearInterval(this.eventStateTimer);
	}

	verifyInternalAuthorization(authorization: string | undefined) {
		requireInternalAuthorization(authorization, 'Invalid Velocity API credentials');
	}

	authorizePlayer(uuidInput: unknown, usernameInput: unknown) {
		const uuid = normalizeMinecraftUuid(typeof uuidInput === 'string' ? uuidInput : '');
		const username = typeof usernameInput === 'string' ? usernameInput.trim() : '';
		if (!uuid || !isValidMinecraftUsername(username))
			return { status: 'DENIED', websiteUrl: this.websiteUrl() };

		if (this.settings().maintenance_mode === 1)
			return { status: 'MAINTENANCE', websiteUrl: this.websiteUrl() };

		const user = this.identities.resolveAndRefresh(uuid, username);
		if (user) {
			const ban = this.bans.resolve(user.id);
			if (ban.active) {
				return {
					status: ban.expiresAtUnixMs === null ? 'BANNED' : 'TIMEOUT',
					expiresAtUnixMs: ban.expiresAtUnixMs,
					websiteUrl: this.websiteUrl(),
				};
			}
			return {
				status: 'ALLOWED',
				websiteUrl: this.websiteUrl(),
				preferredServerName: this.preferredServer(uuid),
			};
		}

		const now = Date.now();
		const flow = [...signupFlows.values()].find(
			(candidate) =>
				candidate.step === 'minecraft-code' &&
				candidate.minecraftUsername?.localeCompare(username, 'en', {
					sensitivity: 'base',
				}) === 0 &&
				(candidate.minecraftCodeExpiresAt ?? 0) > now &&
				candidate.minecraftCode,
		);
		if (!flow) return { status: 'SIGNUP_REQUIRED', websiteUrl: this.websiteUrl() };

		const uuidInAnotherFlow = [...signupFlows.values()].some(
			(candidate) => candidate !== flow && candidate.minecraftUuid === uuid,
		);
		if (uuidInAnotherFlow) return { status: 'SIGNUP_REQUIRED', websiteUrl: this.websiteUrl() };

		flow.minecraftUuid = uuid;
		flow.minecraftUsername = username;
		flow.updatedAt = now;
		return {
			status: 'SIGNUP_CODE',
			code: flow.minecraftCode,
			expiresAtUnixMs: flow.minecraftCodeExpiresAt,
			websiteUrl: this.websiteUrl(),
		};
	}

	synchronize(body: SyncBody | undefined) {
		const now = Date.now();
		for (const id of parseAcknowledgedCommandIds(body?.acknowledgedCommandIds))
			this.commands.delete(id);
		for (const [id, command] of this.commands)
			if (command.createdAtUnixMs + COMMAND_TTL_MS <= now) this.commands.delete(id);

		this.liveServers = new Map(
			parseLiveServers(body?.servers).map((server) => [server.name, server]),
		);
		this.livePlayers = parseLivePlayers(body?.players);
		this.lastHeartbeatUnixMs = now;
		for (const player of this.livePlayers)
			this.rememberConnectedServer(player.uuid, player.serverName);

		const settings = this.settings();
		const servers = this.servers();
		const { activeEvent, ...route } = this.eventRoute(now, settings.event_override);
		if (activeEvent && settings.event_override !== 0)
			this.events.recordParticipants(
				this.livePlayers
					.filter((player) => player.serverName === EVENT_SERVER)
					.map((player) => player.uuid),
				activeEvent.id,
				now,
			);
		for (const [id, command] of this.commands)
			if (command.routeRevision !== route.revision) this.commands.delete(id);

		const disconnects = this.livePlayers.flatMap((player) => {
			const user = this.identities.findByUuid(player.uuid);
			if (!user) return [];
			const ban = this.bans.resolve(user.id, now);
			if (!ban.active) return [];
			return [
				{
					playerUuid: player.uuid,
					status: ban.expiresAtUnixMs === null ? 'BANNED' : 'TIMEOUT',
					expiresAtUnixMs: ban.expiresAtUnixMs,
				},
			];
		});

		return {
			maintenanceMode: settings.maintenance_mode === 1,
			servers: servers.map((server) => ({ name: server.name, address: server.address })),
			route,
			commands: [...this.commands.values()].map((command) => ({
				id: command.id,
				playerUuid: command.playerUuid,
				targetServerName: command.targetServerName,
			})),
			disconnects,
		};
	}

	adminSnapshot() {
		const now = Date.now();
		const proxyOnline = this.proxyIsOnline(now);
		const servers = this.servers();
		const players = proxyOnline
			? this.livePlayers.map((player) => {
					const user = this.identities.findByUuid(player.uuid);
					const profile = user
						? this.database.connection
								.select()
								.from(playerProfiles)
								.where(eq(playerProfiles.user_id, user.id))
								.get()
						: null;
					return {
						...player,
						color: effectivePlayerColor(
							user?.minecraft_uuid ?? player.uuid,
							profile?.color_hex,
						),
						isCommittee: Boolean(
							user && (user.is_super_admin === 1 || user.is_committee === 1),
						),
						customEmojis: user ? customPlayerEmojis(profile?.custom_emojis_json) : [],
					};
				})
			: [];

		return {
			nowUnixMs: now,
			proxyOnline,
			lastHeartbeatUnixMs: this.lastHeartbeatUnixMs,
			maintenanceMode: this.settings().maintenance_mode === 1,
			eventOverride: this.settings().event_override,
			eventActive: Boolean(this.events.active(now)),
			servers: servers.map((server) => {
				const live = proxyOnline ? this.liveServers.get(server.name) : undefined;
				return {
					...this.publicServer(server),
					health: live ? (live.online ? 'online' : 'offline') : 'unknown',
					latencyMs: live?.latencyMs ?? null,
					error: live?.error ?? null,
					playerCount: players.filter((player) => player.serverName === server.name)
						.length,
				};
			}),
			players,
		};
	}

	setMaintenanceMode(enabledInput: unknown) {
		if (typeof enabledInput !== 'boolean')
			throw new BadRequestException('enabled must be a boolean');
		this.database.connection
			.update(velocitySettings)
			.set({ maintenance_mode: enabledInput ? 1 : 0 })
			.where(eq(velocitySettings.id, 1))
			.run();
		if (enabledInput) this.commands.clear();
		return { ok: true, maintenanceMode: enabledInput };
	}

	movePlayer(uuidInput: string, serverIdInput: unknown) {
		const uuid = normalizeMinecraftUuid(uuidInput);
		if (!uuid || !this.identities.findByUuid(uuid))
			throw new NotFoundException('Player not found');
		const serverId = parseSafeInteger(serverIdInput, 'Select a server');
		const server = this.database.connection
			.select()
			.from(velocityServers)
			.where(eq(velocityServers.id, serverId))
			.get();
		if (!server || !['main', EVENT_SERVER].includes(server.name))
			throw new NotFoundException('Server not found');
		if (server.name === EVENT_SERVER && !this.eventControlState().desiredRunning)
			throw new ConflictException('Surprising Saturday is stopped');
		if (server.name === EVENT_SERVER && this.events.warmingUp())
			throw new ConflictException('Surprising Saturday opens at the event start time');
		const player = this.proxyIsOnline()
			? this.livePlayers.find((candidate) => candidate.uuid === uuid)
			: undefined;
		this.database.connection
			.insert(velocityPlayerServers)
			.values({
				player_uuid: uuid,
				preferred_server: server.name,
				last_connected_server: player?.serverName ?? null,
			})
			.onConflictDoUpdate({
				target: velocityPlayerServers.player_uuid,
				set: { preferred_server: server.name },
			})
			.run();

		for (const [id, command] of this.commands)
			if (command.playerUuid === uuid) this.commands.delete(id);
		if (!player || player.serverName === server.name)
			return { ok: true, commandId: null, preferredServerName: server.name };
		const command: MoveCommand = {
			id: this.nextCommandId++,
			playerUuid: uuid,
			targetServerName: server.name,
			routeRevision: this.currentRouteRevision(),
			createdAtUnixMs: Date.now(),
		};
		this.commands.set(command.id, command);
		return { ok: true, commandId: command.id, preferredServerName: server.name };
	}

	myServer(userId: number) {
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		const uuid = normalizeMinecraftUuid(user?.minecraft_uuid ?? '');
		const now = Date.now();
		return {
			uuid,
			preferredServerName: uuid ? this.preferredServer(uuid) : 'main',
			serverName: this.proxyIsOnline(now)
				? (this.livePlayers.find((player) => player.uuid === uuid)?.serverName ?? null)
				: null,
			eventReady: Boolean(this.events.active(now)) && this.settings().event_override !== 0,
			eventOnline:
				this.proxyIsOnline(now) && this.liveServers.get(EVENT_SERVER)?.online === true,
		};
	}

	moveSelf(userId: number, serverName: unknown) {
		if (serverName !== 'main' && serverName !== EVENT_SERVER)
			throw new BadRequestException('Select main or Surprising Saturday');
		if (!this.events.active() || this.settings().event_override === 0)
			throw new ConflictException('Player switching is available during a live event');
		if (this.bans.resolve(userId).active)
			throw new ForbiddenException('Your Minecraft access is restricted');
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		const uuid = normalizeMinecraftUuid(user?.minecraft_uuid ?? '');
		if (!uuid) throw new NotFoundException('Minecraft account not linked');
		const server = this.servers().find((candidate) => candidate.name === serverName);
		if (!server) throw new NotFoundException('Server not found');
		return this.movePlayer(uuid, server.id);
	}

	private settings() {
		const settings = this.database.connection
			.select()
			.from(velocitySettings)
			.where(eq(velocitySettings.id, 1))
			.get();
		if (!settings) throw new Error('Velocity settings row is missing');
		return settings;
	}

	private servers() {
		return this.database.connection
			.select()
			.from(velocityServers)
			.where(inArray(velocityServers.name, ['main', EVENT_SERVER]))
			.all();
	}

	private currentRouteRevision(now = Date.now()) {
		return this.eventRoute(now, this.settings().event_override).revision;
	}

	private eventRoute(now: number, override: number | null) {
		const activeEvent = this.events.active(now);
		const warmingUp = activeEvent ? null : this.events.warmingUp(now);
		return {
			activeEvent,
			eventOpen: override !== 0 && (Boolean(activeEvent) || (override === 1 && !warmingUp)),
			revision: activeEvent
				? `event:${activeEvent.id}`
				: warmingUp
					? `warmup:${warmingUp.id}`
					: 'main',
			targetServerName: activeEvent && override !== 0 ? EVENT_SERVER : 'main',
		};
	}

	eventControlState() {
		const now = Date.now();
		const override = this.settings().event_override;
		const desiredRunning =
			override === null
				? Boolean(this.events.active(now) ?? this.events.warmingUp(now))
				: override === 1;
		return {
			desiredRunning,
			canStop:
				this.proxyIsOnline() &&
				this.livePlayers.every((player) => player.serverName !== EVENT_SERVER),
		};
	}

	setEventOverride(value: unknown) {
		if (value !== null && typeof value !== 'boolean')
			throw new BadRequestException(
				'enabled must be true, false, or null for automatic operation',
			);
		this.database.connection
			.update(velocitySettings)
			.set({ event_override: value === null ? null : value ? 1 : 0 })
			.where(eq(velocitySettings.id, 1))
			.run();
		this.refreshEventState();
		return { ok: true, eventOverride: value };
	}

	connectedPlayer(uuidInput: unknown, serverName: unknown) {
		const uuid = normalizeMinecraftUuid(typeof uuidInput === 'string' ? uuidInput : '');
		if (!uuid || (serverName !== 'main' && serverName !== EVENT_SERVER))
			throw new BadRequestException('Invalid player server');
		this.rememberConnectedServer(uuid, serverName);
		return { ok: true };
	}

	refreshEventState() {
		const desiredRunning = this.eventControlState().desiredRunning;
		if (desiredRunning === this.lastEventState && existsSync(this.eventStatePath)) return;
		const temporary = `${this.eventStatePath}.tmp`;
		writeFileSync(temporary, JSON.stringify({ desiredRunning }) + '\n', { mode: 0o644 });
		renameSync(temporary, this.eventStatePath);
		this.lastEventState = desiredRunning;
	}

	private preferredServer(uuid: string) {
		return (
			this.database.connection
				.select({ preferred: velocityPlayerServers.preferred_server })
				.from(velocityPlayerServers)
				.where(eq(velocityPlayerServers.player_uuid, uuid))
				.get()?.preferred ?? 'main'
		);
	}

	private rememberConnectedServer(uuid: string, serverName: string) {
		if (serverName !== 'main' && serverName !== EVENT_SERVER) return;
		const saved = this.database.connection
			.select()
			.from(velocityPlayerServers)
			.where(eq(velocityPlayerServers.player_uuid, uuid))
			.get();
		if (!saved) {
			this.database.connection
				.insert(velocityPlayerServers)
				.values({
					player_uuid: uuid,
					preferred_server: serverName,
					last_connected_server: serverName,
				})
				.run();
		} else if (saved.last_connected_server !== serverName) {
			const route = this.eventRoute(Date.now(), this.settings().event_override);
			this.database.connection
				.update(velocityPlayerServers)
				.set({
					preferred_server:
						saved.preferred_server === saved.last_connected_server &&
						serverName === route.targetServerName
							? serverName
							: saved.preferred_server,
					last_connected_server: serverName,
				})
				.where(eq(velocityPlayerServers.player_uuid, uuid))
				.run();
		}
	}

	private importLegacyChoices() {
		const path = join(
			dirname(process.env.DATABASE_URL ?? join(process.cwd(), 'data', 'app.sqlite')),
			'event-players-legacy.txt',
		);
		if (!existsSync(path)) return;
		for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
			const uuid = normalizeMinecraftUuid(line.trim());
			if (!uuid) continue;
			this.database.connection
				.insert(velocityPlayerServers)
				.values({
					player_uuid: uuid,
					preferred_server: EVENT_SERVER,
					last_connected_server: EVENT_SERVER,
				})
				.onConflictDoNothing()
				.run();
		}
	}

	private proxyIsOnline(now = Date.now()) {
		return (
			this.lastHeartbeatUnixMs !== null &&
			this.lastHeartbeatUnixMs + PROXY_STALE_AFTER_MS > now
		);
	}

	private websiteUrl() {
		return (process.env.PUBLIC_URL ?? 'https://mmuminecraftsociety.co.uk').replace(/\/$/, '');
	}

	private publicServer(server: typeof velocityServers.$inferSelect) {
		return {
			id: server.id,
			name: server.name,
			address: server.address,
			isDefault: server.is_default === 1,
		};
	}
}

function parseLiveServers(value: unknown): LiveServer[] {
	if (!Array.isArray(value)) return [];
	return value.slice(0, 200).flatMap((entry) => {
		if (!entry || typeof entry !== 'object') return [];
		const input = entry as Record<string, unknown>;
		const name = typeof input.name === 'string' ? input.name.trim().toLowerCase() : '';
		if (!SERVER_NAME_PATTERN.test(name) || typeof input.online !== 'boolean') return [];
		return [
			{
				name,
				online: input.online,
				latencyMs:
					typeof input.latencyMs === 'number' &&
					Number.isSafeInteger(input.latencyMs) &&
					input.latencyMs >= 0
						? input.latencyMs
						: null,
				error: typeof input.error === 'string' ? input.error.slice(0, 200) : null,
			},
		];
	});
}

function parseLivePlayers(value: unknown): LivePlayer[] {
	if (!Array.isArray(value)) return [];
	const seen = new Set<string>();
	return value.slice(0, 1_000).flatMap((entry) => {
		if (!entry || typeof entry !== 'object') return [];
		const input = entry as Record<string, unknown>;
		const uuid = normalizeMinecraftUuid(typeof input.uuid === 'string' ? input.uuid : '');
		const username = typeof input.username === 'string' ? input.username.trim() : '';
		const serverName =
			typeof input.serverName === 'string' ? input.serverName.trim().toLowerCase() : '';
		if (
			!uuid ||
			seen.has(uuid) ||
			!isValidMinecraftUsername(username) ||
			!SERVER_NAME_PATTERN.test(serverName)
		)
			return [];
		seen.add(uuid);
		return [{ uuid, username, serverName }];
	});
}

function parseAcknowledgedCommandIds(value: unknown) {
	if (!Array.isArray(value)) return [];
	return value
		.slice(0, 1_000)
		.filter((id): id is number => Number.isSafeInteger(id) && Number(id) > 0);
}

function parseSafeInteger(value: unknown, message: string) {
	if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0)
		throw new BadRequestException(message);
	return value;
}
