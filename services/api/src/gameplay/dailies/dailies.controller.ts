import { Controller, Get, Headers, Post } from '@nestjs/common'
import { AuthService } from '../../auth/auth.service'
import { DailiesService } from './dailies.service'

@Controller('api/dailies')
export class DailiesController {
	constructor(
		private readonly auth: AuthService,
		private readonly dailies: DailiesService,
	) { }

	@Get()
	getDailies(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader)
		return this.dailies.getStatus(user)
	}

	@Post('login-bonus/claim')
	claimLoginBonus(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader)
		return this.dailies.claimLoginBonus(user)
	}
}
