import { Module } from '@nestjs/common'
import { AuthModule } from './auth/auth.module'
import { DatabaseModule } from './database/database.module'
import { GameplayModule } from './gameplay/gameplay.module'
import { GrpcModule } from './grpc/grpc.module'
import { HealthController } from './health.controller'

@Module({
	imports: [DatabaseModule, GrpcModule, AuthModule, GameplayModule],
	controllers: [HealthController],
})
export class AppModule { }
