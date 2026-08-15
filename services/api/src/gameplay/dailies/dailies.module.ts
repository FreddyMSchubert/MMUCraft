import { Module } from '@nestjs/common'
import { AuthModule } from '../../auth/auth.module'
import { DatabaseModule } from '../../database/database.module'
import { PlayersModule } from '../../players/players.module'
import { ShopModule } from '../shop/shop.module'
import { AdminDailiesController, DailiesController } from './dailies.controller'
import { DailiesService } from './dailies.service'

@Module({
	imports: [AuthModule, DatabaseModule, PlayersModule, ShopModule],
	controllers: [DailiesController, AdminDailiesController],
	providers: [DailiesService],
	exports: [DailiesService],
})
export class DailiesModule { }
