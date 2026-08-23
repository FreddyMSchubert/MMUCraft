import { Global, Module } from '@nestjs/common';
import { DatabaseModule } from '../database/database.module';
import { MonitoringController } from './monitoring.controller';
import { MonitoringService } from './monitoring.service';

@Global()
@Module({
	imports: [DatabaseModule],
	controllers: [MonitoringController],
	providers: [MonitoringService],
	exports: [MonitoringService],
})
export class MonitoringModule {}
