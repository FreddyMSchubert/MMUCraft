import MiniSearch, { type Options } from 'minisearch';

const CACHE_MS = 60_000;
const CACHE_SIZE = 100;

export class CachedSearchIndex<Document extends { id: string }> {
	private index: { version: number; value: MiniSearch<Document> } | null = null;
	private readonly cache = new Map<string, { expiresAt: number; ids: string[] }>();

	constructor(
		private readonly options: Options<Document>,
		private readonly resultLimit = Infinity,
	) {}

	build(version: number, documents: Document[]): MiniSearch<Document> {
		if (this.index?.version !== version) {
			const value = new MiniSearch<Document>(this.options);
			value.addAll(documents);
			this.index = { version, value };
			this.cache.clear();
		}
		return this.index.value;
	}

	search(version: number, documents: Document[], query: string): string[] {
		const index = this.build(version, documents);

		const key = query.toLocaleLowerCase('en');
		const cached = this.cache.get(key);
		if (cached?.expiresAt && cached.expiresAt > Date.now()) return cached.ids;
		if (cached) this.cache.delete(key);

		const ids = index
			.search(query)
			.slice(0, this.resultLimit)
			.map((result) => String(result.id));
		if (this.cache.size >= CACHE_SIZE) {
			const oldestKey = this.cache.keys().next().value;
			if (oldestKey !== undefined) this.cache.delete(oldestKey);
		}
		this.cache.set(key, { expiresAt: Date.now() + CACHE_MS, ids });
		return ids;
	}
}
