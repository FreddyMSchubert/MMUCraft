import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { PlayersModule } from '../players/players.module'
import { DiscordService } from './discord.service'
import { DiscordAvatarController } from './discord-avatar.controller'

@Module({
	imports: [DatabaseModule, PlayersModule],
	providers: [DiscordService],
	controllers: [DiscordAvatarController],
	exports: [DiscordService],
})
export class DiscordModule { }
