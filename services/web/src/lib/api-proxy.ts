const API_BASE_URL = process.env.API_BASE_URL ?? 'http://api:8080';

const METHODS_WITHOUT_BODY = new Set(['GET', 'HEAD']);
const NO_STORE_CACHE_CONTROL = 'no-store, no-cache, must-revalidate, proxy-revalidate';

export async function proxyApiRequest(request: Request, path: string) {
	const requestBody = METHODS_WITHOUT_BODY.has(request.method) ? undefined : await request.text();
	const requestHeaders: HeadersInit = {
		cookie: request.headers.get('cookie') ?? '',
	};
	const requestContentType = request.headers.get('content-type');

	if (requestContentType && requestBody) {
		requestHeaders['content-type'] = requestContentType;
	}

	const upstream = await fetch(`${API_BASE_URL}${path}`, {
		method: request.method,
		headers: requestHeaders,
		body: requestBody,
		cache: 'no-store',
	});

	const headers = new Headers();
	const contentType = upstream.headers.get('content-type');
	const setCookie = upstream.headers.get('set-cookie');
	const cacheControl = upstream.headers.get('cache-control');
	const pragma = upstream.headers.get('pragma');
	const expires = upstream.headers.get('expires');

	if (contentType) headers.set('content-type', contentType);
	if (setCookie) headers.set('set-cookie', setCookie);
	headers.set('cache-control', cacheControl ?? NO_STORE_CACHE_CONTROL);
	if (pragma) headers.set('pragma', pragma);
	if (expires) headers.set('expires', expires);

	// Passing the body through keeps ordinary responses working and lets SSE stay live.
	return new Response(upstream.body, {
		status: upstream.status,
		headers,
	});
}
