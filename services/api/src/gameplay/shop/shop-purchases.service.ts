import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, users } from '../../database/database.service';
import { MinecraftGrpcClientService } from '../../grpc/minecraft-grpc-client.service';
import {
	currentShopDealDate,
	dailyDealDiscountPercent,
	dailyDealItemIds,
	dailyDealMessage,
	discountedShopPrice,
	isBritishAnniversary,
	isShoppingSunday,
	shopDiscountPercent,
} from './shop-daily-deals';
import { type CatalogItem, ShopItemCatalogService } from './shop-item-catalog.service';
import { type ShopUnlockAvailability, ShopUnlocksService } from './shop-unlocks.service';

interface PurchaseShopItemResponse {
	purchased: boolean;
	online: boolean;
	balance_dabloons: number;
	message: string;
}

@Injectable()
export class ShopPurchasesService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly itemCatalog: ShopItemCatalogService,
		private readonly unlocks: ShopUnlocksService,
	) {}

	getShopForUser(user: AuthenticatedUser) {
		const items = this.itemCatalog.load().items;
		const unlockedIds = this.unlocks.unlockedItemIdsForUser(user.id);
		const availability = this.unlocks.availabilityForUser(user.id);
		const dealDate = currentShopDealDate();
		const dailyDealIds = dailyDealItemIds(items, dealDate);
		const signupAnniversary = this.isSignupAnniversary(user.id);

		return {
			availability,
			dealDate,
			shoppingSunday: isShoppingSunday(dealDate),
			items: items
				.filter((item) => isVisibleInShop(item, unlockedIds))
				.map((item) => {
					const dailyDiscount = dailyDealIds.has(item.id)
						? dailyDealDiscountPercent(item.id, dealDate)
						: 0;
					const discountPercent = shopDiscountPercent(
						item.id,
						signupAnniversary,
						dailyDiscount,
					);
					return {
						id: item.id,
						title: item.title,
						type: item.type,
						modelType: item.modelType,
						rarity: item.rarity,
						priceDabloons: item.priceDabloons,
						originalPriceDabloons: item.priceDabloons,
						isDailyDeal: discountPercent > 0,
						discountPercent,
						dealMessage: discountPercent > 0 ? dailyDealMessage(item, dealDate) : null,
						discountedPriceDabloons: discountedShopPrice(
							item.priceDabloons,
							discountPercent,
						),
						description: item.description,
						tooltips: item.tooltips,
						unlockMessage: item.unlockMessage,
						unlockWeight: item.unlockWeight,
						iconUrl: item.iconUrl,
						renderMode: item.renderMode,
						modelUrl: item.modelUrl,
						textureUrl: item.textureUrl,
						animated: item.animated,
						dyeable: item.dyeable,
						animation: item.animation,
						charmDetails: item.charmDetails,
						unlocked: isUnlocked(item, unlockedIds),
						available: isAvailableForPurchase(user.id, item, availability, unlockedIds),
					};
				}),
		};
	}

	searchShopForUser(user: AuthenticatedUser, queryInput: string | undefined) {
		const query = queryInput?.trim() ?? '';
		if (query.length > 100) throw new BadRequestException('Search query is too long.');
		if (!query) return { query, itemIds: [] };

		const catalog = this.itemCatalog.load();
		const unlockedIds = this.unlocks.unlockedItemIdsForUser(user.id);
		const visibleIds = new Set(
			catalog.items
				.filter((item) => isVisibleInShop(item, unlockedIds))
				.map((item) => item.id),
		);
		return {
			query,
			itemIds: this.itemCatalog.search(query).filter((id) => visibleIds.has(id)),
		};
	}

	async purchaseItem(user: AuthenticatedUser, itemIdInput: string | undefined) {
		const itemId = typeof itemIdInput === 'string' ? itemIdInput.trim() : '';
		if (!itemId) throw new BadRequestException('No shop item was selected.');

		const item = this.itemCatalog.load().items.find((candidate) => candidate.id === itemId);
		if (!item) throw new NotFoundException('Shop item not found.');

		const availability = this.unlocks.availabilityForUser(user.id);
		const unlockedIds = this.unlocks.unlockedItemIdsForUser(user.id);
		if (!isAvailableForPurchase(user.id, item, availability, unlockedIds)) {
			throw new BadRequestException(unavailablePurchaseMessage(item));
		}

		const dealDate = currentShopDealDate();
		const dailyDiscount = dailyDealItemIds(this.itemCatalog.load().items, dealDate).has(item.id)
			? dailyDealDiscountPercent(item.id, dealDate)
			: 0;
		const discountPercent = shopDiscountPercent(
			item.id,
			this.isSignupAnniversary(user.id),
			dailyDiscount,
		);
		const price = discountedShopPrice(item.priceDabloons, discountPercent);

		let purchase: PurchaseShopItemResponse;
		try {
			purchase = await this.minecraft.gameplay<PurchaseShopItemResponse>('PurchaseShopItem', {
				minecraft_username: user.minecraftUsername,
				item_id: item.deliveryItemId,
				price_dabloons: price,
				delivery_kind: item.deliveryKind,
				unix_ms: Date.now(),
				display_name: item.title,
				item_type: item.type === 'generic' ? 'Item' : titleCase(item.type),
				rarity: item.rarity,
			});
		} catch {
			throw new BadRequestException(
				'Join the Minecraft server, then try this purchase again while you are online.',
			);
		}

		if (!purchase.purchased) {
			throw new BadRequestException(
				purchase.message ||
					(purchase.online
						? 'Purchase failed.'
						: 'Join the Minecraft server, then try this purchase again while you are online.'),
			);
		}
		return {
			purchased: true,
			itemId: item.id,
			online: purchase.online,
			balanceDabloons: purchase.balance_dabloons,
			message: purchase.message || `${item.title} purchased.`,
		};
	}

	getTextureFilePath(itemId: string): string {
		const path = this.itemCatalog.itemAsset(itemId.trim())?.textureFilePath;
		if (!path) throw new NotFoundException('Shop item texture not found.');
		return path;
	}

	getModelFilePath(itemId: string): string {
		const path = this.itemCatalog.itemAsset(itemId.trim())?.modelFilePath;
		if (!path) throw new NotFoundException('Shop item model not found.');
		return path;
	}

	private isSignupAnniversary(userId: number): boolean {
		const createdAt = this.database.connection
			.select({ value: users.created_at_unix_ms })
			.from(users)
			.where(eq(users.id, userId))
			.get()?.value;
		return createdAt !== undefined && isBritishAnniversary(createdAt, Date.now());
	}
}

function isAvailableForPurchase(
	userId: number,
	item: CatalogItem,
	availability: ShopUnlockAvailability,
	unlockedIds: Set<string>,
): boolean {
	if (item.type === 'charm' || item.type === 'cosmetic') return unlockedIds.has(item.id);
	if (item.bookUnlockType === 'knowledge') return availability.knowledge;
	if (item.bookUnlockType === 'charm') return availability.charms;
	if (item.bookUnlockType === 'cosmetic') return availability.cosmetics;
	return userId > 0;
}

function isVisibleInShop(item: CatalogItem, unlockedIds: Set<string>): boolean {
	return item.type !== 'charm' && item.type !== 'cosmetic' ? true : unlockedIds.has(item.id);
}

function isUnlocked(item: CatalogItem, unlockedIds: Set<string>): boolean {
	return isVisibleInShop(item, unlockedIds);
}

function unavailablePurchaseMessage(item: CatalogItem): string {
	if (item.type === 'charm') return 'Unlock this charm with a magic book before buying it.';
	if (item.type === 'cosmetic')
		return 'Unlock this cosmetic with a fashion book before buying it.';
	if (item.bookUnlockType) {
		return `You have already unlocked all available ${item.bookUnlockType === 'knowledge' ? 'knowledge' : `${item.bookUnlockType}s`}.`;
	}
	return 'This item is not available right now.';
}

function titleCase(value: string): string {
	return value ? value.charAt(0).toUpperCase() + value.slice(1) : value;
}
