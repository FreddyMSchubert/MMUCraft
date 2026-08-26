'use client';

import Link from 'next/link';
import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { apiMessage } from '@/lib/api-response';
import {
	knowledgeMarkdown,
	stripDangerousHtml,
	stripMetadataBlock,
} from './knowledge/knowledge-markdown-renderer';
import {
	filterUnlockedTree,
	flattenPages,
	KnowledgeTreeNode,
	type KnowledgeResponse,
	type KnowledgeTreeEntry,
} from './knowledge/knowledge-tree';

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
		<div className="knowledgePanel">
			<div className="knowledgeTop">
				<h3>Knowledge</h3>
				<p className="tabSubtitle">
					Here you can find info on all the unique features of this server. You can unlock
					more entries by finding Knowledge Books in-game!
				</p>
			</div>
			<label className="knowledgeMobileNav" htmlFor="knowledge-page-select">
				<span>Knowledge page</span>
				<select
					id="knowledge-page-select"
					value={activePage.id}
					onChange={(event) => {
						selectPage(event.target.value);
					}}
				>
					<KnowledgeSelectOptions
						entries={visibleTree}
						readPageIds={readPageIds}
						depth={0}
					/>
				</select>
			</label>

			<div className="knowledgePda">
				<aside className="knowledgeSidebar" aria-label="Knowledge pages">
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
					<h1 className="knowledgePageTitle">{activePage.sidebarTitle}</h1>

					{activePageUnlocked ? (
						<>
							<article
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
									You can get knowledge books by finding them in chests, buying
									them in the{' '}
									<Link href="/play/shop/charm-knowledge-book">shop</Link>, or
									fishing them up.
								</p>
							</div>
						</div>
					)}
				</section>
			</div>
		</div>
	);
}

function KnowledgeSelectOptions({
	entries,
	readPageIds,
	depth,
}: {
	entries: KnowledgeTreeEntry[];
	readPageIds: Set<string>;
	depth: number;
}) {
	return entries.map((entry) => {
		const indent = '\u00a0'.repeat(depth * 2);
		if (entry.type === 'folder') {
			return (
				<Fragment key={`folder-${depth}-${entry.name}`}>
					<option disabled>{`${indent}${entry.name.toUpperCase()}`}</option>
					<KnowledgeSelectOptions
						entries={entry.children}
						readPageIds={readPageIds}
						depth={depth + 1}
					/>
				</Fragment>
			);
		}

		return (
			<option key={entry.id} value={entry.id}>
				{`${indent}${entry.sidebarTitle}${readPageIds.has(entry.id) ? '' : ' ❗'}`}
			</option>
		);
	});
}
