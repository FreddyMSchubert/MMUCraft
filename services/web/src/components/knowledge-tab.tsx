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
	contentVersion: number
	lastUnlockedKnowledgeId: string | null
	unlockedKnowledgeIds: string[]
	readKnowledgeIds: string[]
	tree: KnowledgeTreeEntry[]
}

const POLL_INTERVAL_MS = 8000
export function KnowledgeTab({ pageId, onSelectPage }: {
	pageId?: string
	onSelectPage: (pageId: string, replace?: boolean) => void
}) {
	const [data, setData] = useState<KnowledgeResponse | null>(null)
	const [pageMarkdown, setPageMarkdown] = useState('')
	const [error, setError] = useState('')
	const [readPageIds, setReadPageIds] = useState<Set<string>>(new Set())
	const [markingRead, setMarkingRead] = useState(false)
	const articleRef = useRef<HTMLElement | null>(null)

	const visibleTree = useMemo(() => data ? filterUnlockedTree(data.tree) : [], [data])
	const pages = useMemo(() => flattenPages(visibleTree), [visibleTree])
	const activePage = pages.find((page) => page.id === pageId) ?? pages[0] ?? null
	const activePagePath = activePage?.path ?? null
	const contentVersion = data?.contentVersion ?? 0

	const selectPage = useCallback((pageId: string) => {
		onSelectPage(pageId)
	}, [onSelectPage])

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
		setReadPageIds(new Set(knowledge.readKnowledgeIds))
		const visiblePages = flattenPages(filterUnlockedTree(knowledge.tree))
		const selectedPage = visiblePages.find((page) => page.id === pageId) ?? visiblePages[0]
		if (selectedPage) {
			if (selectedPage.id !== pageId) onSelectPage(selectedPage.id, true)
		}
	}, [onSelectPage, pageId])

	const markRead = useCallback(async () => {
		if (!activePage || markingRead) return
		setMarkingRead(true)
		setError('')
		try {
			const response = await fetch('/api/knowledge/read', {
				method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ knowledgeId: activePage.id }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(body?.message ?? 'Failed to mark knowledge as read')
			setReadPageIds((current) => new Set(current).add(activePage.id))
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to mark knowledge as read')
		} finally {
			setMarkingRead(false)
		}
	}, [activePage, markingRead])

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

			const response = await fetch(`/knowledge/${activePagePath}?v=${encodeURIComponent(contentVersion)}`, {
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
	}, [activePagePath, contentVersion])

	const renderedHtml = useMemo(() => {
		const html = stripDangerousHtml(marked.parse(pageMarkdown, { async: false }) as string)
		return html
	}, [pageMarkdown])

	if (error && !data) {
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
							readPageIds={readPageIds}
							onSelectPage={selectPage}
							depth={0}
						/>
					))}
				</nav>
			</aside>

			<section className="knowledgeReader">
				{error && <p className="authError" role="alert">{error}</p>}
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
				<button type="button" className="knowledgeReadButton" onClick={() => void markRead()} disabled={readPageIds.has(activePage.id) || markingRead}>
					{readPageIds.has(activePage.id) ? 'Read' : markingRead ? 'Marking…' : 'Mark as read (+3 dabloons)'}
				</button>
			</section>
		</div>
	)
}

function KnowledgeTreeNode({
	entry,
	activePageId,
	readPageIds,
	onSelectPage,
	depth,
}: {
	entry: KnowledgeTreeEntry
	activePageId: string
	readPageIds: Set<string>
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
							readPageIds={readPageIds}
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
			{!readPageIds.has(entry.id) && (
				<span className="knowledgeTreeNew" aria-label="Not read yet">!</span>
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
