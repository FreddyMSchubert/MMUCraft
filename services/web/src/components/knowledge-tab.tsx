'use client';

import { Marked, type Tokens } from 'marked';
import Link from 'next/link';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiMessage } from '@/lib/api-response';

type AdmonitionType = 'info' | 'warning' | 'error' | 'hint' | 'tip' | 'note' | 'tldr' | 'context';

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
};

const knowledgeMarkdown = new Marked({
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
				return /^:::(?:info|warning|error|hint|tip|note|tldr|context)\b/m.exec(source)
					?.index;
			},
			tokenizer(source) {
				const match =
					/^:::(info|warning|error|hint|tip|note|tldr|context)(?:[ \t]+([^\r\n]+))?[ \t]*\r?\n([\s\S]*?)\r?\n:::[ \t]*(?:\r?\n|$)/.exec(
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

type KnowledgeTreeEntry = KnowledgeFolder | KnowledgePage;

interface KnowledgeFolder {
	type: 'folder';
	name: string;
	children: KnowledgeTreeEntry[];
}

interface KnowledgePage {
	type: 'page';
	id: string;
	path: string;
	sidebarTitle: string;
	unlockOrder: number | null;
	chatMessage: string;
	unlockedByDefault: boolean;
	unlocked?: boolean;
}

interface KnowledgeResponse {
	contentVersion: number;
	lastUnlockedKnowledgeId: string | null;
	unlockedKnowledgeIds: string[];
	readKnowledgeIds: string[];
	tree: KnowledgeTreeEntry[];
}

const POLL_INTERVAL_MS = 8000;
export function KnowledgeTab({
	pageId,
	onSelectPage,
}: {
	pageId?: string;
	onSelectPage: (pageId: string, replace?: boolean) => void;
}) {
	const [data, setData] = useState<KnowledgeResponse | null>(null);
	const [pageMarkdown, setPageMarkdown] = useState('');
	const [error, setError] = useState('');
	const [readPageIds, setReadPageIds] = useState<Set<string>>(new Set());
	const [markingRead, setMarkingRead] = useState(false);
	const articleRef = useRef<HTMLElement | null>(null);

	const visibleTree = useMemo(() => (data ? filterUnlockedTree(data.tree) : []), [data]);
	const pages = useMemo(() => (data ? flattenPages(data.tree) : []), [data]);
	const activePage =
		pages.find((page) => page.id === pageId) ?? flattenPages(visibleTree).at(0) ?? null;
	const activePageUnlocked = activePage?.unlocked === true;
	const activePagePath = activePageUnlocked ? activePage.path : null;
	const contentVersion = data?.contentVersion ?? 0;

	const selectPage = useCallback(
		(pageId: string) => {
			onSelectPage(pageId);
		},
		[onSelectPage],
	);

	const load = useCallback(
		async (options: { quiet?: boolean } = {}) => {
			if (!options.quiet) {
				setError('');
			}

			const response = await fetch('/api/knowledge', {
				cache: 'no-store',
			});

			const body = await response.json().catch(() => null);

			if (!response.ok) {
				throw new Error(apiMessage(body, 'Failed to load knowledge'));
			}

			const knowledge = body as KnowledgeResponse;
			setData(knowledge);
			setReadPageIds(new Set(knowledge.readKnowledgeIds));
			const allPages = flattenPages(knowledge.tree);
			const selectedPage =
				allPages.find((page) => page.id === pageId) ??
				flattenPages(filterUnlockedTree(knowledge.tree)).at(0);
			if (selectedPage) {
				if (selectedPage.id !== pageId) onSelectPage(selectedPage.id, true);
			}
		},
		[onSelectPage, pageId],
	);

	const markRead = useCallback(async () => {
		if (!activePage?.unlocked || markingRead) return;
		setMarkingRead(true);
		setError('');
		try {
			const response = await fetch('/api/knowledge/read', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ knowledgeId: activePage.id }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to mark knowledge as read'));
			setReadPageIds((current) => new Set(current).add(activePage.id));
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to mark knowledge as read');
		} finally {
			setMarkingRead(false);
		}
	}, [activePage, markingRead]);

	useEffect(() => {
		let cancelled = false;

		async function loadInitial() {
			try {
				if (!cancelled) {
					await load();
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load knowledge');
				}
			}
		}

		void loadInitial();

		return () => {
			cancelled = true;
		};
	}, [load]);

	useEffect(() => {
		const interval = window.setInterval(() => {
			if (document.visibilityState !== 'visible') return;

			void load({ quiet: true }).catch(() => undefined);
		}, POLL_INTERVAL_MS);

		function refreshWhenVisible() {
			if (document.visibilityState === 'visible') {
				void load({ quiet: true }).catch(() => undefined);
			}
		}

		document.addEventListener('visibilitychange', refreshWhenVisible);

		return () => {
			window.clearInterval(interval);
			document.removeEventListener('visibilitychange', refreshWhenVisible);
		};
	}, [load]);

	useEffect(() => {
		let cancelled = false;

		async function loadPage() {
			if (!activePagePath) {
				setPageMarkdown('');
				return;
			}

			const response = await fetch(
				`/knowledge/${activePagePath}?v=${encodeURIComponent(contentVersion)}`,
				{
					cache: 'force-cache',
				},
			);

			if (!response.ok) {
				throw new Error('Failed to load knowledge page');
			}

			const markdown = stripMetadataBlock(await response.text());
			if (!cancelled) {
				setPageMarkdown(markdown);
			}
		}

		void loadPage().catch((caught: unknown) => {
			if (!cancelled) {
				setError(
					caught instanceof Error ? caught.message : 'Failed to load knowledge page',
				);
			}
		});

		return () => {
			cancelled = true;
		};
	}, [activePagePath, contentVersion]);

	const renderedHtml = useMemo(() => {
		const html = stripDangerousHtml(knowledgeMarkdown.parse(pageMarkdown, { async: false }));
		return html;
	}, [pageMarkdown]);

	if (error && !data) {
		return <p className="authError">{error}</p>;
	}

	if (!data || !activePage) {
		return <p>Loading knowledge...</p>;
	}

	return (
		<div className="knowledgePda">
			<aside className="knowledgeSidebar" aria-label="Knowledge pages">
				<div className="knowledgeSidebarHeader">
					<span>Knowledge</span>
				</div>

				<nav className="knowledgeTree">
					{visibleTree.map((entry) => (
						<KnowledgeTreeNode
							key={entry.type === 'folder' ? `folder-${entry.name}` : entry.id}
							entry={entry}
							activePageId={activePage.id}
							readPageIds={readPageIds}
							onSelectPage={selectPage}
							depth={0}
						/>
					))}
				</nav>
			</aside>

			<section className="knowledgeReader">
				{error && (
					<p className="authError" role="alert">
						{error}
					</p>
				)}
				<div className="knowledgeReaderTop">
					<div>
						<p>{activePageUnlocked ? 'Unlocked Entry' : 'Locked Entry'}</p>
						<h3>{activePage.sidebarTitle}</h3>
					</div>
				</div>

				{activePageUnlocked ? (
					<>
						<article
							ref={articleRef}
							className="knowledgePage"
							dangerouslySetInnerHTML={{ __html: renderedHtml }}
						/>
						<button
							type="button"
							className="knowledgeReadButton"
							onClick={() => void markRead()}
							disabled={readPageIds.has(activePage.id) || markingRead}
						>
							{readPageIds.has(activePage.id)
								? 'Read'
								: markingRead
									? 'Marking…'
									: 'Mark as read (+3 dabloons)'}
						</button>
					</>
				) : (
					<div className="knowledgeLocked" role="status">
						<svg viewBox="0 0 24 24" aria-hidden="true">
							<rect x="5" y="10" width="14" height="11" rx="2" />
							<path d="M8 10V7a4 4 0 0 1 8 0v3" />
						</svg>
						<div>
							<h4>You haven&apos;t unlocked this knowledge book yet</h4>
							<p>
								You can get knowledge books by finding them in chests, buying them
								in the <Link href="/play/shop/charm-knowledge-book">shop</Link>, or
								fishing them up.
							</p>
						</div>
					</div>
				)}
			</section>
		</div>
	);
}

function KnowledgeTreeNode({
	entry,
	activePageId,
	readPageIds,
	onSelectPage,
	depth,
}: {
	entry: KnowledgeTreeEntry;
	activePageId: string;
	readPageIds: Set<string>;
	onSelectPage: (pageId: string) => void;
	depth: number;
}) {
	if (entry.type === 'folder') {
		return (
			<div className="knowledgeTreeFolder">
				<div
					className="knowledgeTreeHeading"
					style={{ paddingLeft: `${depth * 16 + 12}px` }}
				>
					{entry.name}
				</div>
				<div>
					{entry.children.map((child) => (
						<KnowledgeTreeNode
							key={
								child.type === 'folder'
									? `folder-${entry.name}-${child.name}`
									: child.id
							}
							entry={child}
							activePageId={activePageId}
							readPageIds={readPageIds}
							onSelectPage={onSelectPage}
							depth={depth + 1}
						/>
					))}
				</div>
			</div>
		);
	}

	return (
		<button
			type="button"
			className={['knowledgeTreePage', entry.id === activePageId ? 'active' : '']
				.filter(Boolean)
				.join(' ')}
			style={{ paddingLeft: `${depth * 16 + 12}px` }}
			onClick={() => {
				onSelectPage(entry.id);
			}}
		>
			<span>{entry.sidebarTitle}</span>
			{!readPageIds.has(entry.id) && (
				<span className="knowledgeTreeNew" aria-label="Not read yet">
					!
				</span>
			)}
		</button>
	);
}

function filterUnlockedTree(entries: KnowledgeTreeEntry[]): KnowledgeTreeEntry[] {
	const visible: KnowledgeTreeEntry[] = [];

	for (const entry of entries) {
		if (entry.type === 'page') {
			if (entry.unlocked) {
				visible.push(entry);
			}
			continue;
		}

		const children = filterUnlockedTree(entry.children);
		if (children.length > 0) {
			visible.push({
				...entry,
				children,
			});
		}
	}

	return visible;
}

function flattenPages(entries: KnowledgeTreeEntry[]): KnowledgePage[] {
	const pages: KnowledgePage[] = [];

	for (const entry of entries) {
		if (entry.type === 'folder') {
			pages.push(...flattenPages(entry.children));
		} else {
			pages.push(entry);
		}
	}

	return pages;
}

function stripMetadataBlock(markdown: string) {
	return markdown.replace(/^====\r?\n[\s\S]*?\r?\n====\s*/, '');
}

function stripDangerousHtml(html: string) {
	return html.replace(/<script\b[\s\S]*?<\/script>/gi, '');
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
