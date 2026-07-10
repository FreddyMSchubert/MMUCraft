import { Body, Controller, Headers, Post } from '@nestjs/common'
import { AuthService } from '../auth/auth.service'
import { GiftsService } from './gifts.service'

@Controller('api/gift-codes')
export class GiftsController {
	constructor(
		private readonly auth: AuthService,
		private readonly gifts: GiftsService,
	) { }

	@Post('redeem')
	redeem(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { code?: unknown } | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader)
		return this.gifts.redeem(user, body?.code)
	}
}
