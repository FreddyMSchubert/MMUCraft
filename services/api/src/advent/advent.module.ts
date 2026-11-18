import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { GrpcModule } from '../grpc/grpc.module';
import { PlayersModule } from '../players/players.module';
import { AdventAdminController, AdventController } from './advent.controller';
import { AdventService } from './advent.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule, PlayersModule],
	controllers: [AdventController, AdventAdminController],
	providers: [AdventService],
})
export class AdventModule {}
