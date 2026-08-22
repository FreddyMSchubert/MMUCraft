import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { FishingController } from './fishing.controller';
import { FishCatalogService } from './fish-catalog.service';
import { FishingService } from './fishing.service';

@Module({
	imports: [AuthModule, DatabaseModule],
	controllers: [FishingController],
	providers: [FishingService, FishCatalogService],
	exports: [FishingService],
})
export class FishingModule {}
