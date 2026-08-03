import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { DatabaseModule } from '../database/database.module'
import { GrpcModule } from '../grpc/grpc.module'
import { ClaimsController } from './claims.controller'
import { ClaimsService } from './claims.service'

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule],
	controllers: [ClaimsController],
	providers: [ClaimsService],
	exports: [ClaimsService],
})
export class ClaimsModule { }
