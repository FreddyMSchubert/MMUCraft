'use client'

import { marked } from 'marked'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

type KnowledgeTreeEntry = KnowledgeFolder | KnowledgePage

interface KnowledgeFolder {
	type: 'folder'
	name: string
	children: KnowledgeTreeEntry[]
}

interface KnowledgePage {
	type: 'page'
	id: string
	path: string
	sidebarTitle: string
	unlockOrder: number | null
	chatMessage: string
	unlockedByDefault: boolean
	unlocked?: boolean
}

interface KnowledgeResponse {
	lastUnlockedKnowledgeId: string | null
	unlockedKnowledgeIds: string[]
	tree: KnowledgeTreeEntry[]
}

const POLL_INTERVAL_MS = 8000
const VIEWED_STORAGE_KEY = 'mcstack.viewedKnowledgePages'

export function KnowledgeTab() {
	const [data, setData] = useState<KnowledgeResponse | null>(null)
	const [activePageId, setActivePageId] = useState<string | null>(null)
	const [pageMarkdown, setPageMarkdown] = useState('')
	const [error, setError] = useState('')
	const [viewedPageIds, setViewedPageIds] = useState<Set<string>>(() => readViewedPageIds())
	const articleRef = useRef<HTMLElement | null>(null)

	const visibleTree = useMemo(() => data ? filterUnlockedTree(data.tree) : [], [data])
	const pages = useMemo(() => flattenPages(visibleTree), [visibleTree])
	const activePage = pages.find((page) => page.id === activePageId) ?? pages[0] ?? null
	const activePagePath = activePage?.path ?? null

	const markPageViewed = useCallback((pageId: string) => {
		setViewedPageIds((current) => {
			if (current.has(pageId)) return current

			const next = new Set(current)
			next.add(pageId)
			writeViewedPageIds(next)
			return next
		})
	}, [])

	const selectPage = useCallback((pageId: string) => {
		setActivePageId(pageId)
		markPageViewed(pageId)
	}, [markPageViewed])

	const load = useCallback(async (options: { quiet?: boolean } = {}) => {
		if (!options.quiet) {
			setError('')
		}

		const response = await fetch('/api/knowledge', {
			cache: 'no-store',
		})

		const body = await response.json().catch(() => null)

		if (!response.ok) {
			throw new Error(body?.message ?? 'Failed to load knowledge')
		}

		const knowledge = body as KnowledgeResponse
		setData(knowledge)
		setActivePageId((current) => {
			const visiblePages = flattenPages(filterUnlockedTree(knowledge.tree))
			const currentStillVisible = visiblePages.some((page) => page.id === current)

			if (current && currentStillVisible) {
				return current
			}

			const nextPageId = visiblePages[0]?.id ?? null
			if (nextPageId) {
				markPageViewed(nextPageId)
			}

			return nextPageId
		})
	}, [markPageViewed])

	useEffect(() => {
		let cancelled = false

		async function loadInitial() {
			try {
				if (!cancelled) {
					await load()
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load knowledge')
				}
			}
		}

		void loadInitial()

		return () => {
			cancelled = true
		}
	}, [load])

	useEffect(() => {
		const interval = window.setInterval(() => {
			if (document.visibilityState !== 'visible') return

			void load({ quiet: true }).catch(() => undefined)
		}, POLL_INTERVAL_MS)

		function refreshWhenVisible() {
			if (document.visibilityState === 'visible') {
				void load({ quiet: true }).catch(() => undefined)
			}
		}

		document.addEventListener('visibilitychange', refreshWhenVisible)

		return () => {
			window.clearInterval(interval)
			document.removeEventListener('visibilitychange', refreshWhenVisible)
		}
	}, [load])

	useEffect(() => {
		let cancelled = false

		async function loadPage() {
			if (!activePagePath) {
				setPageMarkdown('')
				return
			}

			const response = await fetch(`/knowledge/${activePagePath}`, {
				cache: 'force-cache',
			})

			if (!response.ok) {
				throw new Error('Failed to load knowledge page')
			}

			const markdown = stripMetadataBlock(await response.text())
			if (!cancelled) {
				setPageMarkdown(markdown)
			}
		}

		void loadPage().catch((caught) => {
			if (!cancelled) {
				setError(caught instanceof Error ? caught.message : 'Failed to load knowledge page')
			}
		})

		return () => {
			cancelled = true
		}
	}, [activePagePath])

	const renderedHtml = useMemo(() => {
		const html = stripDangerousHtml(marked.parse(pageMarkdown, { async: false }) as string)
		return html
	}, [pageMarkdown])

	if (error) {
		return <p className="authError">{error}</p>
	}

	if (!data || !activePage) {
		return <p>Loading knowledge...</p>
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
							viewedPageIds={viewedPageIds}
							onSelectPage={selectPage}
							depth={0}
						/>
					))}
				</nav>
			</aside>

			<section className="knowledgeReader">
				<div className="knowledgeReaderTop">
					<div>
						<p>Unlocked Entry</p>
						<h3>{activePage.sidebarTitle}</h3>
					</div>
				</div>

				<article
					ref={articleRef}
					className="knowledgePage"
					dangerouslySetInnerHTML={{ __html: renderedHtml }}
				/>
			</section>
		</div>
	)
}

function KnowledgeTreeNode({
	entry,
	activePageId,
	viewedPageIds,
	onSelectPage,
	depth,
}: {
	entry: KnowledgeTreeEntry
	activePageId: string
	viewedPageIds: Set<string>
	onSelectPage: (pageId: string) => void
	depth: number
}) {
	if (entry.type === 'folder') {
		return (
			<div className="knowledgeTreeFolder">
				<div className="knowledgeTreeHeading" style={{ paddingLeft: `${depth * 16 + 12}px` }}>
					{entry.name}
				</div>
				<div>
					{entry.children.map((child) => (
						<KnowledgeTreeNode
							key={child.type === 'folder' ? `folder-${entry.name}-${child.name}` : child.id}
							entry={child}
							activePageId={activePageId}
							viewedPageIds={viewedPageIds}
							onSelectPage={onSelectPage}
							depth={depth + 1}
						/>
					))}
				</div>
			</div>
		)
	}

	return (
		<button
			type="button"
			className={[
				'knowledgeTreePage',
				entry.id === activePageId ? 'active' : '',
			].filter(Boolean).join(' ')}
			style={{ paddingLeft: `${depth * 16 + 12}px` }}
			onClick={() => onSelectPage(entry.id)}
		>
			<span>{entry.sidebarTitle}</span>
			{!viewedPageIds.has(entry.id) && (
				<span className="knowledgeTreeNew" aria-label="Not viewed yet">!</span>
			)}
		</button>
	)
}

function filterUnlockedTree(entries: KnowledgeTreeEntry[]): KnowledgeTreeEntry[] {
	const visible: KnowledgeTreeEntry[] = []

	for (const entry of entries) {
		if (entry.type === 'page') {
			if (entry.unlocked) {
				visible.push(entry)
			}
			continue
		}

		const children = filterUnlockedTree(entry.children)
		if (children.length > 0) {
			visible.push({
				...entry,
				children,
			})
		}
	}

	return visible
}

function flattenPages(entries: KnowledgeTreeEntry[]): KnowledgePage[] {
	const pages: KnowledgePage[] = []

	for (const entry of entries) {
		if (entry.type === 'folder') {
			pages.push(...flattenPages(entry.children))
		} else {
			pages.push(entry)
		}
	}

	return pages
}

function stripMetadataBlock(markdown: string) {
	return markdown.replace(/^====\r?\n[\s\S]*?\r?\n====\s*/, '')
}

function stripDangerousHtml(html: string) {
	return html.replace(/<script\b[\s\S]*?<\/script>/gi, '')
}

function readViewedPageIds(): Set<string> {
	if (typeof window === 'undefined') return new Set()

	const raw = window.localStorage.getItem(VIEWED_STORAGE_KEY)
	if (!raw) return new Set()

	try {
		const values = JSON.parse(raw)
		return Array.isArray(values)
			? new Set(values.filter((value): value is string => typeof value === 'string'))
			: new Set()
	} catch {
		return new Set()
	}
}

function writeViewedPageIds(ids: Set<string>) {
	window.localStorage.setItem(VIEWED_STORAGE_KEY, JSON.stringify([...ids]))
}
