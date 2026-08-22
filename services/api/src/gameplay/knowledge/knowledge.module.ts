import { Module } from '@nestjs/common';
import { AuthModule } from '../../auth/auth.module';
import { DatabaseModule } from '../../database/database.module';
import { PlayersModule } from '../../players/players.module';
import { KnowledgeController } from './knowledge.controller';
import { KnowledgeDocumentCatalogService } from './knowledge-document-catalog.service';
import { KnowledgeService } from './knowledge.service';

@Module({
	imports: [AuthModule, DatabaseModule, PlayersModule],
	controllers: [KnowledgeController],
	providers: [KnowledgeDocumentCatalogService, KnowledgeService],
	exports: [KnowledgeService],
})
export class KnowledgeModule {}
