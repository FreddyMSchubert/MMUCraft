import { Injectable } from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { and, eq } from 'drizzle-orm';
import { DatabaseService, shopUnlocks } from '../../database/database.service';
import { MinecraftIdentityService } from '../../database/minecraft-identity.service';
import { KnowledgeService } from '../knowledge/knowledge.service';
import {
	type CatalogItem,
	ShopItemCatalogService,
	type ShopItemType,
} from './shop-item-catalog.service';

export interface ShopUnlockAvailability {
	knowledge: boolean;
	charms: boolean;
	cosmetics: boolean;
}

interface ShopUnlockResponse {
	unlocked: boolean;
	all_unlocked: boolean;
	knowledge_id: string;
	unlocked_id: string;
	priority: number;
	topic: string;
	message: string;
}

@Injectable()
export class ShopUnlocksService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraftIdentities: MinecraftIdentityService,
		private readonly itemCatalog: ShopItemCatalogService,
		private readonly knowledge: KnowledgeService,
	) {}

	unlockNextForMinecraftPlayer(
		minecraftUuid: string,
		minecraftUsernameInput: string,
		unlockTypeInput: string,
		sourceInput: string,
	): ShopUnlockResponse {
		const unlockType = normalizeUnlockType(unlockTypeInput);
		if (!unlockType) return noUnlock('That book cannot unlock this kind of reward.');

		const minecraftUsername = minecraftUsernameInput.trim();
		if (!minecraftUsername) return noUnlock('No Minecraft username was provided.');

		const user = this.minecraftIdentities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) {
			return noUnlock('No website account is linked to this Minecraft username yet.');
		}

		const candidates = this.itemCatalog.load().items.filter((item) => item.type === unlockType);
		if (!candidates.length) {
			return {
				...noUnlock(`There are no unlockable ${unlockType}s configured yet.`),
				all_unlocked: true,
			};
		}

		const source = sourceInput.trim() || `${unlockType}_book`;
		for (let attempt = 0; attempt < 5; attempt++) {
			const picked = this.database.connection.transaction((transaction) => {
				const unlockedIds = this.unlockedItemIdsForUser(user.id, unlockType);
				const remaining = candidates.filter((item) => !unlockedIds.has(item.id));
				if (!remaining.length) return 'all-unlocked' as const;

				const chosen = pickWeightedRandomItem(remaining);
				const inserted = transaction
					.insert(shopUnlocks)
					.values({
						user_id: user.id,
						item_id: chosen.id,
						unlock_type: chosen.type,
						unlocked_at_unix_ms: Date.now(),
						source,
					})
					.onConflictDoNothing()
					.run();
				return inserted.changes === 1 ? chosen : null;
			});

			if (picked === 'all-unlocked') {
				return {
					...noUnlock(`You have already unlocked all available ${unlockType}s.`),
					all_unlocked: true,
				};
			}
			if (picked) {
				return {
					unlocked: true,
					all_unlocked: false,
					knowledge_id: picked.id,
					unlocked_id: picked.id,
					priority: picked.unlockWeight,
					topic: picked.title,
					message:
						picked.unlockMessage ??
						`You've unlocked ${picked.title}. Visit the website shop to see it.`,
				};
			}
		}
		return noUnlock('Unlock was busy. Try again.');
	}

	availabilityForMinecraftPlayer(minecraftUuid: string, minecraftUsernameInput: string) {
		const minecraftUsername = minecraftUsernameInput.trim();
		if (!minecraftUsername) return unavailableAccount('No Minecraft username was provided.');
		const user = this.minecraftIdentities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) {
			return unavailableAccount(
				'No website account is linked to this Minecraft username yet.',
			);
		}
		return { accountLinked: true, ...this.availabilityForUser(user.id), message: '' };
	}

	availabilityForUser(userId: number): ShopUnlockAvailability {
		return {
			knowledge: this.knowledge.hasRemainingForUser(userId),
			charms: this.hasRemainingForUser(userId, 'charm'),
			cosmetics: this.hasRemainingForUser(userId, 'cosmetic'),
		};
	}

	unlockedItemIdsForUser(userId: number, type?: ShopItemType): Set<string> {
		const rows = this.database.connection
			.select({ itemId: shopUnlocks.item_id })
			.from(shopUnlocks)
			.where(
				type
					? and(eq(shopUnlocks.user_id, userId), eq(shopUnlocks.unlock_type, type))
					: eq(shopUnlocks.user_id, userId),
			)
			.all();
		return new Set(rows.map((row) => row.itemId));
	}

	private hasRemainingForUser(userId: number, type: 'charm' | 'cosmetic'): boolean {
		const items = this.itemCatalog.load().items.filter((item) => item.type === type);
		if (!items.length) return false;
		const unlockedIds = this.unlockedItemIdsForUser(userId, type);
		return items.some((item) => !unlockedIds.has(item.id));
	}
}

function normalizeUnlockType(value: string): 'charm' | 'cosmetic' | null {
	const normalized = value.trim().toLowerCase();
	if (normalized === 'charm' || normalized === 'charms') return 'charm';
	if (normalized === 'cosmetic' || normalized === 'cosmetics') return 'cosmetic';
	return null;
}

function pickWeightedRandomItem(items: CatalogItem[]): CatalogItem {
	let remainingWeight = randomInt(items.reduce((total, item) => total + item.unlockWeight, 0));
	for (const item of items) {
		remainingWeight -= item.unlockWeight;
		if (remainingWeight < 0) return item;
	}
	const fallback = items.at(-1);
	if (!fallback) throw new Error('Cannot select from an empty shop catalog.');
	return fallback;
}

function noUnlock(message: string): ShopUnlockResponse {
	return {
		unlocked: false,
		all_unlocked: false,
		knowledge_id: '',
		unlocked_id: '',
		priority: 0,
		topic: '',
		message,
	};
}

function unavailableAccount(message: string) {
	return {
		accountLinked: false,
		knowledge: false,
		charms: false,
		cosmetics: false,
		message,
	};
}
