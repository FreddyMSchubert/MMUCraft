import { Module } from '@nestjs/common'
import { KnowledgeModule } from './knowledge/knowledge.module'
import { GameplayGrpcService } from './gameplay-grpc.service'
import { DailiesModule } from './dailies/dailies.module'

@Module({
	imports: [KnowledgeModule, DailiesModule],
	providers: [GameplayGrpcService],
})
export class GameplayModule { }
