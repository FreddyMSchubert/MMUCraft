'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DabloonAmount, DabloonText } from '@/components/dabloon-amount';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import { dabloonizeWords } from '@/lib/dabloons';
import {
	knowledgeMarkdown,
	decorateDabloonHtml,
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
import { KnowledgeOutline } from './knowledge/knowledge-outline';

const POLL_INTERVAL_MS = 8000;
interface KnowledgeSearchSnippet {
	before: string;
	match: string;
	after: string;
}

type KnowledgeSearchResult =
	| { locked: true }
	| {
			locked: false;
			id: string;
			title: string;
			folders: string[];
			direct: boolean;
			terms: string[];
			snippets: KnowledgeSearchSnippet[];
	  };

export function KnowledgeTab({
	pageId,
	onSelectPage,
	onSearch,
}: {
	pageId?: string;
	onSelectPage: (pageId: string, replace?: boolean, highlightTerms?: string[]) => void;
	onSearch: (query: string) => void;
}) {
	const { showAlert } = useSiteAlert();
	const searchParams = useSearchParams();
	const showingSearch = pageId === 'search';
	const searchQuery = showingSearch ? (searchParams.get('q')?.trim() ?? '') : '';
	const highlightKey = showingSearch
		? ''
		: searchParams
				.getAll('find')
				.filter((term) => term.length <= 100)
				.slice(0, 50)
				.join('\0');
	const highlightTerms = useMemo(
		() =>
			highlightKey
				.split('\0')
				.map((term) => term.trim())
				.filter(Boolean),
		[highlightKey],
	);
	const [data, setData] = useState<KnowledgeResponse | null>(null);
	const [pageMarkdown, setPageMarkdown] = useState('');
	const [error, setError] = useState('');
	const [searchResponse, setSearchResponse] = useState<{
		query: string;
		results: KnowledgeSearchResult[];
		error: string;
	}>({ query: '', results: [], error: '' });
	const [readPageIds, setReadPageIds] = useState<Set<string>>(new Set());
	const [markingRead, setMarkingRead] = useState(false);
	const sidebarRef = useRef<HTMLElement>(null);
	const readerRef = useRef<HTMLElement>(null);
	const articleRef = useRef<HTMLElement>(null);
	const searchInputRef = useRef<HTMLInputElement>(null);
	const visibleTree = useMemo(() => (data ? filterUnlockedTree(data.tree) : []), [data]);
	const pages = useMemo(() => (data ? flattenPages(data.tree) : []), [data]);
	const activePage =
		pages.find((page) => page.id === pageId) ?? flattenPages(visibleTree).at(0) ?? null;
	const activePageUnlocked = activePage?.unlocked === true;
	const activePagePath = !showingSearch && activePageUnlocked ? activePage.path : null;
	const contentVersion = data?.contentVersion ?? 0;
	const searchResults = searchResponse.query === searchQuery ? searchResponse.results : [];
	const searchError = searchResponse.query === searchQuery ? searchResponse.error : '';
	const searching = Boolean(searchQuery && searchResponse.query !== searchQuery);

	const selectPage = useCallback(
		(pageId: string, terms?: string[]) => {
			onSelectPage(pageId, false, terms);
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
			if (!showingSearch && selectedPage) {
				if (selectedPage.id !== pageId) onSelectPage(selectedPage.id, true);
			}
		},
		[onSelectPage, pageId, showingSearch],
	);

	useEffect(() => {
		if (!showingSearch || !searchQuery) return;

		let cancelled = false;
		void fetch(`/api/knowledge/search?q=${encodeURIComponent(searchQuery)}`, {
			cache: 'no-store',
		})
			.then(async (response) => {
				const body = (await response.json().catch(() => null)) as {
					results?: KnowledgeSearchResult[];
				} | null;
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to search knowledge'));
				if (!cancelled) {
					setSearchResponse({
						query: searchQuery,
						results: body?.results ?? [],
						error: '',
					});
				}
			})
			.catch((caught: unknown) => {
				if (!cancelled) {
					setSearchResponse({
						query: searchQuery,
						results: [],
						error:
							caught instanceof Error ? caught.message : 'Failed to search knowledge',
					});
				}
			});

		return () => {
			cancelled = true;
		};
	}, [searchQuery, showingSearch]);

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
			await showAlert({
				title: 'Could not mark this page as read',
				message:
					caught instanceof Error
						? caught.message
						: 'The reading reward could not be recorded. Please try again.',
				tone: 'danger',
			});
		} finally {
			setMarkingRead(false);
		}
	}, [activePage, markingRead, showAlert]);

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
		return activePage?.id === 'money-basics' ? html : decorateDabloonHtml(html);
	}, [activePage?.id, pageMarkdown]);

	useEffect(() => {
		const article = articleRef.current;
		if (!article) return;
		if (!highlightTerms.length) return;

		const first = highlightArticleMatches(article, highlightTerms);
		if (!first) return;
		const frame = window.requestAnimationFrame(() => {
			first.scrollIntoView({ block: 'center' });
		});
		return () => {
			window.cancelAnimationFrame(frame);
		};
	}, [highlightTerms, renderedHtml]);

	if (error && !data) {
		return <p className="authError">{error}</p>;
	}

	if (!data || (!activePage && !showingSearch)) {
		return <p>Loading knowledge...</p>;
	}

	return (
		<div className="knowledgePanel">
			<div className="knowledgeTop">
				<h3>Knowledge</h3>
				<p className="tabSubtitle">
					Here you can find all infos on this server & the society. You can unlock more
					entries by finding Knowledge Books in-game!
				</p>
			</div>
			<div className="knowledgePda">
				<form
					className="knowledgeSearch"
					role="search"
					onSubmit={(event) => {
						event.preventDefault();
						const query = new FormData(event.currentTarget).get('q');
						if (typeof query === 'string' && query.trim()) onSearch(query.trim());
					}}
				>
					<div className="knowledgeSearchField">
						<input
							key={searchQuery}
							ref={searchInputRef}
							type="text"
							name="q"
							aria-label="Search knowledge"
							placeholder="Search…"
							defaultValue={searchQuery}
							maxLength={100}
							required
						/>
						<button
							type="button"
							className="knowledgeSearchClear"
							aria-label="Clear search"
							onClick={() => {
								if (!searchInputRef.current) return;
								searchInputRef.current.value = '';
								searchInputRef.current.focus();
							}}
						>
							×
						</button>
					</div>
					<button className="knowledgeSearchSubmit" type="submit">
						Search
					</button>
				</form>

				<label className="knowledgeMobileNav" htmlFor="knowledge-page-select">
					<span>Knowledge page</span>
					<select
						id="knowledge-page-select"
						value={showingSearch ? '' : activePage?.id}
						onChange={(event) => {
							selectPage(event.target.value);
						}}
					>
						{showingSearch && (
							<option value="" disabled>
								Select a page
							</option>
						)}
						<KnowledgeSelectOptions
							entries={visibleTree}
							readPageIds={readPageIds}
							depth={0}
						/>
					</select>
				</label>

				<div className="knowledgeSidebarRail">
					<aside
						ref={sidebarRef}
						className="knowledgeSidebar"
						aria-label="Knowledge pages"
					>
						<nav className="knowledgeTree">
							{visibleTree.map((entry) => (
								<KnowledgeTreeNode
									key={
										entry.type === 'folder' ? `folder-${entry.name}` : entry.id
									}
									entry={entry}
									activePageId={showingSearch ? '' : (activePage?.id ?? '')}
									readPageIds={readPageIds}
									onSelectPage={selectPage}
									depth={0}
								/>
							))}
						</nav>
					</aside>
					{!showingSearch && activePageUnlocked && (
						<KnowledgeOutline
							articleRef={articleRef}
							readerRef={readerRef}
							sidebarRef={sidebarRef}
							contentKey={`${activePage.id}:${renderedHtml}`}
						/>
					)}
				</div>

				<section ref={readerRef} className="knowledgeReader">
					{showingSearch ? (
						<KnowledgeSearchResults
							query={searchQuery}
							results={searchResults}
							searching={searching}
							error={searchError}
							onSelectPage={selectPage}
						/>
					) : (
						<>
							{error && (
								<p className="authError" role="alert">
									{error}
								</p>
							)}
							<h1 className="knowledgePageTitle">
								{activePage?.id === 'money-basics' ? (
									activePage.sidebarTitle
								) : (
									<DabloonText>{activePage?.sidebarTitle ?? ''}</DabloonText>
								)}
							</h1>

							{activePage && activePageUnlocked ? (
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
										{readPageIds.has(activePage.id) ? (
											'Read'
										) : markingRead ? (
											'Marking…'
										) : (
											<>
												Mark as read{' '}
												<DabloonAmount
													amount={3}
													format="delta"
													tone="inherit"
												/>
											</>
										)}
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
											You can get knowledge books by finding them in chests,
											buying them in the{' '}
											<Link href="/play/shop/charm-knowledge-book">shop</Link>
											, or fishing them up.
										</p>
									</div>
								</div>
							)}
						</>
					)}
				</section>
			</div>
		</div>
	);
}

function KnowledgeSearchResults({
	query,
	results,
	searching,
	error,
	onSelectPage,
}: {
	query: string;
	results: KnowledgeSearchResult[];
	searching: boolean;
	error: string;
	onSelectPage: (pageId: string, highlightTerms?: string[]) => void;
}) {
	const directResults = results.filter((result) => !result.locked && result.direct);
	const similarResults = results.filter((result) => result.locked || !result.direct);

	return (
		<>
			<h1 className="knowledgePageTitle">Search results</h1>
			{query && <p className="knowledgeSearchSummary">Results for “{query}”</p>}
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
			{searching && <p>Searching knowledge…</p>}
			{!searching && !error && query && results.length === 0 && (
				<p>No knowledge matched your search.</p>
			)}
			{!searching && directResults.length > 0 && (
				<KnowledgeSearchResultList results={directResults} onSelectPage={onSelectPage} />
			)}
			{!searching && directResults.length > 0 && similarResults.length > 0 && (
				<h2 className="knowledgeSimilarHeading">Other similar matches</h2>
			)}
			{!searching && similarResults.length > 0 && (
				<KnowledgeSearchResultList results={similarResults} onSelectPage={onSelectPage} />
			)}
		</>
	);
}

function KnowledgeSearchResultList({
	results,
	onSelectPage,
}: {
	results: KnowledgeSearchResult[];
	onSelectPage: (pageId: string, highlightTerms?: string[]) => void;
}) {
	return (
		<ul className="knowledgeSearchResults">
			{results.map((result, index) =>
				result.locked ? (
					<li key={`locked-${index}`} className="locked">
						<strong>🔒 Locked knowledge</strong>
						<span>Unlock this knowledge book to read the matching result.</span>
					</li>
				) : (
					<li key={result.id}>
						<Link
							className="knowledgeSearchResultTitle"
							href={`/play/knowledge/${encodeURIComponent(result.id)}`}
							onNavigate={(event) => {
								event.preventDefault();
								onSelectPage(result.id);
							}}
						>
							{result.folders.length > 0 && (
								<span>{result.folders.join(' / ')}/</span>
							)}
							<strong>{result.title}</strong>
						</Link>
						{result.snippets.length > 0 && (
							<ul className="knowledgeSearchMatches">
								{result.snippets.map((snippet, snippetIndex) => {
									const href = knowledgeHighlightHref(result.id, result.terms);
									return (
										<li
											key={`${snippet.before}-${snippet.match}-${snippetIndex}`}
										>
											<Link
												href={href}
												onNavigate={(event) => {
													event.preventDefault();
													onSelectPage(result.id, result.terms);
												}}
											>
												{snippet.before}
												<mark>{snippet.match}</mark>
												{snippet.after}
											</Link>
										</li>
									);
								})}
							</ul>
						)}
					</li>
				),
			)}
		</ul>
	);
}

function knowledgeHighlightHref(pageId: string, terms: string[]) {
	const params = new URLSearchParams();
	for (const term of terms) params.append('find', term);
	return `/play/knowledge/${encodeURIComponent(pageId)}?${params.toString()}`;
}

function highlightArticleMatches(article: HTMLElement, terms: string[]) {
	const uniqueTerms = [...new Set(terms)].sort((left, right) => right.length - left.length);
	if (!uniqueTerms.length) return null;
	const expression = new RegExp(
		`(?<![\\p{L}\\p{N}_])(?:${uniqueTerms.map(escapeRegExp).join('|')})(?![\\p{L}\\p{N}_])`,
		'giu',
	);
	const walker = document.createTreeWalker(article, NodeFilter.SHOW_TEXT);
	const textNodes: Text[] = [];
	while (walker.nextNode()) textNodes.push(walker.currentNode as Text);
	let first: HTMLElement | null = null;

	for (const node of textNodes) {
		const text = node.data;
		const matches = [...text.matchAll(expression)];
		if (!matches.length) continue;
		const fragment = document.createDocumentFragment();
		let cursor = 0;
		for (const match of matches) {
			const index = match.index;
			fragment.append(text.slice(cursor, index));
			const mark = document.createElement('mark');
			mark.className = 'knowledgeSearchHighlight';
			mark.textContent = match[0];
			fragment.append(mark);
			first ??= mark;
			cursor = index + match[0].length;
		}
		fragment.append(text.slice(cursor));
		node.replaceWith(fragment);
	}

	return first;
}

function escapeRegExp(value: string) {
	return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
				{`${indent}${entry.id === 'money-basics' ? entry.sidebarTitle : dabloonizeWords(entry.sidebarTitle)}${readPageIds.has(entry.id) ? '' : ' ❗'}`}
			</option>
		);
	});
}
