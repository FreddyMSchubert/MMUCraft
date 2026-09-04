import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { FishingModule } from '../fishing/fishing.module';
import { GrpcModule } from '../grpc/grpc.module';
import { KnowledgeDocumentCatalogService } from '../gameplay/knowledge/knowledge-document-catalog.service';
import { ShopItemCatalogService } from '../gameplay/shop/shop-item-catalog.service';
import { PlayerAvatarController } from './player-avatar.controller';
import { OnlinePlayerPresenceService } from './online-player-presence.service';
import { PlayersController } from './players.controller';
import { PlayerProfileStorageService } from './player-profile-storage.service';
import { PlayerMoneyHistoryService } from './player-money-history.service';
import { PlayerStatisticsSynchronizationService } from './player-statistics-synchronization.service';
import { PlayersService } from './players.service';

@Module({
	imports: [AuthModule, DatabaseModule, FishingModule, GrpcModule],
	controllers: [PlayersController, PlayerAvatarController],
	providers: [
		PlayersService,
		OnlinePlayerPresenceService,
		PlayerMoneyHistoryService,
		PlayerProfileStorageService,
		PlayerStatisticsSynchronizationService,
		KnowledgeDocumentCatalogService,
		ShopItemCatalogService,
	],
	exports: [
		PlayersService,
		OnlinePlayerPresenceService,
		PlayerMoneyHistoryService,
		PlayerStatisticsSynchronizationService,
	],
})
export class PlayersModule {}
