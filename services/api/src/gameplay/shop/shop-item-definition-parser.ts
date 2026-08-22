import {
	flatItemTextureFile,
	itemRenderAsset,
	modelDefinitionFile,
	modelTextureFile,
	readTextureAnimation,
	shopAssetUrl,
} from './shop-item-asset-files';
import type {
	BookUnlockType,
	CatalogItem,
	CharmDetailsDefinition,
	CharmLevelDefinition,
	RawItemDefinition,
	ShopItemType,
	ShopPurchasableDefinition,
} from './shop-item-catalog.types';

export function parseShopItemDefinition(
	json: RawItemDefinition,
	directory: string,
	root: string,
): CatalogItem | null {
	const id = typeof json.id === 'string' ? json.id : '';
	const title = typeof json.title === 'string' ? json.title : '';
	const shop = parseShopPurchasable(json.shopPurchasable);
	if (!id || !title || !shop) return null;

	const type: ShopItemType =
		json.equippableCharm && typeof json.equippableCharm === 'object'
			? 'charm'
			: json.equippableCosmetic && typeof json.equippableCosmetic === 'object'
				? 'cosmetic'
				: 'generic';
	const modelType = typeof json.modelType === 'string' ? json.modelType : 'basic';
	const modelFilePath = modelDefinitionFile(directory);
	const modelTextureFilePath = modelTextureFile(directory);
	const canRenderModel = Boolean(
		modelFilePath && modelTextureFilePath && (modelType === 'basic-3d' || type === 'cosmetic'),
	);
	const textureFilePath = canRenderModel
		? modelTextureFilePath
		: (flatItemTextureFile(id, directory, root) ?? modelTextureFilePath);
	const textureUrl = textureFilePath ? shopAssetUrl('texture', id) : null;
	const animation = textureFilePath ? readTextureAnimation(textureFilePath) : null;

	return {
		id,
		title,
		type,
		modelType,
		rarity: typeof json.rarity === 'string' ? json.rarity : 'common',
		priceDabloons: shop.priceDabloons,
		description: shop.description,
		tooltips: Array.isArray(json.tooltips)
			? json.tooltips.filter(
					(tooltip): tooltip is string =>
						typeof tooltip === 'string' && tooltip.trim().length > 0,
				)
			: [],
		unlockMessage: shop.unlockMessage,
		unlockWeight: shop.unlockWeight,
		iconUrl: textureUrl,
		renderMode: canRenderModel ? 'model' : 'texture',
		modelUrl: canRenderModel ? shopAssetUrl('model', id) : null,
		textureUrl,
		animated: Boolean(animation),
		dyeable: Boolean(json.dyeable && typeof json.dyeable === 'object'),
		animation,
		textureFilePath,
		modelFilePath: canRenderModel ? modelFilePath : null,
		deliveryKind: 'fake_item',
		deliveryItemId: id,
		bookUnlockType: bookUnlockType(id),
		charmDetails: type === 'charm' ? parseCharmDetails(json.charm) : null,
	};
}

export function unlistedItemRenderAsset(itemId: string, directory: string, root: string) {
	const modelFilePath = modelDefinitionFile(directory);
	const textureFilePath =
		modelTextureFile(directory) ?? flatItemTextureFile(itemId, directory, root);
	return itemRenderAsset(itemId, modelFilePath, textureFilePath);
}

function parseShopPurchasable(value: unknown): ShopPurchasableDefinition | null {
	if (!value || typeof value !== 'object') return null;
	const candidate = value as Partial<Record<keyof ShopPurchasableDefinition, unknown>>;
	if (
		typeof candidate.priceDabloons !== 'number' ||
		!Number.isInteger(candidate.priceDabloons) ||
		typeof candidate.description !== 'string' ||
		(candidate.unlockMessage !== undefined && typeof candidate.unlockMessage !== 'string') ||
		typeof candidate.unlockWeight !== 'number' ||
		!Number.isInteger(candidate.unlockWeight) ||
		candidate.unlockWeight < 1
	)
		return null;
	return {
		priceDabloons: candidate.priceDabloons,
		description: candidate.description,
		unlockMessage: candidate.unlockMessage ?? null,
		unlockWeight: candidate.unlockWeight,
	};
}

function parseCharmDetails(value: unknown): CharmDetailsDefinition | null {
	if (!value || typeof value !== 'object') return null;
	const candidate = value as { minLevel?: unknown; maxLevel?: unknown; levels?: unknown };
	const minLevel = Number.isInteger(candidate.minLevel) ? Number(candidate.minLevel) : 0;
	const levels = (Array.isArray(candidate.levels) ? candidate.levels : []).flatMap(
		(raw): CharmLevelDefinition[] => {
			if (!raw || typeof raw !== 'object') return [];
			const level = raw as {
				level?: unknown;
				abilityStatusCurrent?: unknown;
				upgradeIngredients?: unknown;
			};
			if (!Number.isInteger(level.level)) return [];
			return [
				{
					level: Number(level.level),
					abilityStatusCurrent:
						typeof level.abilityStatusCurrent === 'string'
							? level.abilityStatusCurrent
							: '',
					upgradeIngredients: Array.isArray(level.upgradeIngredients)
						? level.upgradeIngredients.filter(
								(ingredient): ingredient is string =>
									typeof ingredient === 'string',
							)
						: [],
				},
			];
		},
	);
	const maxLevel = Number.isInteger(candidate.maxLevel)
		? Number(candidate.maxLevel)
		: Math.max(minLevel, ...levels.map((level) => level.level));
	return { minLevel, maxLevel, levels };
}

function bookUnlockType(itemId: string): BookUnlockType | null {
	if (itemId === 'charm-knowledge-book') return 'knowledge';
	if (itemId === 'charm-magic-book') return 'charm';
	if (itemId === 'charm-fashion-book') return 'cosmetic';
	return null;
}
