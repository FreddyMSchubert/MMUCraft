import { Module } from '@nestjs/common'
import { DiscordService } from './discord.service'
import { DiscordAvatarController } from './discord-avatar.controller'

@Module({
	providers: [DiscordService],
	controllers: [DiscordAvatarController],
	exports: [DiscordService],
})
export class DiscordModule { }
