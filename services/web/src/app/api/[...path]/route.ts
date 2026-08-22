import { proxyApiRequest } from '@/lib/api-proxy';

export const dynamic = 'force-dynamic';
export const revalidate = 0;
export const fetchCache = 'force-no-store';

interface RouteContext {
	params: { path?: string[] } | Promise<{ path?: string[] }>;
}

async function proxy(request: Request, context: RouteContext) {
	const params = await context.params;
	const path = params.path ?? [];
	const search = new URL(request.url).search;

	return proxyApiRequest(request, `/api/${path.join('/')}${search}`);
}

export async function GET(request: Request, context: RouteContext) {
	return proxy(request, context);
}

export async function POST(request: Request, context: RouteContext) {
	return proxy(request, context);
}

export async function PUT(request: Request, context: RouteContext) {
	return proxy(request, context);
}

export async function PATCH(request: Request, context: RouteContext) {
	return proxy(request, context);
}

export async function DELETE(request: Request, context: RouteContext) {
	return proxy(request, context);
}
