import { BadGatewayException, BadRequestException, Controller, Get, Header, Param, Query, StreamableFile } from '@nestjs/common'
import sharp from 'sharp'

@Controller('api/discord/avatar')
export class DiscordAvatarController {
	@Get(':uuid.png')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', 'public, max-age=86400, stale-while-revalidate=604800')
	async avatar(@Param('uuid') uuidInput: string, @Query('color') colorInput = 'e6e6e6') {
		const uuid = uuidInput.replaceAll('-', '')
		const color = colorInput.replace(/^#/, '')
		if (!/^[0-9a-f]{32}$/i.test(uuid) || !/^[0-9a-f]{6}$/i.test(color)) {
			throw new BadRequestException('Invalid Minecraft UUID or profile color')
		}

		const response = await fetch(`https://crafatar.com/avatars/${uuid}?size=96&overlay`)
		if (!response.ok) throw new BadGatewayException('The Minecraft avatar service did not return a player head')
		const head = Buffer.from(await response.arrayBuffer())
		if (head.length > 1_000_000) throw new BadGatewayException('The Minecraft avatar response was too large')
		const ring = Buffer.from(
			`<svg width="128" height="128" xmlns="http://www.w3.org/2000/svg"><circle cx="64" cy="64" r="58" fill="none" stroke="#${color}" stroke-width="10"/></svg>`,
		)

		const png = await sharp({
			create: { width: 128, height: 128, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
		}).composite([
			{ input: await sharp(head).resize(96, 96, { kernel: 'nearest' }).png().toBuffer(), left: 16, top: 16 },
			{ input: ring },
		]).png().toBuffer()

		return new StreamableFile(png)
	}
}
