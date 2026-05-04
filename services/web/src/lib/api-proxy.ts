const API_BASE_URL = process.env.API_BASE_URL ?? 'http://api:8080'

const METHODS_WITHOUT_BODY = new Set(['GET', 'HEAD'])

export async function proxyApiRequest(request: Request, path: string) {
	const upstream = await fetch(`${API_BASE_URL}${path}`, {
		method: request.method,
		headers: {
			'content-type': request.headers.get('content-type') ?? 'application/json',
			cookie: request.headers.get('cookie') ?? '',
		},
		body: METHODS_WITHOUT_BODY.has(request.method) ? undefined : await request.text(),
		cache: 'no-store',
	})

	const headers = new Headers()
	const contentType = upstream.headers.get('content-type')
	const setCookie = upstream.headers.get('set-cookie')

	if (contentType) headers.set('content-type', contentType)
	if (setCookie) headers.set('set-cookie', setCookie)

	return new Response(await upstream.text(), {
		status: upstream.status,
		headers,
	})
}
