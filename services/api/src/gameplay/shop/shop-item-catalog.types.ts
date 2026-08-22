export type ShopItemType = 'charm' | 'cosmetic' | 'generic';
export type ShopDeliveryKind = 'fake_item' | 'vanilla_item';
export type BookUnlockType = 'knowledge' | 'charm' | 'cosmetic';

export interface ShopPurchasableDefinition {
	priceDabloons: number;
	description: string;
	unlockMessage: string | null;
	unlockWeight: number;
}

export interface RawItemDefinition {
	title?: unknown;
	id?: unknown;
	modelType?: unknown;
	rarity?: unknown;
	tooltips?: unknown;
	shopPurchasable?: unknown;
	dyeable?: unknown;
	charm?: unknown;
	equippableCharm?: unknown;
	equippableCosmetic?: unknown;
}

export interface CharmLevelDefinition {
	level: number;
	abilityStatusCurrent: string;
	upgradeIngredients: string[];
}

export interface CharmDetailsDefinition {
	minLevel: number;
	maxLevel: number;
	levels: CharmLevelDefinition[];
}

export interface TextureAnimationDefinition {
	frameDelayMs: number;
	frames: number[] | null;
}

export interface ItemRenderAsset {
	animation: TextureAnimationDefinition | null;
	modelFilePath: string | null;
	modelUrl: string | null;
	textureFilePath: string | null;
	textureUrl: string | null;
}

export interface CatalogItem extends ItemRenderAsset {
	id: string;
	title: string;
	type: ShopItemType;
	modelType: string;
	rarity: string;
	priceDabloons: number;
	description: string;
	tooltips: string[];
	unlockMessage: string | null;
	unlockWeight: number;
	iconUrl: string | null;
	renderMode: 'texture' | 'model';
	animated: boolean;
	dyeable: boolean;
	deliveryKind: ShopDeliveryKind;
	deliveryItemId: string;
	bookUnlockType: BookUnlockType | null;
	charmDetails: CharmDetailsDefinition | null;
}

export interface ShopItemCatalog {
	root: string;
	mtimeMs: number;
	items: CatalogItem[];
	assets: Map<string, ItemRenderAsset>;
}
