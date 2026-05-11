import { Global, Module } from '@nestjs/common'
import { GrpcServerService } from './grpc-server.service'

@Global()
@Module({
	providers: [GrpcServerService],
	exports: [GrpcServerService],
})
export class GrpcModule { }
