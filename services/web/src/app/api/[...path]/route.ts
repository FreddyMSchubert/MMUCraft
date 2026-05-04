import { proxyApiRequest } from '@/lib/api-proxy'

type RouteContext = {
	params: { path?: string[] } | Promise<{ path?: string[] }>
}

async function proxy(request: Request, context: RouteContext) {
	const params = await context.params
	const path = params.path ?? []

	return proxyApiRequest(request, `/api/${path.join('/')}`)
}

export async function GET(request: Request, context: RouteContext) {
	return proxy(request, context)
}

export async function POST(request: Request, context: RouteContext) {
	return proxy(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
	return proxy(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
	return proxy(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
	return proxy(request, context)
}
