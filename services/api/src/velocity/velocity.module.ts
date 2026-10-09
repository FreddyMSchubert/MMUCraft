import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { DiscordModule } from '../discord/discord.module';
import {
	SurprisingSaturdayAdminController,
	SurprisingSaturdayController,
	SurprisingSaturdayInternalController,
	VelocityAdminController,
	VelocityInternalController,
	VelocityPublicController,
} from './velocity.controller';
import { SurprisingSaturdayService } from './surprising-saturday.service';
import { VelocityService } from './velocity.service';

@Module({
	imports: [DatabaseModule, AuthModule, DiscordModule],
	controllers: [
		VelocityPublicController,
		VelocityAdminController,
		VelocityInternalController,
		SurprisingSaturdayController,
		SurprisingSaturdayAdminController,
		SurprisingSaturdayInternalController,
	],
	providers: [VelocityService, SurprisingSaturdayService],
	exports: [SurprisingSaturdayService],
})
export class VelocityModule {}
