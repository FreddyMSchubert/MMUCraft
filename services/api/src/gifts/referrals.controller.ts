import { Controller, Get, Headers, Post } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { ReferralsService } from '../database/referrals.service';

@Controller('api/referrals')
export class ReferralsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly referrals: ReferralsService,
	) {}

	@Get()
	list(@Headers('cookie') cookie: string | undefined) {
		return this.referrals.list(this.auth.requireSession(cookie).id);
	}

	@Post()
	create(@Headers('cookie') cookie: string | undefined) {
		return this.referrals.create(this.auth.requireSession(cookie).id);
	}
}
