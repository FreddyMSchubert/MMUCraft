import { Body, Controller, Get, Header, Headers, Param, Post, StreamableFile } from '@nestjs/common'
import { createReadStream } from 'node:fs'
import { AuthService } from '../../auth/auth.service'
import { ShopService } from './shop.service'

@Controller('api/shop')
export class ShopController {
	constructor(
		private readonly auth: AuthService,
		private readonly shop: ShopService,
	) { }

	@Get()
	getShop(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader)
		return this.shop.getShopForUser(user)
	}

	@Post('purchase')
	purchase(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { itemId?: unknown },
	) {
		const user = this.auth.requireSession(cookieHeader)
		return this.shop.purchaseItem(user, body?.itemId)
	}

	@Get('texture/:itemId')
	@Header('Content-Type', 'image/png')
	getTexture(@Param('itemId') itemId: string) {
		return new StreamableFile(createReadStream(this.shop.getTextureFilePath(itemId)))
	}
}
