import { Body, Controller, Get, Headers, Param, Patch, Post } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { AnnouncementInput, AnnouncementsService } from './announcements.service';

@Controller('api/admin/announcements')
export class AnnouncementsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly announcements: AnnouncementsService,
	) {}

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.announcements.listOutstanding();
	}

	@Post()
	create(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: AnnouncementInput | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.announcements.create(body ?? {});
	}

	@Patch(':id/order')
	move(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: { direction?: 'up' | 'down' } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.announcements.move(id, body?.direction);
	}
}
