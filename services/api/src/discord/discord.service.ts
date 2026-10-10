import { Injectable, Logger, OnApplicationBootstrap, OnModuleDestroy } from '@nestjs/common';
import {
	Client,
	ChatInputCommandInteraction,
	GatewayIntentBits,
	PermissionFlagsBits,
	SlashCommandBuilder,
	WebhookClient,
	type Collection,
	type Guild,
	type GuildMember,
	type Role,
	type Snowflake,
} from 'discord.js';
import { ADMIN_SIGNUP_EMAIL } from '../auth/auth.util';
import { DatabaseService, velocityServers } from '../database/database.service';
import { eq } from 'drizzle-orm';
import { CommandLogsService } from '../database/command-logs.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { OnlinePlayerPresenceService } from '../players/online-player-presence.service';

export interface MinecraftDiscordEvent {
	type: string;
	minecraft_username: string;
	minecraft_uuid: string;
	content: string;
	role: string;
	nickname: string;
	pronouns: string;
	color_hex: string;
	online_players: { minecraft_username: string; minecraft_uuid: string }[];
}

export function formatDiscordWebhookMessage(event: MinecraftDiscordEvent, maxPlayers?: number) {
	const type = event.type.replace(/^waiting_/, '');
	const emoji: Record<string, string> = {
		advancement: '🏆',
		deployment_complete: '✅',
		deployment_start: '🛠️',
		join: '➡️',
		leave: '⬅️',
		first_join: '➡️ 👶',
		dailies: '✅',
		shop: '🛒',
		charm: '✨',
		death: '☠️',
		fish: '🐟',
		fish_first: '🐟 👶',
	};
	const isServer = type !== 'chat' || !event.minecraft_username;
	const label = roleLabel(event.role);
	const player = event.minecraft_username
		? `${ansi(ansiColor(event.color_hex))}${event.minecraft_username}${ansi(0)}${label ? `${ansi(roleColor(event.role))}${label}${ansi(0)}` : ''}`
		: '';
	const username = isServer
		? 'Minecraft Server'
		: `${event.minecraft_username}${label}`.slice(0, 80);
	const prefix = emoji[type] ?? '👾';
	const content =
		(type === 'join' || type === 'first_join') && maxPlayers !== undefined
			? event.content.replace(/\((\d+) online\)$/, (_, countText: string) => {
					const count = Number(countText);
					const status = `(${count}/${maxPlayers} online)`;
					return count >= maxPlayers ? `${ansi(31)}${status}${ansi(0)}` : status;
				})
			: event.content;
	return {
		username,
		content: (isServer
			? `\`\`\`ansi\n${prefix} ${player ? `${player} ` : ''}${content.replaceAll('```', '``\\`')}\n\`\`\``
			: content
		).slice(0, 2_000),
		isServer,
	};
}

function roleLabel(role: string) {
	switch (role) {
		case 'Committee':
			return ' [Committee]';
		case 'Member':
			return ' [Member]';
		case 'External':
			return ' [External]';
		default:
			return '';
	}
}

function roleColor(role: string) {
	if (role === 'Committee') return 36;
	if (role === 'Member') return 32;
	if (role === 'External') return 30;
	return 37;
}

function ansi(color: number) {
	return `\u001b[${color}m`;
}

export function ansiColor(color: string) {
	const rgb = /^#([0-9a-f]{6})$/i.exec(color)?.[1];
	if (!rgb) return 37;
	const value = Number.parseInt(rgb, 16);
	const red = (value >> 16) & 0xff;
	const green = (value >> 8) & 0xff;
	const blue = value & 0xff;
	if (Math.max(red, green, blue) < 96) return 37;
	const choices: [number, number, number, number][] = [
		[31, 255, 0, 0],
		[33, 255, 255, 0],
		[32, 0, 200, 0],
		[34, 0, 100, 255],
		[35, 160, 32, 240],
		[30, 128, 128, 128],
		[37, 255, 255, 255],
	];
	return choices.reduce((best, candidate) => {
		const distance =
			(red - candidate[1]) ** 2 + (green - candidate[2]) ** 2 + (blue - candidate[3]) ** 2;
		const bestDistance = (red - best[1]) ** 2 + (green - best[2]) ** 2 + (blue - best[3]) ** 2;
		return distance < bestDistance ? candidate : best;
	})[0];
}

export function formatOnlinePlayers(
	players: { minecraftUsername: string; color: string; role: string }[],
) {
	const lines = players.map((player) => {
		const label = roleLabel(player.role);
		return `${ansi(ansiColor(player.color))}${player.minecraftUsername}${ansi(0)}${label ? `${ansi(roleColor(player.role))}${label}${ansi(0)}` : ''}`;
	});
	return `\`\`\`ansi\n${ansi(37)}Players online:${ansi(0)}\n${lines.join('\n') || 'No players online.'}\n\`\`\``;
}

@Injectable()
export class DiscordService implements OnApplicationBootstrap, OnModuleDestroy {
	private readonly logger = new Logger(DiscordService.name);
	private readonly channelId = process.env.DISCORD_CHANNEL_ID?.trim() ?? '';
	private readonly waitingChannelId = process.env.DISCORD_WAITING_ROOM_CHANNEL_ID?.trim() ?? '';
	private readonly eventChannelId = process.env.DISCORD_EVENT_CHANNEL_ID?.trim() ?? '';
	private readonly client = new Client({
		intents: [
			GatewayIntentBits.Guilds,
			GatewayIntentBits.GuildMembers,
			GatewayIntentBits.GuildMessages,
			GatewayIntentBits.MessageContent,
		],
	});
	private webhook: WebhookClient | null = null;
	private waitingWebhook: WebhookClient | null = null;
	private connectionIssue: string | null = null;
	private membershipContextCache: {
		fetchedAtUnixMs: number;
		guild: Guild;
		role: Role;
		members: Collection<Snowflake, GuildMember>;
	} | null = null;
	private draining = false;
	private drainPromise: Promise<void> | null = null;
	private readonly pending = new Set<Promise<unknown>>();
	private lobbyCursor = 0;
	// ponytail: This buffer can drop chat during outages. Persist it if chat must survive restarts.
	private readonly lobbyMessages: { id: number; name: string; content: string }[] = [];
	// ponytail: This buffer can drop chat during long API outages. Persist it if that matters.
	private readonly eventMessages: { id: string; name: string; content: string }[] = [];
	private nextWaitingCommandId = 0;
	private readonly waitingCommands = new Map<
		number,
		{
			command: string;
			dispatched: boolean;
			resolve: (result: string) => void;
		}
	>();
	private readonly avatarBaseUrl = (
		process.env.DISCORD_AVATAR_BASE_URL ??
		(process.env.PUBLIC_URL ? `${process.env.PUBLIC_URL}/api/discord/avatar` : '')
	).replace(/\/$/, '');

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly playerPresence: OnlinePlayerPresenceService,
		private readonly identities: MinecraftIdentityService,
		private readonly commandLogs: CommandLogsService,
	) {}

	onApplicationBootstrap() {
		const token = process.env.DISCORD_BOT_TOKEN?.trim();
		const webhookUrl = process.env.DISCORD_WEBHOOK_URL?.trim();
		const waitingWebhookUrl = process.env.DISCORD_WAITING_ROOM_WEBHOOK_URL?.trim();
		if (this.waitingChannelId && this.waitingChannelId === this.channelId) {
			this.connectionIssue = 'The waiting room and main Discord channels must differ';
			this.logger.error(this.connectionIssue);
			return;
		}
		if (!this.avatarBaseUrl) {
			this.logger.warn(
				'Discord player avatars need a public DISCORD_AVATAR_BASE_URL or PUBLIC_URL',
			);
		}

		if (webhookUrl && this.channelId) this.webhook = new WebhookClient({ url: webhookUrl });
		else
			this.logger.log(
				'Discord chat bridge disabled; set DISCORD_WEBHOOK_URL and DISCORD_CHANNEL_ID',
			);
		if (waitingWebhookUrl && this.waitingChannelId)
			this.waitingWebhook = new WebhookClient({ url: waitingWebhookUrl });
		else
			this.logger.warn(
				`Waiting room Discord webhook disabled: channel ID ${this.waitingChannelId ? 'set' : 'missing'}, webhook URL ${waitingWebhookUrl ? 'set' : 'missing'}`,
			);
		if (!token) {
			this.connectionIssue = 'DISCORD_BOT_TOKEN is missing from the API environment';
			this.logger.warn('Discord bot disabled; set DISCORD_BOT_TOKEN');
			return;
		}
		this.client.once('ready', () => {
			this.connectionIssue = null;
			void this.registerCommands().catch((error: unknown) => {
				this.logger.error('Could not register the Discord commands', error);
			});
		});
		this.client.on('shardDisconnect', (event) => {
			this.connectionIssue =
				event.code === 4014
					? 'Guild Members Intent is not enabled for this bot in the Discord Developer Portal'
					: `Discord gateway disconnected (code ${event.code})`;
		});
		this.client.on('messageCreate', (message) => {
			if (
				this.draining ||
				(message.channelId !== this.channelId &&
					message.channelId !== this.waitingChannelId &&
					message.channelId !== this.eventChannelId) ||
				message.author.bot ||
				message.webhookId
			)
				return;
			const attachments = [...message.attachments.values()].map(
				(attachment) => attachment.url,
			);
			const content = [message.content, ...attachments].filter(Boolean).join(' ');
			if (!content) return;
			if (message.channelId === this.waitingChannelId) {
				this.lobbyMessages.push({
					id: ++this.lobbyCursor,
					name: message.member?.displayName ?? message.author.displayName,
					content: content.slice(0, 500),
				});
				if (this.lobbyMessages.length > 100) this.lobbyMessages.shift();
			} else if (message.channelId === this.eventChannelId) {
				this.eventMessages.push({
					id: message.id,
					name: message.member?.displayName ?? message.author.displayName,
					content: content.slice(0, 500),
				});
				if (this.eventMessages.length > 100) this.eventMessages.shift();
			} else
				this.track(
					this.callMinecraft('BroadcastDiscordMessage', {
						discord_name: message.member?.displayName ?? message.author.displayName,
						content,
					}),
					'Could not send Discord message to Minecraft',
				);
		});
		this.client.on('interactionCreate', (interaction) => {
			if (this.draining || !interaction.isChatInputCommand()) return;
			if (interaction.commandName === 'mc')
				this.track(this.runCommand(interaction), 'Could not handle the Discord command');
			else if (interaction.commandName === 'players')
				this.track(this.listPlayers(interaction), 'Could not handle the Discord command');
		});
		void this.client.login(token).catch((error: unknown) => {
			this.connectionIssue = error instanceof Error ? error.message : 'Discord login failed';
			this.logger.error('Could not connect the Discord bot', error);
		});
	}

	async publish(event: MinecraftDiscordEvent) {
		const webhook = event.type.startsWith('waiting_') ? this.waitingWebhook : this.webhook;
		if (!webhook) return false;
		if (
			!['chat', 'waiting_chat'].includes(event.type) &&
			event.minecraft_uuid &&
			this.identities.findByUuid(event.minecraft_uuid)?.email === ADMIN_SIGNUP_EMAIL
		)
			return false;
		const presentation = event.minecraft_uuid
			? this.playerPresence.discordPresentation(event.minecraft_uuid)
			: null;
		const currentEvent = presentation
			? {
					...event,
					minecraft_username: presentation.minecraftUsername || event.minecraft_username,
					role: presentation.role,
					nickname: presentation.nickname,
					pronouns: presentation.pronouns,
					color_hex: presentation.colorHex,
				}
			: event;
		const maxPlayers = currentEvent.type.endsWith('join')
			? this.database.connection
					.select()
					.from(velocityServers)
					.where(
						eq(
							velocityServers.name,
							currentEvent.type.startsWith('waiting_') ? 'waiting-room' : 'main',
						),
					)
					.get()?.max_players
			: undefined;
		const message = formatDiscordWebhookMessage(currentEvent, maxPlayers);
		if (!message.content.trim()) return false;
		const avatarURL =
			!message.isServer && currentEvent.minecraft_uuid && this.avatarBaseUrl
				? `${this.avatarBaseUrl}/${currentEvent.minecraft_uuid}.png?v=${currentEvent.color_hex.replace('#', '').toLowerCase()}`
				: undefined;
		await webhook.send({
			username: message.username,
			avatarURL,
			content: message.content,
			allowedMentions: { parse: [] },
		});
		return true;
	}

	queueWaitingCommand(command: string) {
		if (!command.trim() || command.length > 512 || /[\r\n]/.test(command))
			throw new Error('Invalid waiting room command');
		if (this.waitingCommands.size >= 10) throw new Error('Too many waiting commands');
		return new Promise<string>((resolve) => {
			const id = ++this.nextWaitingCommandId;
			this.waitingCommands.set(id, { command, dispatched: false, resolve });
			setTimeout(() => {
				if (this.waitingCommands.delete(id))
					resolve(
						'No result within 15 seconds. The command may have run; check before retrying.',
					);
			}, 15_000);
		});
	}

	takeWaitingCommands() {
		return [...this.waitingCommands].flatMap(([id, entry]) => {
			if (entry.dispatched) return [];
			entry.dispatched = true;
			return [{ id, command: entry.command }];
		});
	}

	finishWaitingCommand(id: number, output: string) {
		const entry = this.waitingCommands.get(id);
		if (!entry?.dispatched) return false;
		this.waitingCommands.delete(id);
		entry.resolve(output);
		return true;
	}

	lobbyChat(after: number | null) {
		return {
			cursor: this.lobbyCursor,
			messages:
				after === null ? [] : this.lobbyMessages.filter((message) => message.id > after),
		};
	}

	eventChat(after: string | null) {
		const latest = this.eventMessages.at(-1)?.id;
		return {
			cursor:
				latest && (after === null || BigInt(latest) > BigInt(after))
					? latest
					: (after ?? '0'),
			messages:
				after === null
					? []
					: this.eventMessages.filter((message) => BigInt(message.id) > BigInt(after)),
		};
	}

	publishServer(type: string, content: string) {
		return this.publish({
			type,
			content,
			minecraft_username: '',
			minecraft_uuid: '',
			role: '',
			nickname: '',
			pronouns: '',
			color_hex: '',
			online_players: [],
		});
	}

	drain() {
		return (this.drainPromise ??= this.drainOnce());
	}

	private async drainOnce() {
		this.draining = true;
		await Promise.allSettled(this.pending);
	}

	async onModuleDestroy() {
		await this.drain();
		await this.client.destroy();
		this.webhook?.destroy();
		this.waitingWebhook?.destroy();
	}

	private track(task: Promise<unknown>, failureMessage: string) {
		this.pending.add(task);
		void task
			.catch((error: unknown) => {
				this.logger.error(failureMessage, error);
			})
			.finally(() => {
				this.pending.delete(task);
			});
	}

	private async registerCommands() {
		const commands = [
			new SlashCommandBuilder()
				.setName('mc')
				.setDescription('Run a Minecraft server console command')
				.addStringOption((option) =>
					option
						.setName('command')
						.setDescription('Command without the leading slash')
						.setRequired(true),
				)
				.setDefaultMemberPermissions(PermissionFlagsBits.Administrator),
			new SlashCommandBuilder()
				.setName('players')
				.setDescription('List the players who are online'),
		];
		const guildId = process.env.DISCORD_GUILD_ID?.trim();
		if (guildId) {
			const guild = await this.client.guilds.fetch(guildId);
			for (const command of commands) await guild.commands.create(command);
		} else {
			for (const command of commands) await this.client.application?.commands.create(command);
		}
		this.logger.log('Discord bridge connected');
	}

	async membershipRoleContext() {
		const guildId = process.env.DISCORD_GUILD_ID?.trim();
		if (!guildId) throw new Error('DISCORD_GUILD_ID is missing from the API environment');
		if (!this.client.isReady())
			throw new Error(
				this.connectionIssue ??
					'Discord bot is offline; check its API logs and Guild Members Intent',
			);
		const cached = this.membershipContextCache;
		if (cached && Date.now() - cached.fetchedAtUnixMs < 60_000)
			return { guild: cached.guild, role: cached.role, members: cached.members };
		const guild = await this.client.guilds.fetch(guildId);
		const role = await guild.roles.fetch('1500897435206287552');
		if (!role) throw new Error('The 26/27 Member role was not found');
		const bot = await guild.members.fetchMe();
		if (!bot.permissions.has(PermissionFlagsBits.ManageRoles))
			throw new Error('The bot does not have Manage Roles in this Discord server');
		if (bot.roles.highest.comparePositionTo(role) <= 0)
			throw new Error('Move the bot role above 26/27 Member in Server Settings → Roles');
		const members = await guild.members.fetch();
		this.membershipContextCache = {
			fetchedAtUnixMs: Date.now(),
			guild,
			role,
			members,
		};
		return { guild, role, members };
	}

	private async listPlayers(interaction: ChatInputCommandInteraction) {
		await interaction.deferReply({ ephemeral: true });
		try {
			const { players } = await this.playerPresence.listOnlinePlayers();
			await interaction.editReply(formatOnlinePlayers(players));
		} catch (error) {
			this.logger.error('Could not list online players', error);
			await interaction.editReply('Could not retrieve the online players.');
		}
	}

	private async runCommand(interaction: ChatInputCommandInteraction) {
		if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
			await interaction.reply({
				content: 'Discord did not report Administrator permission for your account here.',
				ephemeral: true,
			});
			return;
		}
		if (![this.channelId, this.waitingChannelId].includes(interaction.channelId)) {
			await interaction.reply({
				content: `This channel (${interaction.channelId}) does not match DISCORD_CHANNEL_ID (${this.channelId || 'unset'}) or DISCORD_WAITING_ROOM_CHANNEL_ID (${this.waitingChannelId || 'unset'}).`,
				ephemeral: true,
			});
			return;
		}

		await interaction.deferReply({ ephemeral: true });
		const command = interaction.options.getString('command', true);
		try {
			if (interaction.channelId === this.waitingChannelId) {
				this.commandLogs.record({
					command: command.replace(/^\//, ''),
					source: 'discord',
					actor_name: interaction.user.tag,
					is_operator: true,
				});
				const output = await this.queueWaitingCommand(command);
				await interaction.editReply(
					`\`\`\`\n${output.replaceAll('```', '``\\`').slice(0, 1900)}\n\`\`\``,
				);
				return;
			}
			const response = await this.callMinecraft<{
				succeeded: boolean;
				result: number;
				output: string;
			}>('RunServerCommand', {
				command,
				discord_user: interaction.user.tag,
			});
			const output = response.output || `Command returned ${response.result}.`;
			await interaction.editReply(
				`\`\`\`\n${output.replaceAll('```', '``\\`').slice(0, 1900)}\n\`\`\``,
			);
		} catch (error) {
			this.logger.error('Could not run Minecraft command', error);
			await interaction.editReply('The Minecraft server did not accept the command.');
		}
	}

	private async callMinecraft<T>(
		methodName: string,
		request: Record<string, unknown>,
	): Promise<T> {
		return await this.minecraft.gameplay<T>(methodName, request);
	}
}
