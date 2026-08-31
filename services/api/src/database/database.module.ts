import { Module } from '@nestjs/common';
import { DatabaseService } from './database.service';
import { CommandLogsService } from './command-logs.service';
import { MinecraftIdentityService } from './minecraft-identity.service';

@Module({
	providers: [DatabaseService, MinecraftIdentityService, CommandLogsService],
	exports: [DatabaseService, MinecraftIdentityService, CommandLogsService],
})
export class DatabaseModule {}
