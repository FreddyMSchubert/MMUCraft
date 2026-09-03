import { Marked, type Tokens } from 'marked';

type AdmonitionType =
	'info' | 'warning' | 'error' | 'hint' | 'tip' | 'note' | 'tldr' | 'context' | 'perk';

interface AdmonitionToken extends Tokens.Generic {
	type: 'admonition';
	kind: AdmonitionType;
	title: string;
	tokens: Tokens.Generic[];
}

interface RecipeItemsToken extends Tokens.Generic {
	type: 'recipeItems';
	tokens: Tokens.Generic[];
}

const ADMONITION_ICONS: Record<AdmonitionType, string> = {
	info: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/></svg>',
	warning:
		'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.3 3.7 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.7a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/></svg>',
	error: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/></svg>',
	hint: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18h6M10 22h4M8.5 14.5a7 7 0 1 1 7 0c-.9.7-1.5 1.7-1.5 2.5h-4c0-.8-.6-1.8-1.5-2.5Z"/></svg>',
	tip: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5L12 3ZM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z"/></svg>',
	note: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h14v18H5zM9 8h6M9 12h6M9 16h4"/></svg>',
	tldr: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h10M4 18h13"/></svg>',
	context:
		'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2 5-5 2 2-5 5-2Z"/></svg>',
	perk: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>',
};

export const knowledgeMarkdown = new Marked({
	renderer: {
		link({ href, title, tokens }) {
			const titleAttribute = title ? ` title="${escapeHtml(title)}"` : '';
			const externalAttributes = isExternalKnowledgeLink(href)
				? ' target="_blank" rel="noopener noreferrer"'
				: '';
			return `<a href="${escapeHtml(href)}"${titleAttribute}${externalAttributes}>${this.parser.parseInline(tokens)}</a>`;
		},
	},
	extensions: [
		{
			name: 'admonition',
			level: 'block',
			start(source) {
				return /^:::(?:info|warning|error|hint|tip|note|tldr|context|perk)\b/m.exec(source)
					?.index;
			},
			tokenizer(source) {
				const match =
					/^:::(info|warning|error|hint|tip|note|tldr|context|perk)(?:[ \t]+([^\r\n]+))?[ \t]*\r?\n([\s\S]*?)\r?\n:::[ \t]*(?:\r?\n|$)/.exec(
						source,
					);
				if (!match) return;

				const kind = match[1] as AdmonitionType;
				return {
					type: 'admonition',
					raw: match[0],
					kind,
					title:
						match.at(2)?.trim() ??
						(kind === 'perk' ? 'Membership Perk' : kind.toUpperCase()),
					tokens: this.lexer.blockTokens(match.at(3) ?? ''),
				} satisfies AdmonitionToken;
			},
			renderer(token) {
				const admonition = token as AdmonitionToken;
				const title = `${ADMONITION_ICONS[admonition.kind]}<span>${escapeHtml(admonition.title)}</span>`;
				const heading =
					admonition.kind === 'perk'
						? `<a class="knowledgeAdmonitionTitle" href="/play/knowledge/membership">${title}</a>`
						: `<div class="knowledgeAdmonitionTitle">${title}</div>`;
				return `<aside class="knowledgeAdmonition ${admonition.kind}" role="note">${heading}<div class="knowledgeAdmonitionBody">${this.parser.parse(admonition.tokens)}</div></aside>`;
			},
			childTokens: ['tokens'],
		},
		{
			name: 'recipeItems',
			level: 'block',
			start(source) {
				return /^:::recipe-items\b/m.exec(source)?.index;
			},
			tokenizer(source) {
				const match = /^:::recipe-items[ \t]*\r?\n([^\r\n]+)\r?\n:::[ \t]*(?:\r?\n|$)/.exec(
					source,
				);
				if (!match) return;
				return {
					type: 'recipeItems',
					raw: match[0],
					tokens: this.lexer.inlineTokens(match[1].trim()),
				} satisfies RecipeItemsToken;
			},
			renderer(token) {
				return `<div class="knowledgeRecipeItems">${this.parser.parseInline((token as RecipeItemsToken).tokens)}</div>`;
			},
			childTokens: ['tokens'],
		},
	],
});
export function stripMetadataBlock(markdown: string) {
	return markdown.replace(/^====\r?\n[\s\S]*?\r?\n====\s*/, '');
}

export function stripDangerousHtml(html: string) {
	return html.replace(/<script\b[\s\S]*?<\/script>/gi, '');
}

export function decorateDabloonHtml(html: string) {
	return html;
}

function escapeHtml(value: string) {
	return value.replace(
		/[&<>"']/g,
		(character) =>
			({
				'&': '&amp;',
				'<': '&lt;',
				'>': '&gt;',
				'"': '&quot;',
				"'": '&#39;',
			})[character] ?? character,
	);
}

function isExternalKnowledgeLink(href: string) {
	try {
		const url = new URL(href, 'https://mmuminecraftsociety.co.uk');
		return (
			['http:', 'https:'].includes(url.protocol) &&
			url.hostname !== 'mmuminecraftsociety.co.uk' &&
			!url.hostname.endsWith('.mmuminecraftsociety.co.uk')
		);
	} catch {
		return false;
	}
}
