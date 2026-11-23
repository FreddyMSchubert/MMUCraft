import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { GrpcModule } from '../grpc/grpc.module';
import { PresentsAdminController, PresentsController } from './presents.controller';
import { PresentsService } from './presents.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule],
	controllers: [PresentsController, PresentsAdminController],
	providers: [PresentsService],
	exports: [PresentsService],
})
export class PresentsModule {}
