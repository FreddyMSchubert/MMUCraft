const API_BASE_URL = process.env.API_BASE_URL ?? 'http://api:8080'

const METHODS_WITHOUT_BODY = new Set(['GET', 'HEAD'])
const NO_STORE_CACHE_CONTROL = 'no-store, no-cache, must-revalidate, proxy-revalidate'

export async function proxyApiRequest(request: Request, path: string) {
	const requestBody = METHODS_WITHOUT_BODY.has(request.method) ? undefined : await request.text()
	const requestHeaders: HeadersInit = {
		cookie: request.headers.get('cookie') ?? '',
	}
	const requestContentType = request.headers.get('content-type')

	if (requestContentType && requestBody) {
		requestHeaders['content-type'] = requestContentType
	}

	const upstream = await fetch(`${API_BASE_URL}${path}`, {
		method: request.method,
		headers: requestHeaders,
		body: requestBody,
		cache: 'no-store',
	})

	const headers = new Headers()
	const contentType = upstream.headers.get('content-type')
	const setCookie = upstream.headers.get('set-cookie')

	if (contentType) headers.set('content-type', contentType)
	if (setCookie) headers.set('set-cookie', setCookie)
	headers.set('cache-control', upstream.headers.get('cache-control') ?? NO_STORE_CACHE_CONTROL)
	headers.set('pragma', upstream.headers.get('pragma') ?? 'no-cache')
	headers.set('expires', upstream.headers.get('expires') ?? '0')

	return new Response(await upstream.arrayBuffer(), {
		status: upstream.status,
		headers,
	})
}
