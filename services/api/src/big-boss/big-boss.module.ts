import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { DiscordModule } from '../discord/discord.module';
import { FeatureTogglesModule } from '../toggles/feature-toggles.module';
import { BigBossController } from './big-boss.controller';
import { BigBossService } from './big-boss.service';

@Module({
	imports: [AuthModule, DatabaseModule, DiscordModule, FeatureTogglesModule],
	controllers: [BigBossController],
	providers: [BigBossService],
	exports: [BigBossService],
})
export class BigBossModule {}
