import { Body, Controller, Get, Headers, Param, Patch, Post, Put } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { PresentsService } from './presents.service';

@Controller('api/presents')
export class PresentsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly presents: PresentsService,
	) {}

	@Get()
	get(@Headers('cookie') cookie: string | undefined) {
		return this.presents.get(this.auth.requireSession(cookie));
	}

	@Get('preview')
	preview(@Headers('cookie') cookie: string | undefined) {
		return this.presents.preview(this.auth.requireSession(cookie));
	}

	@Patch('draft')
	saveDraft(
		@Headers('cookie') cookie: string | undefined,
		@Body() body: { recipient?: unknown; anonymous?: unknown; title?: unknown } | undefined,
	) {
		return this.presents.saveDraft(this.auth.requireSession(cookie), body ?? {});
	}

	@Post('draft/capture')
	capture(@Headers('cookie') cookie: string | undefined) {
		return this.presents.capture(this.auth.requireSession(cookie));
	}

	@Post('draft/take-out')
	takeOut(@Headers('cookie') cookie: string | undefined) {
		return this.presents.takeOut(this.auth.requireSession(cookie));
	}

	@Post('draft/send')
	send(@Headers('cookie') cookie: string | undefined) {
		return this.presents.send(this.auth.requireSession(cookie));
	}

	@Post(':id/open')
	open(@Headers('cookie') cookie: string | undefined, @Param('id') id: string) {
		return this.presents.open(this.auth.requireSession(cookie), id);
	}

	@Post(':id/claim')
	claim(@Headers('cookie') cookie: string | undefined, @Param('id') id: string) {
		return this.presents.claim(this.auth.requireSession(cookie), id);
	}
}

@Controller('api/admin/presents')
export class PresentsAdminController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly presents: PresentsService,
	) {}

	@Get()
	get(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireCommitteeSession(cookie);
		return this.presents.admin();
	}

	@Put('clock')
	clock(
		@Headers('cookie') cookie: string | undefined,
		@Body() body: { simulatedAt?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.presents.setClock(body?.simulatedAt);
	}

	@Post(':id/:action')
	action(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('action') action: string,
		@Body() body: { reason?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.presents.adminAction(id, action, body?.reason);
	}
}
