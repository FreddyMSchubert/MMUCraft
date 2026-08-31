import { Injectable, Logger, OnApplicationBootstrap, OnModuleDestroy } from '@nestjs/common';
import {
	Client,
	ChatInputCommandInteraction,
	GatewayIntentBits,
	PermissionFlagsBits,
	SlashCommandBuilder,
	WebhookClient,
} from 'discord.js';
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
}

export function formatDiscordWebhookMessage(event: MinecraftDiscordEvent) {
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
	const isServer = event.type !== 'chat' || !event.minecraft_username;
	const label = roleLabel(event.role);
	const player = event.minecraft_username
		? `${ansi(ansiColor(event.color_hex))}${event.minecraft_username}${ansi(0)}${label ? `${ansi(roleColor(event.role))}${label}${ansi(0)}` : ''}`
		: '';
	const username = isServer
		? 'Minecraft Server'
		: `${event.minecraft_username}${label}`.slice(0, 80);
	const prefix = emoji[event.type] ?? '👾';
	return {
		username,
		content: (isServer
			? `\`\`\`ansi\n${prefix} ${player ? `${player} ` : ''}${event.content.replaceAll('```', '``\\`')}\n\`\`\``
			: event.content
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
	private readonly client = new Client({
		intents: [
			GatewayIntentBits.Guilds,
			GatewayIntentBits.GuildMessages,
			GatewayIntentBits.MessageContent,
		],
	});
	private webhook: WebhookClient | null = null;
	private draining = false;
	private drainPromise: Promise<void> | null = null;
	private readonly pending = new Set<Promise<unknown>>();
	private readonly avatarBaseUrl = (
		process.env.DISCORD_AVATAR_BASE_URL ??
		(process.env.PUBLIC_URL ? `${process.env.PUBLIC_URL}/api/players/avatar` : '')
	).replace(/\/$/, '');

	constructor(
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly playerPresence: OnlinePlayerPresenceService,
	) {}

	onApplicationBootstrap() {
		const token = process.env.DISCORD_BOT_TOKEN?.trim();
		const webhookUrl = process.env.DISCORD_WEBHOOK_URL?.trim();
		if (!token || !webhookUrl || !this.channelId) {
			this.logger.log(
				'Discord bridge disabled; set DISCORD_BOT_TOKEN, DISCORD_WEBHOOK_URL, and DISCORD_CHANNEL_ID',
			);
			return;
		}
		if (!this.avatarBaseUrl) {
			this.logger.warn(
				'Discord player avatars need a public DISCORD_AVATAR_BASE_URL or PUBLIC_URL',
			);
		}

		this.webhook = new WebhookClient({ url: webhookUrl });
		this.client.once(
			'ready',
			() =>
				void this.registerCommands().catch((error: unknown) => {
					this.logger.error('Could not register the Discord commands', error);
				}),
		);
		this.client.on('messageCreate', (message) => {
			if (
				this.draining ||
				message.channelId !== this.channelId ||
				message.author.bot ||
				message.webhookId
			)
				return;
			const attachments = [...message.attachments.values()].map(
				(attachment) => attachment.url,
			);
			const content = [message.content, ...attachments].filter(Boolean).join(' ');
			if (!content) return;
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
			this.logger.error('Could not connect the Discord bot', error);
		});
	}

	async publish(event: MinecraftDiscordEvent) {
		if (!this.webhook) return false;
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
		const message = formatDiscordWebhookMessage(currentEvent);
		if (!message.content.trim()) return false;
		const avatarURL =
			!message.isServer && currentEvent.minecraft_uuid && this.avatarBaseUrl
				? `${this.avatarBaseUrl}/${currentEvent.minecraft_uuid}.png?v=${currentEvent.color_hex.replace('#', '').toLowerCase()}`
				: undefined;
		await this.webhook.send({
			username: message.username,
			avatarURL,
			content: message.content,
			allowedMentions: { parse: [] },
		});
		return true;
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
		});
	}

	drain() {
		return (this.drainPromise ??= this.drainOnce());
	}

	private async drainOnce() {
		this.draining = true;
		await this.client.destroy();
		await Promise.allSettled(this.pending);
	}

	async onModuleDestroy() {
		await this.drain();
		this.webhook?.destroy();
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
		if (
			interaction.channelId !== this.channelId ||
			!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)
		) {
			await interaction.reply({
				content:
					'This command is restricted to server administrators in the bridge channel.',
				ephemeral: true,
			});
			return;
		}

		await interaction.deferReply({ ephemeral: true });
		const command = interaction.options.getString('command', true);
		try {
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
