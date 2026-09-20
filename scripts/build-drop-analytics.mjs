import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = new URL('..', import.meta.url).pathname.replace(/^\/(?=[A-Za-z]:)/, '');
const read = (path) => readFileSync(join(root, path), 'utf8');
const drops = JSON.parse(read('minecraft/main/data/drops.json'));
const ids = new Set(drops.map((drop) => drop.id));
if (ids.size !== drops.length) throw new Error('Duplicate drop id');
for (const drop of drops) {
	if (!drop.name || !/^2026-\d\d-\d\d$/.test(drop.date) || !drop.description) {
		throw new Error(`Incomplete drop metadata: ${drop.id}`);
	}
}
function walk(directory, suffix) {
	return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const path = join(directory, entry.name);
		return entry.isDirectory() ? walk(path, suffix) : entry.name.endsWith(suffix) ? [path] : [];
	});
}
function checkDrop(drop, path) {
	if (drop !== null && !ids.has(drop)) throw new Error(`Unknown drop ${drop} in ${path}`);
	return drop;
}
const itemRoot = join(root, 'minecraft/main/data/data/items');
const items = walk(itemRoot, 'item.json')
	.map((path) => {
		const item = JSON.parse(readFileSync(path, 'utf8'));
		const type = item.equippableCosmetic ? 'cosmetic' : item.decoBlock ? 'decoblock' : null;
		if (!type) return null;
		const shopDrop = item.shopPurchasable?.gameplayToggle ?? null;
		if (item.drop && shopDrop && item.drop !== shopDrop)
			throw new Error(`Conflicting drop in ${path}`);
		return {
			id: item.id,
			name: item.title,
			type,
			drop: checkDrop(item.drop ?? shopDrop, path),
			shopPurchasable: Boolean(item.shopPurchasable),
			membersOnly: Boolean(item.shopPurchasable?.membersOnly),
			source: relative(root, path).replaceAll('\\', '/'),
		};
	})
	.filter(Boolean);
const knowledgeRoot = join(root, 'services/web/public/knowledge');
const knowledge = walk(knowledgeRoot, '.md').map((path) => {
	const source = readFileSync(path, 'utf8');
	const metadata = /^====\r?\n([\s\S]*?)\r?\n====/.exec(source)?.[1];
	if (!metadata) throw new Error(`Missing knowledge metadata: ${path}`);
	const field = (key) => new RegExp(`^${key}:\\s*(.+)$`, 'm').exec(metadata)?.[1]?.trim() ?? null;
	return {
		id: field('id'),
		name: field('sidebarTitle'),
		type: 'knowledge',
		drop: checkDrop(field('drop') ?? field('gameplayToggle'), path),
		public: field('unlockOrder') === 'public',
		source: relative(root, path).replaceAll('\\', '/'),
	};
});
const result = { drops, items, knowledge };
const output = JSON.stringify(result, null, '\t') + '\n';
const destination = join(root, 'services/web/src/data/drop-analytics.json');
if (process.argv.includes('--check')) {
	if (readFileSync(destination, 'utf8') !== output)
		throw new Error('Drop analytics is stale. Run npm run drops:build.');
} else {
	writeFileSync(destination, output);
	console.log(
		`Built drop analytics: ${items.length} items, ${knowledge.length} knowledge pages.`,
	);
}
