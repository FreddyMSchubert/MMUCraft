import { Global, Module } from '@nestjs/common'
import { KnowledgeModule } from '../knowledge/knowledge.module'
import { GrpcService } from './grpc.service'

@Global()
@Module({
	imports: [KnowledgeModule],
	providers: [GrpcService],
	exports: [GrpcService],
})
export class GrpcModule { }
