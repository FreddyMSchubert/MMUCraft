const API_BASE_URL = process.env.API_BASE_URL ?? 'http://api:8080'

const METHODS_WITHOUT_BODY = new Set(['GET', 'HEAD'])

type KnowledgeSection =
	| {
		type: 'public'
		html: string
	}
	| {
		type: 'knowledge'
		id: string
		html: string
	}

interface ApiKnowledgeResponse {
	lastUnlockedKnowledgeId: string | null
	sections: KnowledgeSection[]
}

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

	const body = await upstream.text()

	return new Response(transformBody(path, request.method, upstream.status, body), {
		status: upstream.status,
		headers,
	})
}

function transformBody(path: string, method: string, status: number, body: string): string {
	if (path !== '/api/knowledge' || method !== 'GET' || status < 200 || status >= 300) {
		return body
	}

	const knowledge = JSON.parse(body) as ApiKnowledgeResponse

	return JSON.stringify({
		html: knowledge.sections.map((section) => {
			if (section.type === 'public') {
				return section.html
			}

			return addKnowledgeAnchor(section.html, section.id)
		}).join('\n'),
		lastUnlockedElementId: knowledge.lastUnlockedKnowledgeId
			? knowledgeElementId(knowledge.lastUnlockedKnowledgeId)
			: null,
	})
}

function addKnowledgeAnchor(html: string, id: string): string {
	const elementId = knowledgeElementId(id)
	const replaced = html.replace(
		/^(\s*)<([A-Za-z][\w:-]*)([^>]*)>/,
		(_, leadingWhitespace: string, tagName: string, existingAttributes: string) => {
			const withClass = /\sclass\s*=/i.test(existingAttributes)
				? existingAttributes.replace(
					/\sclass\s*=\s*(["'])(.*?)\1/i,
					(__, quote: string, value: string) => ` class=${quote}${value} knowledgeUnlock${quote}`,
				)
				: `${existingAttributes} class="knowledgeUnlock"`

			return `${leadingWhitespace}<${tagName}${withClass} id="${elementId}">`
		},
	)

	if (replaced !== html) {
		return replaced
	}

	return `<span class="knowledgeUnlock" id="${elementId}">${html}</span>`
}

function knowledgeElementId(id: string): string {
	return `knowledge-${id}`
}
