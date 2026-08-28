import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { FeatureTogglesController } from './feature-toggles.controller';
import { FeatureTogglesService } from './feature-toggles.service';

@Module({
	imports: [AuthModule, DatabaseModule],
	controllers: [FeatureTogglesController],
	providers: [FeatureTogglesService],
	exports: [FeatureTogglesService],
})
export class FeatureTogglesModule {}
