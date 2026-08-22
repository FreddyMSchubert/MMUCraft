import { Module } from '@nestjs/common';
import { AuthModule } from '../../auth/auth.module';
import { DatabaseModule } from '../../database/database.module';
import { GrpcModule } from '../../grpc/grpc.module';
import { KnowledgeModule } from '../knowledge/knowledge.module';
import { ShopController } from './shop.controller';
import { ShopService } from './shop.service';

@Module({
	imports: [AuthModule, DatabaseModule, GrpcModule, KnowledgeModule],
	controllers: [ShopController],
	providers: [ShopService],
	exports: [ShopService],
})
export class ShopModule {}
