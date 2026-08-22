import { Module } from '@nestjs/common';
import { AuthModule } from '../../auth/auth.module';
import { DatabaseModule } from '../../database/database.module';
import { PlayersModule } from '../../players/players.module';
import { ShopModule } from '../shop/shop.module';
import { AdminDailiesController, DailiesController } from './dailies.controller';
import { DailyAdminRefreshService } from './daily-admin-refresh.service';
import { DailyBonusClaimsService } from './daily-bonus-claims.service';
import { DailyGeneratedTaskClaimsService } from './daily-generated-task-claims.service';
import { DailyMinecraftClientService } from './daily-minecraft-client.service';
import { DailyTaskStorageService } from './daily-task-storage.service';
import { DailyTaskUpdateEventsService } from './daily-task-update-events.service';
import { DailyTaskAssignmentService } from './daily-task-assignment.service';
import { DailiesService } from './dailies.service';

@Module({
	imports: [AuthModule, DatabaseModule, PlayersModule, ShopModule],
	controllers: [DailiesController, AdminDailiesController],
	providers: [
		DailiesService,
		DailyAdminRefreshService,
		DailyBonusClaimsService,
		DailyGeneratedTaskClaimsService,
		DailyMinecraftClientService,
		DailyTaskAssignmentService,
		DailyTaskStorageService,
		DailyTaskUpdateEventsService,
	],
	exports: [DailiesService],
})
export class DailiesModule {}
