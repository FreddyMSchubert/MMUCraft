import { Module } from '@nestjs/common'
import { AuthModule } from '../auth/auth.module'
import { DatabaseModule } from '../database/database.module'
import { PlayersModule } from '../players/players.module'
import { AdminController } from './admin.controller'
import { GiftsController } from './gifts.controller'
import { GiftsService } from './gifts.service'
import { CountdownsController } from './countdowns.controller'
import { CountdownsService } from './countdowns.service'

@Module({
	imports: [AuthModule, DatabaseModule, PlayersModule],
	controllers: [AdminController, CountdownsController, GiftsController],
	providers: [CountdownsService, GiftsService],
})
export class GiftsModule { }
