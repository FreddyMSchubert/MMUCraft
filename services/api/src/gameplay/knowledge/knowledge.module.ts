import { Module } from '@nestjs/common'
import { AuthModule } from '../../auth/auth.module'
import { DatabaseModule } from '../../database/database.module'
import { PlayersModule } from '../../players/players.module'
import { KnowledgeController } from './knowledge.controller'
import { KnowledgeService } from './knowledge.service'

@Module({
	imports: [AuthModule, DatabaseModule, PlayersModule],
	controllers: [KnowledgeController],
	providers: [KnowledgeService],
	exports: [KnowledgeService],
})
export class KnowledgeModule { }
