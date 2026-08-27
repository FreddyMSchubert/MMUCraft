import { Module } from '@nestjs/common';
import { AuthModule } from './auth/auth.module';
import { DatabaseModule } from './database/database.module';
import { GameplayModule } from './gameplay/gameplay.module';
import { GrpcModule } from './grpc/grpc.module';
import { GiftsModule } from './gifts/gifts.module';
import { HealthController } from './health.controller';
import { PlayersModule } from './players/players.module';
import { DiscordModule } from './discord/discord.module';
import { ShutdownModule } from './shutdown';
import { MonitoringModule } from './monitoring/monitoring.module';
import { VelocityModule } from './velocity/velocity.module';

@Module({
	imports: [
		DatabaseModule,
		GrpcModule,
		DiscordModule,
		AuthModule,
		GameplayModule,
		PlayersModule,
		GiftsModule,
		ShutdownModule,
		MonitoringModule,
		VelocityModule,
	],
	controllers: [HealthController],
})
export class AppModule {}
