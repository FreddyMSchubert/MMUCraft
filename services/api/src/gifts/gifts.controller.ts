import { Body, Controller, Headers, Post } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { GiftCodeRedemptionService } from './gift-code-redemption.service';

@Controller('api/gift-codes')
export class GiftsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly giftCodeRedemption: GiftCodeRedemptionService,
	) {}

	@Post('redeem')
	redeem(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { code?: string } | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.giftCodeRedemption.redeem(user, body?.code);
	}
}
