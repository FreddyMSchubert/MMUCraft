import { Marked, type Tokens } from 'marked';

type AdmonitionType = 'info' | 'warning' | 'error' | 'hint' | 'tip' | 'note' | 'tldr' | 'context' | 'perk';

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
	perk:
		'<svg viewBox="0 0 640 640"<path d="M442.9 144C415.6 144 389.9 157.1 373.9 179.2L339.5 226.8C335 233 327.8 236.7 320.1 236.7C312.4 236.7 305.2 233 300.7 226.8L266.3 179.2C250.3 157.1 224.6 144 197.3 144C150.3 144 112.2 182.1 112.2 229.1C112.2 279 144.2 327.5 180.3 371.4C221.4 421.4 271.7 465.4 306.2 491.7C309.4 494.1 314.1 495.9 320.2 495.9C326.3 495.9 331 494.1 334.2 491.7C368.7 465.4 419 421.3 460.1 371.4C496.3 327.5 528.2 279 528.2 229.1C528.2 182.1 490.1 144 443.1 144zM335 151.1C360 116.5 400.2 96 442.9 96C516.4 96 576 155.6 576 229.1C576 297.7 533.1 358 496.9 401.9C452.8 455.5 399.6 502 363.1 529.8C350.8 539.2 335.6 543.9 320 543.9C304.4 543.9 289.2 539.2 276.9 529.8C240.4 502 187.2 455.5 143.1 402C106.9 358.1 64 297.7 64 229.1C64 155.6 123.6 96 197.1 96C239.8 96 280 116.5 305 151.1L320 171.8L335 151.1z"/></svg>',
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
					title: match.at(2)?.trim() ?? kind.toUpperCase(),
					tokens: this.lexer.blockTokens(match.at(3) ?? ''),
				} satisfies AdmonitionToken;
			},
			renderer(token) {
				const admonition = token as AdmonitionToken;
				return `<aside class="knowledgeAdmonition ${admonition.kind}" role="note"><div class="knowledgeAdmonitionTitle">${ADMONITION_ICONS[admonition.kind]}<span>${escapeHtml(admonition.title)}</span></div><div class="knowledgeAdmonitionBody">${this.parser.parse(admonition.tokens)}</div></aside>`;
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
