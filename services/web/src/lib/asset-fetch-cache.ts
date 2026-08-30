const MAX_CONCURRENT_ASSET_REQUESTS = 6;
const MAX_ASSET_ATTEMPTS = 4;
const IMMUTABLE_CACHE_NAME = 'mmucraft-immutable-assets-v1';

const memoryCache = new Map<string, Promise<Response>>();
const queue: (() => void)[] = [];
let activeRequests = 0;

export class AssetResponseError extends Error {
	constructor(
		readonly status: number,
		url: string,
	) {
		super(`Asset request failed with status ${status}: ${url}`);
	}
}

function schedule<T>(task: () => Promise<T>) {
	return new Promise<T>((resolve, reject) => {
		const run = () => {
			activeRequests += 1;
			void task()
				.then(resolve, reject)
				.finally(() => {
					activeRequests -= 1;
					queue.shift()?.();
				});
		};
		if (activeRequests < MAX_CONCURRENT_ASSET_REQUESTS) run();
		else queue.push(run);
	});
}

function retryDelay(response: Response | null, attempt: number) {
	const retryAfter = response?.headers.get('retry-after');
	if (retryAfter) {
		const seconds = Number(retryAfter);
		if (Number.isFinite(seconds)) return Math.max(250, seconds * 1000);
	}
	return 300 * 2 ** attempt + Math.random() * 150;
}

function wait(delayMs: number) {
	return new Promise((resolve) => window.setTimeout(resolve, delayMs));
}

async function persistentMatch(url: string) {
	if (!('caches' in window)) return null;
	try {
		return await (await caches.open(IMMUTABLE_CACHE_NAME)).match(url);
	} catch {
		return null;
	}
}

async function fetchAsset(url: string) {
	const persisted = await persistentMatch(url);
	if (persisted) return persisted;

	let lastResponse: Response | null = null;
	let lastError: unknown;
	for (let attempt = 0; attempt < MAX_ASSET_ATTEMPTS; attempt += 1) {
		try {
			lastResponse = await fetch(url, { cache: 'force-cache' });
			if (lastResponse.ok) {
				if (
					'caches' in window &&
					lastResponse.headers.get('cache-control')?.includes('immutable')
				) {
					try {
						const cache = await caches.open(IMMUTABLE_CACHE_NAME);
						await cache.put(url, lastResponse.clone());
					} catch {
						// Browser-managed HTTP caching still applies when Cache Storage is unavailable.
					}
				}
				return lastResponse;
			}
			if (lastResponse.status !== 429 && lastResponse.status < 500) break;
		} catch (error) {
			lastError = error;
		}
		if (attempt < MAX_ASSET_ATTEMPTS - 1) await wait(retryDelay(lastResponse, attempt));
	}

	if (lastResponse) throw new AssetResponseError(lastResponse.status, url);
	throw lastError instanceof Error ? lastError : new Error(`Asset request failed: ${url}`);
}

export function loadAssetResponse(url: string) {
	const cached = memoryCache.get(url);
	if (cached) return cached.then((response) => response.clone());

	const loading = schedule(() => fetchAsset(url));
	memoryCache.set(url, loading);
	void loading.catch(() => memoryCache.delete(url));
	return loading.then((response) => response.clone());
}

export async function loadAssetJson<T>(url: string) {
	return (await loadAssetResponse(url)).json() as Promise<T>;
}
