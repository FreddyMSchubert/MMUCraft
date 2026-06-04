import { Module } from '@nestjs/common'
import { KnowledgeModule } from './knowledge/knowledge.module'
import { GameplayGrpcService } from './gameplay-grpc.service'
import { DailiesModule } from './dailies/dailies.module'
import { ShopModule } from './shop/shop.module'

@Module({
	imports: [KnowledgeModule, DailiesModule, ShopModule],
	providers: [GameplayGrpcService],
})
export class GameplayModule { }
