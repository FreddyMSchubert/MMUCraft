import { Module } from '@nestjs/common'
import { AuthService } from '../auth/auth.service'
import { DatabaseModule } from '../database/database.module'
import { KnowledgeController } from './knowledge.controller'
import { KnowledgeService } from './knowledge.service'

@Module({
	imports: [DatabaseModule],
	controllers: [KnowledgeController],
	providers: [KnowledgeService, AuthService],
	exports: [KnowledgeService],
})
export class KnowledgeModule { }