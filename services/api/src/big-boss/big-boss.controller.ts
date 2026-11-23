import { Body, Controller, Get, Headers, Param, Post } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { BigBossService } from './big-boss.service';

@Controller('api/big-boss')
export class BigBossController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly boss: BigBossService,
	) {}

	@Get()
	get(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireSession(cookie);
		return this.boss.publicState();
	}

	@Get('holder')
	holder(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireSession(cookie);
		return this.boss.holderName();
	}

	@Get('admin/:username/warnings')
	warnings(@Headers('cookie') cookie: string | undefined, @Param('username') username: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.boss.adminWarnings(username);
	}

	@Post('admin/assign')
	assign(
		@Headers('cookie') cookie: string | undefined,
		@Body() body: { minecraftUsername?: string | null; reason?: string } | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookie);
		return this.boss.setByAdmin(admin, body?.minecraftUsername ?? null, body?.reason ?? '');
	}
}
