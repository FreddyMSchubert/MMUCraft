import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { DiscordModule } from '../discord/discord.module';
import { ShopModule } from '../gameplay/shop/shop.module';
import { GrpcModule } from '../grpc/grpc.module';
import { FeatureTogglesModule } from '../toggles/feature-toggles.module';
import { DucksController } from './ducks.controller';
import { DucksService } from './ducks.service';

@Module({
	imports: [
		AuthModule,
		DatabaseModule,
		DiscordModule,
		GrpcModule,
		ShopModule,
		FeatureTogglesModule,
	],
	controllers: [DucksController],
	providers: [DucksService],
	exports: [DucksService],
})
export class DucksModule {}
