import { Module } from '@nestjs/common';
import { KnowledgeModule } from './knowledge/knowledge.module';
import { GameplayGrpcService } from './gameplay-grpc.service';
import { DailiesModule } from './dailies/dailies.module';
import { ShopModule } from './shop/shop.module';
import { PlayersModule } from '../players/players.module';
import { FishingModule } from '../fishing/fishing.module';
import { ClaimsModule } from '../claims/claims.module';
import { DiscordModule } from '../discord/discord.module';
import { FeatureTogglesModule } from '../toggles/feature-toggles.module';

@Module({
	imports: [
		KnowledgeModule,
		DailiesModule,
		ShopModule,
		PlayersModule,
		FishingModule,
		ClaimsModule,
		DiscordModule,
		FeatureTogglesModule,
	],
	providers: [GameplayGrpcService],
})
export class GameplayModule {}
