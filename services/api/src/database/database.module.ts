import { Module } from '@nestjs/common';
import { DatabaseService } from './database.service';
import { CommandLogsService } from './command-logs.service';
import { DabloonInteractionsService } from './dabloon-interactions.service';
import { MinecraftIdentityService } from './minecraft-identity.service';
import { SigninAttemptLogsService } from './signin-attempt-logs.service';
import { ReferralsService } from './referrals.service';

@Module({
	providers: [
		DatabaseService,
		MinecraftIdentityService,
		CommandLogsService,
		DabloonInteractionsService,
		SigninAttemptLogsService,
		ReferralsService,
	],
	exports: [
		DatabaseService,
		MinecraftIdentityService,
		CommandLogsService,
		DabloonInteractionsService,
		SigninAttemptLogsService,
		ReferralsService,
	],
})
export class DatabaseModule {}
