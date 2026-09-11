import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { and, count, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, limitedShopPurchases, users } from '../../database/database.service';
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

const KNOWLEDGE_BOOK_ID = 'charm-knowledge-book';
const KNOWLEDGE_BOOK_DAILY_LIMIT = 3;
const FASHION_BOOK_ID = 'charm-fashion-book';
const FASHION_BOOK_DAILY_LIMIT = 1;
const JOKE_BOOK_ID = 'charm-joke-book';
const JOKE_BOOK_DAILY_LIMIT = 3;

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
		const limitedPurchaseCounts = this.limitedPurchaseCounts(user.id, dealDate);
		const dailyDealIds = dailyDealItemIds(items, dealDate);
		const signupAnniversary = this.isSignupAnniversary(user.id);

		return {
			isMember: user.isMember,
			availability,
			dealDate,
			shoppingSunday: isShoppingSunday(dealDate),
			items: items
				.filter((item) => isVisibleInShop(item, unlockedIds))
				.map((item) => {
					const limitedPurchaseCount = limitedPurchaseCounts.get(item.id) ?? 0;
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
						decoBlock: item.decoBlock,
						membersOnly: item.membersOnly,
						membershipLocked: item.membersOnly && !user.isMember,
						animation: item.animation,
						charmDetails: item.charmDetails,
						unlocked: isUnlocked(item, unlockedIds),
						dailyLimitReached: hasReachedLimitedPurchaseDailyLimit(
							item.id,
							limitedPurchaseCount,
						),
						available: isAvailableForPurchase(
							user,
							item,
							availability,
							unlockedIds,
							limitedPurchaseCount,
						),
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
		const dealDate = currentShopDealDate();
		const limitedPurchaseCount =
			this.limitedPurchaseCounts(user.id, dealDate).get(item.id) ?? 0;
		if (!isAvailableForPurchase(user, item, availability, unlockedIds, limitedPurchaseCount)) {
			throw new BadRequestException(unavailablePurchaseMessage(user, item));
		}

		const dailyDiscount = dailyDealItemIds(this.itemCatalog.load().items, dealDate).has(item.id)
			? dailyDealDiscountPercent(item.id, dealDate)
			: 0;
		const discountPercent = shopDiscountPercent(
			item.id,
			this.isSignupAnniversary(user.id),
			dailyDiscount,
		);
		const price = discountedShopPrice(item.priceDabloons, discountPercent);
		const reservedSlot = this.reserveLimitedPurchase(user.id, item, dealDate);

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
			this.releaseLimitedPurchase(user.id, item, dealDate, reservedSlot);
			throw new BadRequestException(
				'Join the Minecraft server, then try this purchase again while you are online.',
			);
		}

		if (!purchase.purchased) {
			this.releaseLimitedPurchase(user.id, item, dealDate, reservedSlot);
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

	private limitedPurchaseCounts(userId: number, periodKey: string): Map<string, number> {
		return new Map(
			this.database.connection
				.select({ itemId: limitedShopPurchases.item_id, count: count() })
				.from(limitedShopPurchases)
				.where(
					and(
						eq(limitedShopPurchases.user_id, userId),
						eq(limitedShopPurchases.period_key, periodKey),
					),
				)
				.groupBy(limitedShopPurchases.item_id)
				.all()
				.map((row) => [row.itemId, row.count]),
		);
	}

	private reserveLimitedPurchase(
		userId: number,
		item: CatalogItem,
		periodKey: string,
	): number | null {
		const dailyLimit = limitedPurchaseDailyLimit(item.id);
		if (dailyLimit === null) return null;

		for (let slot = 1; slot <= dailyLimit; slot += 1) {
			const inserted = this.database.connection
				.insert(limitedShopPurchases)
				.values({
					user_id: userId,
					item_id: item.id,
					period_key: periodKey,
					slot,
					purchased_at_unix_ms: Date.now(),
				})
				.onConflictDoNothing()
				.run();
			if (inserted.changes === 1) return slot;
		}

		throw new BadRequestException('This item is not available right now.');
	}

	private releaseLimitedPurchase(
		userId: number,
		item: CatalogItem,
		periodKey: string,
		slot: number | null,
	) {
		if (slot === null) return;
		this.database.connection
			.delete(limitedShopPurchases)
			.where(
				and(
					eq(limitedShopPurchases.user_id, userId),
					eq(limitedShopPurchases.item_id, item.id),
					eq(limitedShopPurchases.period_key, periodKey),
					eq(limitedShopPurchases.slot, slot),
				),
			)
			.run();
	}
}

function isAvailableForPurchase(
	user: AuthenticatedUser,
	item: CatalogItem,
	availability: ShopUnlockAvailability,
	unlockedIds: Set<string>,
	limitedPurchaseCount = 0,
): boolean {
	if (item.membersOnly && !user.isMember) return false;
	const dailyLimit = limitedPurchaseDailyLimit(item.id);
	if (dailyLimit !== null && limitedPurchaseCount >= dailyLimit) return false;
	if (item.type === 'charm' || item.type === 'cosmetic') return unlockedIds.has(item.id);
	if (item.bookUnlockType === 'knowledge') return availability.knowledge;
	if (item.bookUnlockType === 'charm') return availability.charms;
	if (item.bookUnlockType === 'cosmetic') return availability.cosmetics;
	return user.id > 0;
}

function limitedPurchaseDailyLimit(itemId: string): number | null {
	if (itemId === KNOWLEDGE_BOOK_ID) return KNOWLEDGE_BOOK_DAILY_LIMIT;
	if (itemId === FASHION_BOOK_ID) return FASHION_BOOK_DAILY_LIMIT;
	if (itemId === JOKE_BOOK_ID) return JOKE_BOOK_DAILY_LIMIT;
	return null;
}

function hasReachedLimitedPurchaseDailyLimit(itemId: string, purchaseCount: number): boolean {
	const dailyLimit = limitedPurchaseDailyLimit(itemId);
	return dailyLimit !== null && purchaseCount >= dailyLimit;
}

function isVisibleInShop(item: CatalogItem, unlockedIds: Set<string>): boolean {
	return item.type !== 'charm' && item.type !== 'cosmetic' ? true : unlockedIds.has(item.id);
}

function isUnlocked(item: CatalogItem, unlockedIds: Set<string>): boolean {
	return isVisibleInShop(item, unlockedIds);
}

function unavailablePurchaseMessage(user: AuthenticatedUser, item: CatalogItem): string {
	if (item.membersOnly && !user.isMember) return 'This item is for members only.';
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
