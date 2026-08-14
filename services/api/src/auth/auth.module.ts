import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { AuthController } from './auth.controller'
import { AuthService } from './auth.service'
import { MinecraftGameplayClient } from './minecraft-gameplay-client.service'
import { PlayerBansService } from './player-bans.service'

@Module({
	imports: [DatabaseModule],
	controllers: [AuthController],
	providers: [AuthService, MinecraftGameplayClient, PlayerBansService],
	exports: [AuthService, PlayerBansService],
})
export class AuthModule { }
