import { Injectable, OnModuleInit } from '@nestjs/common';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { findItemDefinitionFiles } from './shop-item-asset-files';
import { parseShopItemDefinition, unlistedItemRenderAsset } from './shop-item-definition-parser';
import type {
	CatalogItem,
	ItemRenderAsset,
	RawItemDefinition,
	ShopItemCatalog,
} from './shop-item-catalog.types';

export type {
	CatalogItem,
	ItemRenderAsset,
	ShopItemType,
	TextureAnimationDefinition,
} from './shop-item-catalog.types';

const DEFAULT_ITEM_ROOTS = [
	join(process.cwd(), 'content', 'items'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
	join(process.cwd(), 'minecraft', 'main', 'data', 'data', 'items'),
] as const;

@Injectable()
export class ShopItemCatalogService implements OnModuleInit {
	private cache: ShopItemCatalog | null = null;

	onModuleInit() {
		if (process.env.NODE_ENV === 'production') this.load();
	}

	load(): ShopItemCatalog {
		if (process.env.NODE_ENV === 'production' && this.cache) return this.cache;
		const root =
			process.env.SHOP_ITEM_ROOT ??
			DEFAULT_ITEM_ROOTS.find((candidate) => existsSync(candidate)) ??
			DEFAULT_ITEM_ROOTS[0];
		if (!existsSync(root)) return (this.cache = emptyCatalog(root));

		const mtimeMs = treeMtime(root);
		if (this.cache?.root === root && this.cache.mtimeMs === mtimeMs) return this.cache;

		const { items, assets } = readCatalog(root);
		items.sort(
			(left, right) =>
				left.type.localeCompare(right.type, 'en') ||
				left.title.localeCompare(right.title, 'en'),
		);
		return (this.cache = { root, mtimeMs, items, assets });
	}

	itemAsset(itemId: string): ItemRenderAsset | null {
		return this.load().assets.get(itemId) ?? null;
	}

	gameItemAsset(itemId: string): ItemRenderAsset | null {
		return itemId.startsWith('mainmod:')
			? this.itemAsset(itemId.slice('mainmod:'.length))
			: null;
	}
}

function readCatalog(root: string) {
	const items: CatalogItem[] = [];
	const assets = new Map<string, ItemRenderAsset>();
	for (const filePath of findItemDefinitionFiles(root)) {
		const json = JSON.parse(readFileSync(filePath, 'utf8')) as RawItemDefinition;
		const directory = dirname(filePath);
		const item = parseShopItemDefinition(json, directory, root);
		if (item) {
			items.push(item);
			assets.set(item.id, item);
		} else if (typeof json.id === 'string') {
			assets.set(json.id, unlistedItemRenderAsset(json.id, directory, root));
		}
	}
	return { items, assets };
}

function emptyCatalog(root: string): ShopItemCatalog {
	return { root, mtimeMs: 0, items: [], assets: new Map() };
}

function treeMtime(path: string): number {
	const stats = statSync(path);
	if (!stats.isDirectory()) return stats.mtimeMs;
	return readdirSync(path, { withFileTypes: true }).reduce(
		(mtime, child) =>
			child.name.startsWith('.') ? mtime : Math.max(mtime, treeMtime(join(path, child.name))),
		stats.mtimeMs,
	);
}
