import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { AuthController } from './auth.controller'
import { AuthGrpcService } from './auth-grpc.service'
import { AuthService } from './auth.service'
import { PlayerBansService } from './player-bans.service'

@Module({
	imports: [DatabaseModule],
	controllers: [AuthController],
	providers: [AuthService, AuthGrpcService, PlayerBansService],
	exports: [AuthService],
})
export class AuthModule { }
