import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { DatabaseModule } from '../database/database.module'
import { PlayersController } from './players.controller'
import { PlayersService } from './players.service'

@Module({
	imports: [AuthModule, DatabaseModule],
	controllers: [PlayersController],
	providers: [PlayersService],
	exports: [PlayersService],
})
export class PlayersModule { }
