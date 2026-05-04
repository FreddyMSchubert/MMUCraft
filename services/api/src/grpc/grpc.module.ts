import { Global, Module } from '@nestjs/common'
import { GrpcService } from './grpc.service'

@Global()
@Module({
	providers: [GrpcService],
	exports: [GrpcService],
})
export class GrpcModule { }
