import { Module } from '@nestjs/common'
import { AuthModule } from '../../auth/auth.module'
import { DatabaseModule } from '../../database/database.module'
import { DailiesController } from './dailies.controller'
import { DailiesService } from './dailies.service'

@Module({
	imports: [AuthModule, DatabaseModule],
	controllers: [DailiesController],
	providers: [DailiesService],
	exports: [DailiesService],
})
export class DailiesModule { }
