import {
	Body,
	Controller,
	Get,
	Header,
	Headers,
	Param,
	Post,
	StreamableFile,
} from '@nestjs/common';
import { createReadStream } from 'node:fs';
import { AuthSessionService } from '../../auth/auth-session.service';
import { CharmUpgradeInput, ShopCharmInventoryService } from './shop-charm-inventory.service';
import { ShopPurchasesService } from './shop-purchases.service';

const NO_STORE_CACHE_CONTROL = 'no-store, no-cache, must-revalidate, proxy-revalidate';
const VERSIONED_ASSET_CACHE_CONTROL = 'public, max-age=31536000, immutable';

@Controller('api/shop')
export class ShopController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly purchases: ShopPurchasesService,
		private readonly charmInventory: ShopCharmInventoryService,
	) {}

	@Get()
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	@Header('Pragma', 'no-cache')
	@Header('Expires', '0')
	getShop(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.purchases.getShopForUser(user);
	}

	@Post('purchase')
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	@Header('Pragma', 'no-cache')
	@Header('Expires', '0')
	purchase(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { itemId?: string },
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.purchases.purchaseItem(user, body.itemId);
	}

	@Get('charms')
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	getCharms(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.charmInventory.getInventory(user);
	}

	@Post('charms/upgrade')
	@Header('Cache-Control', NO_STORE_CACHE_CONTROL)
	upgradeCharm(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: CharmUpgradeInput,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.charmInventory.upgrade(user, body);
	}

	@Get('texture/:itemId')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', VERSIONED_ASSET_CACHE_CONTROL)
	getTexture(@Param('itemId') itemId: string) {
		return new StreamableFile(createReadStream(this.purchases.getTextureFilePath(itemId)));
	}

	@Get('model/:itemId')
	@Header('Content-Type', 'application/json')
	@Header('Cache-Control', VERSIONED_ASSET_CACHE_CONTROL)
	getModel(@Param('itemId') itemId: string) {
		return new StreamableFile(createReadStream(this.purchases.getModelFilePath(itemId)));
	}
}
