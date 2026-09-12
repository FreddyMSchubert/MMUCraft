import { Module } from '@nestjs/common';
import { DatabaseModule } from '../database/database.module';
import { LaunchController } from './launch.controller';
import { LaunchSettingsService } from './launch-settings.service';

@Module({
	imports: [DatabaseModule],
	controllers: [LaunchController],
	providers: [LaunchSettingsService],
	exports: [LaunchSettingsService],
})
export class LaunchModule {}
