import { Body, Controller, Get, Headers, Param, Post, Put } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { AdventService } from './advent.service';

@Controller('api/advent')
export class AdventController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly advent: AdventService,
	) {}

	@Get()
	get(@Headers('cookie') cookie: string | undefined) {
		return this.advent.getCalendar(this.auth.requireSession(cookie));
	}

	@Post(':day/claim')
	claim(@Headers('cookie') cookie: string | undefined, @Param('day') day: string) {
		return this.advent.claim(this.auth.requireSession(cookie), day);
	}
}

@Controller('api/admin/advent')
export class AdventAdminController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly advent: AdventService,
	) {}

	@Get()
	get(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireCommitteeSession(cookie);
		return this.advent.getAdmin();
	}

	@Put()
	put(
		@Headers('cookie') cookie: string | undefined,
		@Body() body: { layout?: unknown; revision?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.advent.saveLayout(body?.layout, body?.revision);
	}

	@Post(':day/preview-gift')
	previewGift(@Headers('cookie') cookie: string | undefined, @Param('day') day: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.advent.previewGift(day);
	}
}
