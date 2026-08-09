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
import { DatabaseService, discordAdminCommandLogs } from '../database/database.service'

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

export function formatDiscordWebhookMessage(event: MinecraftDiscordEvent) {
	const emoji: Record<string, string> = {
		advancement: '🏆',
		dailies: '✅',
		shop: '🛒',
		charm: '✨',
	}
	const isServer = event.type !== 'chat' || !event.minecraft_username
	const label = roleLabel(event.role)
	const player = event.minecraft_username
		? `${ansi(ansiColor(event.color_hex))}${event.minecraft_username}${ansi(0)}${label ? `${ansi(roleColor(event.role))}${label}${ansi(0)}` : ''}`
		: ''
	const username = isServer
		? 'Minecraft Server'
		: `${event.minecraft_username}${label}`.slice(0, 80)
	return {
		username,
		content: (isServer
			? `\`\`\`ansi\n🤖 ${player ? `${player} ` : ''}${emoji[event.type] ? `${emoji[event.type]} ` : ''}${event.content.replaceAll('```', '``\\`')}\n\`\`\``
			: event.content).slice(0, 2_000),
		isServer,
	}
}

function roleLabel(role: string) {
	switch (role) {
		case 'Committee': return ' [Committee]'
		case 'Member': return ' [Member]'
		case 'External': return ' [External]'
		default: return ''
	}
}

function roleColor(role: string) {
	if (role === 'Committee') return 33
	if (role === 'Member') return 32
	if (role === 'External') return 30
	return 37
}

function ansi(color: number) {
	return `\u001b[${color}m`
}

export function ansiColor(color: string) {
	const rgb = /^#([0-9a-f]{6})$/i.exec(color)?.[1]
	if (!rgb) return 37
	const value = Number.parseInt(rgb, 16)
	const red = value >> 16 & 0xff
	const green = value >> 8 & 0xff
	const blue = value & 0xff
	const choices: Array<[number, number, number, number]> = [
		[31, 255, 0, 0], [33, 255, 255, 0], [32, 0, 200, 0], [34, 0, 100, 255],
		[35, 160, 32, 240], [30, 128, 128, 128], [37, 255, 255, 255],
	]
	return choices.reduce((best, candidate) => {
		const distance = (red - candidate[1]) ** 2 + (green - candidate[2]) ** 2 + (blue - candidate[3]) ** 2
		const bestDistance = (red - best[1]) ** 2 + (green - best[2]) ** 2 + (blue - best[3]) ** 2
		return distance < bestDistance ? candidate : best
	})[0]
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
	private readonly avatarBaseUrl = (process.env.DISCORD_AVATAR_BASE_URL
		?? (process.env.PUBLIC_URL ? `${process.env.PUBLIC_URL}/api/discord/avatar` : '')).replace(/\/$/, '')

	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly database: DatabaseService,
	) { }

	onApplicationBootstrap() {
		const token = process.env.DISCORD_BOT_TOKEN?.trim()
		const webhookUrl = process.env.DISCORD_WEBHOOK_URL?.trim()
		if (!token || !webhookUrl || !this.channelId) {
			this.logger.log('Discord bridge disabled; set DISCORD_BOT_TOKEN, DISCORD_WEBHOOK_URL, and DISCORD_CHANNEL_ID')
			return
		}
		if (!this.avatarBaseUrl) {
			this.logger.warn('Discord player avatars need a public DISCORD_AVATAR_BASE_URL or PUBLIC_URL')
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
		const message = formatDiscordWebhookMessage(event)
		if (!message.content.trim()) return false
		const avatarURL = !message.isServer && event.minecraft_uuid && this.avatarBaseUrl
			? `${this.avatarBaseUrl}/${event.minecraft_uuid}.png`
			: undefined
		await this.webhook.send({
			username: message.username,
			avatarURL,
			content: message.content,
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
		const command = interaction.options.getString('command', true)
		try {
			this.database.connection.insert(discordAdminCommandLogs).values({
				command,
				discord_username: interaction.user.tag,
				created_at_unix_ms: interaction.createdTimestamp,
			}).run()
			const response = await this.callMinecraft<{ succeeded: boolean; result: number; output: string }>('RunServerCommand', {
				command,
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
