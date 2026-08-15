import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { DiscordService } from './discord.service'
import { DiscordAvatarController } from './discord-avatar.controller'

@Module({
	imports: [DatabaseModule],
	providers: [DiscordService],
	controllers: [DiscordAvatarController],
	exports: [DiscordService],
})
export class DiscordModule { }
