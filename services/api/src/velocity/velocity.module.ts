import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import {
	SurprisingSaturdayAdminController,
	SurprisingSaturdayController,
	SurprisingSaturdayInternalController,
	VelocityAdminController,
	VelocityInternalController,
} from './velocity.controller';
import { SurprisingSaturdayService } from './surprising-saturday.service';
import { VelocityService } from './velocity.service';

@Module({
	imports: [DatabaseModule, AuthModule],
	controllers: [
		VelocityAdminController,
		VelocityInternalController,
		SurprisingSaturdayController,
		SurprisingSaturdayAdminController,
		SurprisingSaturdayInternalController,
	],
	providers: [VelocityService, SurprisingSaturdayService],
})
export class VelocityModule {}
