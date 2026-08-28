import { Body, Controller, Get, Headers, Param, Patch } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { FeatureTogglesService } from './feature-toggles.service';

@Controller('api/admin/toggles')
export class FeatureTogglesController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly toggles: FeatureTogglesService,
	) {}

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.toggles.getSnapshot();
	}

	@Patch(':key')
	set(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('key') key: string,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.toggles.set(key, body?.enabled);
	}
}
