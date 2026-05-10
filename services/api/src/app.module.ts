import { Module } from '@nestjs/common'
import { AuthController } from './auth/auth.controller'
import { AuthService } from './auth/auth.service'
import { DatabaseModule } from './database/database.module'
import { GrpcModule } from './grpc/grpc.module'
import { HealthController } from './health.controller'
import { KnowledgeModule } from './knowledge/knowledge.module'

@Module({
	imports: [DatabaseModule, KnowledgeModule, GrpcModule],
	controllers: [AuthController, HealthController],
	providers: [AuthService],
})
export class AppModule { }
