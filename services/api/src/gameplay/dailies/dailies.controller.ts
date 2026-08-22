import { Controller, Get, Headers, Param, Post, Sse } from '@nestjs/common';
import { AuthSessionService } from '../../auth/auth-session.service';
import { DailiesService } from './dailies.service';

@Controller('api/dailies')
export class DailiesController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly dailies: DailiesService,
	) {}

	@Get()
	getDailies(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.dailies.getStatus(user);
	}

	@Post('login-bonus/claim')
	claimLoginBonus(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.dailies.claimLoginBonus(user);
	}

	@Post('advancement-bonus/claim')
	claimAdvancementBonus(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.dailies.claimAdvancementBonus(user);
	}

	@Post('completion/claim')
	claimDailyCompletion(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.dailies.claimDailyCompletion(user);
	}

	@Post('tasks/:taskId/claim')
	claimTask(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('taskId') taskId: string,
	) {
		return this.dailies.claimTask(this.auth.requireSession(cookieHeader), taskId);
	}

	@Sse('events')
	events(@Headers('cookie') cookieHeader: string | undefined) {
		return this.dailies.events(this.auth.requireSession(cookieHeader).id);
	}
}

@Controller('api/admin/dailies')
export class AdminDailiesController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly dailies: DailiesService,
	) {}

	@Post(':userId/refresh')
	refresh(@Headers('cookie') cookieHeader: string | undefined, @Param('userId') userId: string) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.dailies.refreshForAdmin(userId);
	}
}
