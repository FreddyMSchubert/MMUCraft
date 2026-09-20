import { Module } from '@nestjs/common';
import { AuthModule } from '../../auth/auth.module';
import { DropAnalyticsController } from './drop-analytics.controller';
import { DropAnalyticsService } from './drop-analytics.service';

@Module({
	imports: [AuthModule],
	controllers: [DropAnalyticsController],
	providers: [DropAnalyticsService],
})
export class DropAnalyticsModule {}
