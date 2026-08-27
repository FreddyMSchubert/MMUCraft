import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { GrpcModule } from '../grpc/grpc.module';
import {
	AdminClaimsController,
	AdminServerClaimsController,
	ClaimsController,
} from './claims.controller';
import { ClaimAdministrationService } from './claim-administration.service';
import { ClaimMinecraftSynchronizationService } from './claim-minecraft-synchronization.service';
import { ClaimPurchasingService } from './claim-purchasing.service';
import { ClaimsService } from './claims.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule],
	controllers: [ClaimsController, AdminClaimsController, AdminServerClaimsController],
	providers: [
		ClaimAdministrationService,
		ClaimPurchasingService,
		ClaimsService,
		ClaimMinecraftSynchronizationService,
	],
	exports: [ClaimsService, ClaimMinecraftSynchronizationService],
})
export class ClaimsModule {}
