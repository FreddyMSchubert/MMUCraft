import { Controller, Get, Header, Headers } from '@nestjs/common';
import { AuthSessionService } from '../../auth/auth-session.service';
import { DropAnalyticsService } from './drop-analytics.service';

@Controller('api/admin/drops')
export class DropAnalyticsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly analytics: DropAnalyticsService,
	) {}

	@Get()
	@Header('Cache-Control', 'no-store')
	list(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.analytics.load();
	}
}
