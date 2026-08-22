import { Global, Module } from '@nestjs/common';
import { GrpcServerService } from './grpc-server.service';
import { MinecraftGrpcClientService } from './minecraft-grpc-client.service';

@Global()
@Module({
	providers: [GrpcServerService, MinecraftGrpcClientService],
	exports: [GrpcServerService, MinecraftGrpcClientService],
})
export class GrpcModule {}
