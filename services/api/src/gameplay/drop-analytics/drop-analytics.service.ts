import { Injectable } from '@nestjs/common';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { findItemDefinitionFiles } from '../shop/shop-item-asset-files';

const defaultContentRoot = join(process.cwd(), 'content');
const contentRoots = [
	defaultContentRoot,
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data'),
];
const defaultKnowledgeRoot = join(process.cwd(), 'content', 'knowledge');
const knowledgeRoots = [
	defaultKnowledgeRoot,
	join(process.cwd(), '..', 'web', 'public', 'knowledge'),
];
const defaultItemRoot = join(process.cwd(), 'content', 'items');
const itemRoots = [
	defaultItemRoot,
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
];

function contentRoot() {
	return contentRoots.find((root) => existsSync(join(root, 'drops.json'))) ?? defaultContentRoot;
}

function knowledgeRoot() {
	return (
		process.env.KNOWLEDGE_ROOT ??
		knowledgeRoots.find((root) => existsSync(root)) ??
		defaultKnowledgeRoot
	);
}

function itemRoot() {
	return (
		process.env.SHOP_ITEM_ROOT ?? itemRoots.find((root) => existsSync(root)) ?? defaultItemRoot
	);
}

function markdownFiles(directory: string): string[] {
	return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const path = join(directory, entry.name);
		return entry.isDirectory()
			? markdownFiles(path)
			: entry.isFile() && entry.name.endsWith('.md')
				? [path]
				: [];
	});
}

function metadataField(metadata: string, key: string) {
	return new RegExp(`^${key}:\\s*(.+)$`, 'm').exec(metadata)?.[1]?.trim() ?? null;
}

@Injectable()
export class DropAnalyticsService {
	load() {
		const drops = JSON.parse(readFileSync(join(contentRoot(), 'drops.json'), 'utf8')) as {
			id: string;
			name: string;
			date: string;
			description: string;
			weekStart: string;
			lastYear: string;
			availableDayOne?: string;
			lastYearNotes?: string;
			surprisingSaturday?: string;
			releaseNotes?: string;
			screenshot?: string;
			notes?: string;
		}[];
		const dropIds = new Set(drops.map((drop) => drop.id));
		if (dropIds.size !== drops.length) throw new Error('Duplicate drop id');
		const items = findItemDefinitionFiles(itemRoot()).flatMap((path) => {
			const item = JSON.parse(readFileSync(path, 'utf8')) as {
				id?: string;
				title?: string;
				drop?: string;
				equippableCosmetic?: object;
				decoBlock?: object;
				shopPurchasable?: { gameplayToggle?: string; membersOnly?: boolean };
			};
			const type = item.equippableCosmetic ? 'cosmetic' : item.decoBlock ? 'decoblock' : null;
			if (!type || !item.id || !item.title) return [];
			if (
				item.drop &&
				item.shopPurchasable?.gameplayToggle &&
				item.drop !== item.shopPurchasable.gameplayToggle
			)
				throw new Error(`Conflicting item drop: ${path}`);
			return [
				{
					id: item.id,
					name: item.title,
					type,
					drop: item.drop ?? item.shopPurchasable?.gameplayToggle ?? null,
					shopPurchasable: Boolean(item.shopPurchasable),
					membersOnly: Boolean(item.shopPurchasable?.membersOnly),
				},
			];
		});
		const knowledge = markdownFiles(knowledgeRoot()).map((path) => {
			const source = readFileSync(path, 'utf8');
			const metadata = /^====\r?\n([\s\S]*?)\r?\n====/.exec(source)?.[1];
			if (!metadata) throw new Error(`Missing knowledge metadata: ${path}`);
			const id = metadataField(metadata, 'id');
			const name = metadataField(metadata, 'sidebarTitle');
			if (!id || !name) throw new Error(`Incomplete knowledge metadata: ${path}`);
			return {
				id,
				name,
				drop: metadataField(metadata, 'drop') ?? metadataField(metadata, 'gameplayToggle'),
				public: metadataField(metadata, 'unlockOrder') === 'public',
			};
		});
		for (const entry of [...items, ...knowledge]) {
			if (entry.drop !== null && !dropIds.has(entry.drop))
				throw new Error(`Unknown drop ${entry.drop} for ${entry.id}`);
		}
		return { drops, items, knowledge };
	}
}
