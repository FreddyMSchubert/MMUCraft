import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { VelocityAdminController, VelocityInternalController } from './velocity.controller';
import { VelocityService } from './velocity.service';

@Module({
	imports: [DatabaseModule, AuthModule],
	controllers: [VelocityAdminController, VelocityInternalController],
	providers: [VelocityService],
})
export class VelocityModule {}
