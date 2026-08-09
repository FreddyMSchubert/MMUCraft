import { BadGatewayException, BadRequestException, Controller, Get, Header, Param, StreamableFile } from '@nestjs/common'
import { eq } from 'drizzle-orm'
import sharp from 'sharp'
import { DatabaseService, playerProfiles, users } from '../database/database.service'
import { effectivePlayerColor } from '../players/player-color'
import { fetchMojangProfileByUuid } from '../players/players.service'

@Controller('api/discord/avatar')
export class DiscordAvatarController {
	constructor(private readonly database: DatabaseService) { }

	@Get(':uuid.png')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', 'public, max-age=300, must-revalidate')
	async avatar(@Param('uuid') uuidInput: string) {
		const uuid = uuidInput.replaceAll('-', '')
		if (!/^[0-9a-f]{32}$/i.test(uuid)) {
			throw new BadRequestException('Invalid Minecraft UUID')
		}

		const user = this.database.connection.select().from(users)
			.where(eq(users.minecraft_uuid, uuid)).get()
		const profile = user
			? this.database.connection.select().from(playerProfiles).where(eq(playerProfiles.user_id, user.id)).get()
			: null
		const color = effectivePlayerColor(uuid, profile?.color_hex).slice(1)
		const skinUrl = (await fetchMojangProfileByUuid(uuid, user?.minecraft_username ?? '')).skinUrl
		if (!skinUrl) throw new BadGatewayException('Mojang did not return a player skin')
		const response = await fetch(skinUrl)
		if (!response.ok) throw new BadGatewayException('The Minecraft avatar service did not return a player head')
		const head = Buffer.from(await response.arrayBuffer())
		if (head.length > 1_000_000) throw new BadGatewayException('The Minecraft avatar response was too large')
		const skin = sharp(head)
		const metadata = await skin.metadata()
		if ((metadata.width ?? 0) < 48 || (metadata.height ?? 0) < 16) {
			throw new BadGatewayException('Mojang returned an invalid player skin')
		}
		const ring = Buffer.from(
			`<svg width="128" height="128" xmlns="http://www.w3.org/2000/svg"><circle cx="64" cy="64" r="58" fill="none" stroke="#${color}" stroke-width="10"/></svg>`,
		)

		const face = await skin.clone().extract({ left: 8, top: 8, width: 8, height: 8 })
			.resize(96, 96, { kernel: 'nearest' }).png().toBuffer()
		const hat = await skin.clone().extract({ left: 40, top: 8, width: 8, height: 8 })
			.resize(96, 96, { kernel: 'nearest' }).png().toBuffer()
		const png = await sharp({
			create: { width: 128, height: 128, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
		}).composite([
			{ input: face, left: 16, top: 16 },
			{ input: hat, left: 16, top: 16 },
			{ input: ring },
		]).png().toBuffer()

		return new StreamableFile(png)
	}
}
