import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { DatabaseModule } from '../database/database.module';
import { AnnouncementsController } from './announcements.controller';
import { AnnouncementsService } from './announcements.service';

@Module({
	imports: [AuthModule, DatabaseModule],
	controllers: [AnnouncementsController],
	providers: [AnnouncementsService],
	exports: [AnnouncementsService],
})
export class AnnouncementsModule {}
