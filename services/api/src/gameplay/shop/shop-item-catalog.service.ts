import { Injectable, OnModuleInit } from '@nestjs/common';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { CachedSearchIndex } from '../../search/cached-search-index';
import { loadDrops } from '../drop-catalog';
import { findItemDefinitionFiles, itemRenderAsset } from './shop-item-asset-files';
import { parseShopItemDefinition, unlistedItemRenderAsset } from './shop-item-definition-parser';
import type {
	CatalogItem,
	ItemRenderAsset,
	RawItemDefinition,
	ShopItemCatalog,
	ShopSearchDocument,
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
	private readonly searchIndex = new CachedSearchIndex<ShopSearchDocument>({
		fields: ['content'],
		searchOptions: { combineWith: 'AND', fuzzy: 0.2, prefix: true },
	});

	onModuleInit() {
		const catalog = this.load();
		this.searchIndex.build(catalog.mtimeMs, catalog.searchDocuments);
	}

	load(): ShopItemCatalog {
		if (this.cache) return this.cache;
		const root =
			process.env.SHOP_ITEM_ROOT ??
			DEFAULT_ITEM_ROOTS.find((candidate) => existsSync(candidate)) ??
			DEFAULT_ITEM_ROOTS[0];
		if (!existsSync(root)) return (this.cache = emptyCatalog(root));

		const mtimeMs = treeMtime(root);
		const { items, assets, searchDocuments } = readCatalog(root);
		items.sort(
			(left, right) =>
				left.type.localeCompare(right.type, 'en') ||
				left.title.localeCompare(right.title, 'en'),
		);
		return (this.cache = { root, mtimeMs, items, assets, searchDocuments });
	}

	search(query: string): string[] {
		const catalog = this.load();
		return this.searchIndex.search(catalog.mtimeMs, catalog.searchDocuments, query);
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
	const drops = loadDrops();
	const items: CatalogItem[] = [];
	const assets = new Map<string, ItemRenderAsset>();
	const searchDocuments: ShopSearchDocument[] = [];
	for (const filePath of findItemDefinitionFiles(root)) {
		const json = JSON.parse(readFileSync(filePath, 'utf8')) as RawItemDefinition;
		const directory = dirname(filePath);
		const item = parseShopItemDefinition(json, directory, root, drops);
		if (item) {
			items.push(item);
			assets.set(item.id, item);
			searchDocuments.push({ id: item.id, content: JSON.stringify(json) });
		} else if (typeof json.id === 'string') {
			assets.set(json.id, unlistedItemRenderAsset(json.id, directory, root));
		}
	}
	// Enderite equipment and the two repurposed blocks have vanilla item IDs in-game.
	// Keep explicit preview assets for each variant without fake-item definitions.
	const enderiteTextures = existsSync(join(root, 'enderite-textures'))
		? join(root, 'enderite-textures')
		: join(
				root,
				'..',
				'..',
				'..',
				'respack',
				'packs',
				'general-pack',
				'assets',
				'general-pack',
				'textures',
				'item',
				'enderite',
			);
	for (const kind of [
		'helmet',
		'chestplate',
		'leggings',
		'boots',
		'sword',
		'pickaxe',
		'axe',
		'shovel',
		'hoe',
		'spear',
	]) {
		const texture = join(enderiteTextures, `${kind}.png`);
		if (existsSync(texture))
			assets.set(`enderite-${kind}`, itemRenderAsset(`enderite-${kind}`, null, texture));
	}
	const blockTextures = existsSync(join(root, 'enderite-block-textures'))
		? join(root, 'enderite-block-textures')
		: join(
				root,
				'..',
				'..',
				'..',
				'respack',
				'packs',
				'general-pack',
				'assets',
				'minecraft',
				'textures',
				'block',
			);
	for (const [id, filename] of [
		['alien-debris', 'alien_debris.png'],
		['enderite-block', 'enderite_block.png'],
	] as const) {
		const texture = join(blockTextures, filename);
		if (existsSync(texture)) assets.set(id, itemRenderAsset(id, null, texture));
	}
	return { items, assets, searchDocuments };
}

function emptyCatalog(root: string): ShopItemCatalog {
	return { root, mtimeMs: 0, items: [], assets: new Map(), searchDocuments: [] };
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
