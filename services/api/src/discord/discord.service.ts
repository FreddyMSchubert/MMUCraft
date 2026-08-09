import { Injectable, Logger, OnApplicationBootstrap, OnModuleDestroy } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import {
	Client,
	ChatInputCommandInteraction,
	GatewayIntentBits,
	PermissionFlagsBits,
	SlashCommandBuilder,
	WebhookClient,
} from 'discord.js'
import { GrpcServerService } from '../grpc/grpc-server.service'

export interface MinecraftDiscordEvent {
	type: string
	minecraft_username: string
	minecraft_uuid: string
	content: string
	role: string
	nickname: string
	pronouns: string
	color_hex: string
}

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } }
}

@Injectable()
export class DiscordService implements OnApplicationBootstrap, OnModuleDestroy {
	private readonly logger = new Logger(DiscordService.name)
	private readonly channelId = process.env.DISCORD_CHANNEL_ID?.trim() ?? ''
	private readonly client = new Client({
		intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent],
	})
	private webhook: WebhookClient | null = null
	private minecraft: grpc.Client | null = null
	private readonly avatarBaseUrl = process.env.DISCORD_AVATAR_BASE_URL?.replace(/\/$/, '') ?? ''

	constructor(private readonly grpcServer: GrpcServerService) { }

	onApplicationBootstrap() {
		const token = process.env.DISCORD_BOT_TOKEN?.trim()
		const webhookUrl = process.env.DISCORD_WEBHOOK_URL?.trim()
		if (!token || !webhookUrl || !this.channelId) {
			this.logger.log('Discord bridge disabled; set DISCORD_BOT_TOKEN, DISCORD_WEBHOOK_URL, and DISCORD_CHANNEL_ID')
			return
		}

		this.webhook = new WebhookClient({ url: webhookUrl })
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.minecraft = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)

		this.client.once('ready', () => void this.registerCommand()
			.catch((error) => this.logger.error('Could not register the Discord command', error)))
		this.client.on('messageCreate', (message) => {
			if (message.channelId !== this.channelId || message.author.bot || message.webhookId) return
			const attachments = [...message.attachments.values()].map((attachment) => attachment.url)
			const content = [message.content, ...attachments].filter(Boolean).join(' ')
			if (!content) return
			void this.callMinecraft('BroadcastDiscordMessage', {
				discord_name: message.member?.displayName ?? message.author.displayName,
				content,
			}).catch((error) => this.logger.error('Could not send Discord message to Minecraft', error))
		})
		this.client.on('interactionCreate', (interaction) => {
			if (!interaction.isChatInputCommand() || interaction.commandName !== 'mc') return
			void this.runCommand(interaction)
		})
		void this.client.login(token).catch((error) => this.logger.error('Could not connect the Discord bot', error))
	}

	async publish(event: MinecraftDiscordEvent) {
		if (!this.webhook) return false

		const emoji: Record<string, string> = {
			advancement: '🏆',
			join: '🟢',
			leave: '🔴',
			first_join: '🎉',
			dailies: '✅',
			shop: '🛒',
			charm: '✨',
			server: '📣',
		}
		const profile = event.minecraft_username
			? `**${event.role || 'Player'}** · (MC: ${event.minecraft_username} · Nickname: ${event.nickname || '—'} · Pronouns: ${event.pronouns || '—'})\n`
			: ''
		const prefix = emoji[event.type] ? `${emoji[event.type]} ` : ''
		const color = /^#[0-9a-f]{6}$/i.test(event.color_hex) ? Number.parseInt(event.color_hex.slice(1), 16) : 0x5865f2

		const avatarURL = event.minecraft_uuid && this.avatarBaseUrl
			? `${this.avatarBaseUrl}/${event.minecraft_uuid}.png?color=${event.color_hex.replace(/^#/, '')}`
			: event.minecraft_uuid
				? `https://crafatar.com/avatars/${event.minecraft_uuid.replaceAll('-', '')}?size=128&overlay`
				: undefined
		await this.webhook.send({
			username: event.minecraft_username || 'Minecraft Server',
			avatarURL,
			embeds: [{ color, description: `${profile}${prefix}${event.content}`.slice(0, 4096) }],
			allowedMentions: { parse: [] },
		})
		return true
	}

	onModuleDestroy() {
		this.minecraft?.close()
		this.webhook?.destroy()
		this.client.destroy()
	}

	private async registerCommand() {
		const command = new SlashCommandBuilder()
			.setName('mc')
			.setDescription('Run a Minecraft server console command')
			.addStringOption((option) => option.setName('command').setDescription('Command without the leading slash').setRequired(true))
			.setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
		const guildId = process.env.DISCORD_GUILD_ID?.trim()
		if (guildId) {
			await (await this.client.guilds.fetch(guildId)).commands.create(command)
		} else {
			await this.client.application?.commands.create(command)
		}
		this.logger.log('Discord bridge connected')
	}

	private async runCommand(interaction: ChatInputCommandInteraction) {
		if (interaction.channelId !== this.channelId || !interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
			await interaction.reply({ content: 'This command is restricted to server administrators in the bridge channel.', ephemeral: true })
			return
		}

		await interaction.deferReply({ ephemeral: true })
		try {
			const response = await this.callMinecraft<{ succeeded: boolean; result: number; output: string }>('RunServerCommand', {
				command: interaction.options.getString('command', true),
				discord_user: interaction.user.tag,
			})
			const output = response.output || `Command returned ${response.result}.`
			await interaction.editReply(`\`\`\`\n${output.replaceAll('```', '``\\`').slice(0, 1900)}\n\`\`\``)
		} catch (error) {
			this.logger.error('Could not run Minecraft command', error)
			await interaction.editReply('The Minecraft server did not accept the command.')
		}
	}

	private async callMinecraft<T>(methodName: string, request: Record<string, unknown>): Promise<T> {
		if (!this.minecraft) throw new Error('Minecraft gRPC client is not initialized')
		const method = (this.minecraft as unknown as Record<string, unknown>)[methodName]
		if (typeof method !== 'function') throw new Error(`Unknown GameplayControl method: ${methodName}`)
		return await new Promise<T>((resolve, reject) => method.call(this.minecraft, request, (
			error: grpc.ServiceError | null,
			response: T,
		) => error ? reject(error) : resolve(response)))
	}
}
