import { Module } from '@nestjs/common';
import { DatabaseService } from './database.service';
import { CommandLogsService } from './command-logs.service';
import { MinecraftIdentityService } from './minecraft-identity.service';
import { SigninAttemptLogsService } from './signin-attempt-logs.service';

@Module({
	providers: [
		DatabaseService,
		MinecraftIdentityService,
		CommandLogsService,
		SigninAttemptLogsService,
	],
	exports: [
		DatabaseService,
		MinecraftIdentityService,
		CommandLogsService,
		SigninAttemptLogsService,
	],
})
export class DatabaseModule {}
