import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { GrpcModule } from '../grpc/grpc.module';
import { BuildEventsAdminController, BuildEventsController } from './build-events.controller';
import { BuildEventsService } from './build-events.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule],
	controllers: [BuildEventsController, BuildEventsAdminController],
	providers: [BuildEventsService],
})
export class BuildEventsModule {}
