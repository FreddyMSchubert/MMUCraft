import { Module } from '@nestjs/common'
import { KnowledgeModule } from './knowledge/knowledge.module'
import { GameplayGrpcService } from './gameplay-grpc.service'
import { DailiesModule } from './dailies/dailies.module'
import { ShopModule } from './shop/shop.module'
import { PlayersModule } from '../players/players.module'

@Module({
	imports: [KnowledgeModule, DailiesModule, ShopModule, PlayersModule],
	providers: [GameplayGrpcService],
})
export class GameplayModule { }
