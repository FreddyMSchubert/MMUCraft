import { Body, Controller, Get, Header, Headers, Param, Post, StreamableFile } from '@nestjs/common'
import { createReadStream } from 'node:fs'
import { AuthService } from '../../auth/auth.service'
import { ShopService } from './shop.service'

const NO_STORE_CACHE_CONTROL = 'no-store, no-cache, must-revalidate, proxy-revalidate'
const VERSIONED_ASSET_CACHE_CONTROL = 'public, max-age=31536000, immutable'

@Controller('api/shop')
export class ShopController {
	constructor(
		private readonly auth: AuthService,
		private readonly shop: ShopService,
	) { }

	@Get()
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	@Header('Pragma', 'no-cache')
	@Header('Expires', '0')
	getShop(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader)
		return this.shop.getShopForUser(user)
	}

	@Post('purchase')
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	@Header('Pragma', 'no-cache')
	@Header('Expires', '0')
	purchase(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { itemId?: unknown },
	) {
		const user = this.auth.requireSession(cookieHeader)
		return this.shop.purchaseItem(user, body?.itemId)
	}

	@Get('texture/:itemId')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', VERSIONED_ASSET_CACHE_CONTROL)
	getTexture(@Param('itemId') itemId: string) {
		return new StreamableFile(createReadStream(this.shop.getTextureFilePath(itemId)))
	}

	@Get('model/:itemId')
	@Header('Content-Type', 'application/json')
	@Header('Cache-Control', VERSIONED_ASSET_CACHE_CONTROL)
	getModel(@Param('itemId') itemId: string) {
		return new StreamableFile(createReadStream(this.shop.getModelFilePath(itemId)))
	}
}
