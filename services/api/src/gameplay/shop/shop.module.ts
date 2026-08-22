import { Module } from '@nestjs/common';
import { AuthModule } from '../../auth/auth.module';
import { DatabaseModule } from '../../database/database.module';
import { GrpcModule } from '../../grpc/grpc.module';
import { KnowledgeModule } from '../knowledge/knowledge.module';
import { ShopCharmInventoryService } from './shop-charm-inventory.service';
import { ShopController } from './shop.controller';
import { ShopItemCatalogService } from './shop-item-catalog.service';
import { ShopPurchasesService } from './shop-purchases.service';
import { ShopUnlocksService } from './shop-unlocks.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule, KnowledgeModule],
	controllers: [ShopController],
	providers: [
		ShopCharmInventoryService,
		ShopItemCatalogService,
		ShopPurchasesService,
		ShopUnlocksService,
	],
	exports: [ShopItemCatalogService, ShopUnlocksService],
})
export class ShopModule {}
