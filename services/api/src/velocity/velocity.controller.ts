import {
	BadRequestException,
	Body,
	Controller,
	Delete,
	Get,
	Headers,
	Param,
	Patch,
	Post,
	Query,
	ServiceUnavailableException,
} from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { DiscordService } from '../discord/discord.service';
import { CommandLogsService } from '../database/command-logs.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { SurprisingSaturdayService } from './surprising-saturday.service';
import { VelocityService } from './velocity.service';

@Controller('api/velocity')
export class VelocityPublicController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly velocity: VelocityService,
	) {}

	@Get('capacity')
	capacity(@Headers('cookie') cookieHeader: string | undefined) {
		this.sessions.requireSession(cookieHeader);
		return this.velocity.capacitySnapshot();
	}
}

@Controller('api/admin/velocity')
export class VelocityAdminController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly velocity: VelocityService,
	) {}

	@Get()
	snapshot(@Headers('cookie') cookieHeader: string | undefined) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.adminSnapshot();
	}

	@Patch('event-override')
	setEventOverride(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.setEventOverride(body?.enabled);
	}

	@Patch('maintenance')
	setMaintenanceMode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.setMaintenanceMode(body?.enabled);
	}

	@Patch('max-players')
	setMaxPlayers(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { maxPlayers?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.setMaxPlayers(body?.maxPlayers);
	}

	@Post('players/:uuid/move')
	movePlayer(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('uuid') uuid: string,
		@Body() body: { serverId?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.movePlayer(uuid, body?.serverId);
	}
}

@Controller('api/internal/velocity')
export class VelocityInternalController {
	constructor(
		private readonly velocity: VelocityService,
		private readonly discord: DiscordService,
		private readonly commandLogs: CommandLogsService,
	) {}

	@Post('command-log')
	recordCommand(
		@Headers('authorization') authorization: string | undefined,
		@Body()
		body:
			| { uuid?: unknown; username?: unknown; serverName?: unknown; command?: unknown }
			| undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		if (
			typeof body?.uuid !== 'string' ||
			!normalizeMinecraftUuid(body.uuid) ||
			typeof body.username !== 'string' ||
			!/^[A-Za-z0-9_]{3,16}$/.test(body.username) ||
			typeof body.command !== 'string' ||
			!body.command.trim() ||
			body.command.length > 32767 ||
			!['main', 'waiting-room', 'surprising-saturday', 'proxy'].includes(
				String(body.serverName),
			)
		)
			throw new BadRequestException('Invalid command log entry');
		return this.commandLogs.record({
			command: body.command.replace(/^\//, ''),
			actor_name: body.username,
			minecraft_uuid: body.uuid,
			source: body.serverName === 'main' ? 'minecraft' : String(body.serverName),
		});
	}

	@Get('player-count')
	playerCount(@Headers('authorization') authorization: string | undefined) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.onlinePlayerCount();
	}

	@Post('access')
	access(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; username?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.authorizePlayer(body?.uuid, body?.username);
	}

	@Post('sync')
	sync(
		@Headers('authorization') authorization: string | undefined,
		@Body()
		body:
			| {
					servers?: unknown;
					players?: unknown;
					acknowledgedCommandIds?: unknown;
			  }
			| undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.synchronize(body);
	}

	@Post('connected')
	connected(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; serverName?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.connectedPlayer(body?.uuid, body?.serverName);
	}

	@Post('waiting-state')
	waitingState(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; username?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.markQueued(body?.uuid, body?.username);
	}

	@Get('event-control')
	eventControl(@Headers('authorization') authorization: string | undefined) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.eventControlState();
	}

	@Get('waiting-chat')
	waitingChat(
		@Headers('authorization') authorization: string | undefined,
		@Query('after') after: string | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		if (after !== undefined && !/^(0|[1-9][0-9]{0,14})$/.test(after))
			throw new BadRequestException('Invalid chat cursor');
		return this.discord.lobbyChat(after === undefined ? null : Number(after));
	}

	@Post('waiting-chat')
	async publishWaitingChat(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; content?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		const player = this.velocity.waitingRoomPlayer(
			typeof body?.uuid === 'string' ? body.uuid : '',
		);
		const content = typeof body?.content === 'string' ? body.content.trim() : '';
		if (!player || !content || content.length > 500)
			throw new BadRequestException('Invalid waiting room message');
		const accepted = await this.discord.publish({
			type: 'waiting_chat',
			minecraft_username: player.username,
			minecraft_uuid: player.uuid,
			content,
			role: '',
			nickname: '',
			pronouns: '',
			color_hex: '',
			online_players: [],
		});
		if (!accepted)
			throw new ServiceUnavailableException('Waiting room chat bridge is unavailable');
		return { accepted: true };
	}

	@Get('waiting-commands')
	waitingCommands(@Headers('authorization') authorization: string | undefined) {
		this.velocity.verifyInternalAuthorization(authorization);
		return { commands: this.discord.takeWaitingCommands() };
	}

	@Post('waiting-commands/:id/result')
	waitingCommandResult(
		@Headers('authorization') authorization: string | undefined,
		@Param('id') id: string,
		@Body() body: { output?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		if (
			!/^[1-9][0-9]{0,14}$/.test(id) ||
			typeof body?.output !== 'string' ||
			body.output.length > 8000
		)
			throw new BadRequestException('Invalid command result');
		return { accepted: this.discord.finishWaitingCommand(Number(id), body.output) };
	}
}

@Controller('api/surprising-saturday')
export class SurprisingSaturdayController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly events: SurprisingSaturdayService,
		private readonly velocity: VelocityService,
	) {}

	@Get()
	list() {
		return this.events.list();
	}

	@Get('me')
	me(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.sessions.requireSession(cookieHeader);
		return this.velocity.myServer(user.id);
	}

	@Post('me/server')
	moveSelf(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { serverName?: unknown } | undefined,
	) {
		const user = this.sessions.requireSession(cookieHeader);
		return this.velocity.moveSelf(user.id, body?.serverName);
	}

	@Get(':id')
	detail(@Param('id') id: string) {
		return this.events.detail(id);
	}
}

@Controller('api/admin/surprising-saturday')
export class SurprisingSaturdayAdminController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly events: SurprisingSaturdayService,
		private readonly velocity: VelocityService,
	) {}

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.list(true);
	}

	@Get(':id')
	detail(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.adminDetail(id);
	}

	@Post()
	create(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		const result = this.events.create(body);
		this.velocity.refreshEventState();
		return result;
	}

	@Patch(':id')
	update(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		const result = this.events.update(id, body);
		this.velocity.refreshEventState();
		return result;
	}

	@Delete(':id')
	remove(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.sessions.requireCommitteeSession(cookieHeader);
		const result = this.events.remove(id);
		this.velocity.refreshEventState();
		return result;
	}
}

@Controller('api/internal/surprising-saturday')
export class SurprisingSaturdayInternalController {
	constructor(
		private readonly velocity: VelocityService,
		private readonly events: SurprisingSaturdayService,
	) {}

	@Get('player-data/:uuid')
	playerData(
		@Headers('authorization') authorization: string | undefined,
		@Param('uuid') uuid: string,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.playerData(uuid);
	}

	@Get('score/:uuid')
	score(
		@Headers('authorization') authorization: string | undefined,
		@Param('uuid') uuid: string,
		@Query('atUnixMs') atUnixMs: string | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.score(uuid, atUnixMs);
	}

	@Post('list-completion')
	completion(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.recordCompletion(body);
	}
}
