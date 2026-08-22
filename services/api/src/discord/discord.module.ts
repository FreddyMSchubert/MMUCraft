import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { PlayersModule } from '../players/players.module'
import { DiscordService } from './discord.service'

@Module({
	imports: [DatabaseModule, PlayersModule],
	providers: [DiscordService],
	exports: [DiscordService],
})
export class DiscordModule { }
