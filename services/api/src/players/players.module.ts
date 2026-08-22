import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { DatabaseModule } from '../database/database.module'
import { FishingModule } from '../fishing/fishing.module'
import { GrpcModule } from '../grpc/grpc.module'
import { PlayerAvatarController } from './player-avatar.controller'
import { PlayersController } from './players.controller'
import { PlayersService } from './players.service'

@Module({
	imports: [AuthModule, DatabaseModule, FishingModule, GrpcModule],
	controllers: [PlayersController, PlayerAvatarController],
	providers: [PlayersService],
	exports: [PlayersService],
})
export class PlayersModule { }
