import { Module } from '@nestjs/common'
import { KnowledgeModule } from './knowledge/knowledge.module'
import { GameplayGrpcService } from './gameplay-grpc.service'

@Module({
	imports: [KnowledgeModule],
	providers: [GameplayGrpcService],
})
export class GameplayModule { }
