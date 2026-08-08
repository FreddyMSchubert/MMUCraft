'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthPanel } from '@/components/auth-panel'
import { SitePage } from '@/components/site-page'
import { AdminTab } from '@/components/admin-tab'
import { DailiesTab } from '@/components/dailies-tab'
import { KnowledgeTab } from '@/components/knowledge-tab'
import { MiscTab } from '@/components/misc-tab'
import { PlayersTab } from '@/components/players-tab'
import { ShopTab } from '@/components/shop-tab'
import { FishingTab } from '@/components/fishing-tab'
import { ClaimsTab } from '@/components/claims-tab'
import { CharmsTab } from '@/components/charms-tab'
import { PlayerName } from '@/components/player-name'

interface SessionUser {
	id: number
	minecraftUsername: string
	color: string
	whitelisted: boolean
	rulesAccepted: boolean
	isMember: boolean
	isCommittee: boolean
	isSuperAdmin: boolean
}

type TabId = 'dailies' | 'knowledge' | 'charms' | 'shop' | 'claims' | 'fishing' | 'players' | 'admin' | 'misc'

const TAB_IDS = new Set<TabId>(['dailies', 'knowledge', 'charms', 'shop', 'claims', 'fishing', 'players', 'admin', 'misc'])
const ADMIN_SECTIONS = new Set(['members', 'signins', 'claims', 'whitelist', 'bans', 'gifts'])
const MISC_SECTIONS = new Set(['settings', 'gift-codes'])
const TAB_LINKS: Array<{ id: TabId; label: string; href: string }> = [
	{ id: 'knowledge', label: 'Knowledge', href: '/play/knowledge' },
	{ id: 'dailies', label: 'Dailies', href: '/play/dailies' },
	{ id: 'charms', label: 'Charms', href: '/play/charms' },
	{ id: 'shop', label: 'Shop', href: '/play/shop' },
	{ id: 'claims', label: 'Claims', href: '/play/claims' },
	{ id: 'fishing', label: 'Fishing', href: '/play/fishing' },
	{ id: 'players', label: 'Players', href: '/play/players' },
	{ id: 'admin', label: 'Admin', href: '/play/admin/members' },
	{ id: 'misc', label: 'Misc', href: '/play/misc/settings' },
]

async function fetchMe(): Promise<SessionUser | null> {
	const response = await fetch('/api/auth/me', {
		cache: 'no-store',
	})

	if (!response.ok) {
		throw new Error('Failed to check session')
	}

	const data = await response.json().catch(() => null) as { user?: SessionUser | null } | null
	return data?.user ?? null
}

export function SiteShell({ background, splash }: { background: string; splash: string }) {
	const pathname = usePathname()
	const router = useRouter()
	const [user, setUser] = useState<SessionUser | null | undefined>(undefined)
	const route = useMemo(() => pathname.split('/').slice(2).map(decodePathSegment), [pathname])
	const activeTab = TAB_IDS.has(route[0] as TabId) ? route[0] as TabId : 'knowledge'
	const routeDetail = route[1]

	const reloadUser = useCallback(async () => {
		setUser(await fetchMe())
	}, [])

	useEffect(() => {
		let cancelled = false
		let retryTimer: number | undefined

		async function loadInitialUser() {
			try {
				const nextUser = await fetchMe()
				if (!cancelled) setUser(nextUser)
			} catch {
				if (!cancelled) retryTimer = window.setTimeout(() => void loadInitialUser(), 1000)
			}
		}

		void loadInitialUser()

		return () => {
			cancelled = true
			window.clearTimeout(retryTimer)
		}
	}, [])

	async function signOut() {
		await fetch('/api/auth/signout', {
			method: 'POST',
		})

		setUser(null)
	}

	useEffect(() => {
		if (!user) return
		if (!TAB_IDS.has(route[0] as TabId) || (activeTab === 'admin' && !user.isCommittee)) {
			router.replace('/play/knowledge')
			return
		}

		let canonicalPath: string | null = null
		if (activeTab === 'admin') canonicalPath = `/play/admin/${ADMIN_SECTIONS.has(routeDetail ?? '') ? routeDetail : 'members'}`
		else if (activeTab === 'misc') canonicalPath = `/play/misc/${MISC_SECTIONS.has(routeDetail ?? '') ? routeDetail : 'settings'}`
		else if (activeTab === 'dailies' || activeTab === 'charms' || activeTab === 'claims' || activeTab === 'fishing') canonicalPath = `/play/${activeTab}`
		else if (route.length > 2) canonicalPath = routeDetail ? `/play/${activeTab}/${encodeURIComponent(routeDetail)}` : `/play/${activeTab}`

		if (canonicalPath && pathname !== canonicalPath) router.replace(canonicalPath)
	}, [activeTab, pathname, route, routeDetail, router, user])

	const openKnowledge = useCallback((pageId: string, replace = false) => {
		const href = `/play/knowledge/${encodeURIComponent(pageId)}`
		navigate(href, replace)
	}, [])

	const openShop = useCallback((itemId: string | null, replace = false) => {
		const href = itemId ? `/play/shop/${encodeURIComponent(itemId)}` : '/play/shop'
		navigate(href, replace)
	}, [])

	const openPlayer = useCallback((playerName: string | null, replace = false) => {
		const href = playerName ? `/play/players/${encodeURIComponent(playerName)}` : '/play/players'
		navigate(href, replace)
	}, [])

	return (
		<SitePage background={background} splash={splash}>
				{user === undefined && (
					<section className="authCard">
						<div className="authForm">
							<p>Loading...</p>
						</div>
					</section>
				)}

				{user === null && (
					<AuthPanel onSignedIn={reloadUser} />
				)}

				{user && (
					<section className="dashboard">
						<div className="dashboardTop">
							<div>
								<h2>Dashboard</h2>
								<p>
									<Link
										className="signedInPlayer"
										href={`/play/players/${encodeURIComponent(user.minecraftUsername)}`}
										onNavigate={(event) => {
											event.preventDefault()
											openPlayer(user.minecraftUsername)
										}}
									>
										Signed in as <strong><PlayerName name={user.minecraftUsername} color={user.color} /></strong>.
									</Link>
								</p>
							</div>

							<button type="button" onClick={signOut}>
								Sign out
							</button>
						</div>

						<nav className="dashboardTabs" aria-label="Dashboard sections">
							{TAB_LINKS.filter((tab) => tab.id !== 'admin' || user.isCommittee).map((tab) => (
								<Link key={tab.id} href={tab.href} className={activeTab === tab.id ? 'active' : ''} onNavigate={(event) => {
									event.preventDefault()
									navigate(tab.href)
								}}>
									{tab.label}
								</Link>
							))}
						</nav>

						<div className="dashboardPanel">
							{activeTab === 'dailies' && <DailiesTab />}
							{activeTab === 'knowledge' && <KnowledgeTab pageId={routeDetail} onSelectPage={openKnowledge} />}
							{activeTab === 'charms' && <CharmsTab />}
							{activeTab === 'shop' && <ShopTab itemId={routeDetail} onSelectItem={openShop} />}
							{activeTab === 'claims' && <ClaimsTab />}
							{activeTab === 'fishing' && <FishingTab onSelectPlayer={openPlayer} />}
							{activeTab === 'players' && <PlayersTab playerName={routeDetail} onSelectPlayer={openPlayer} />}
							{activeTab === 'admin' && user.isCommittee && <AdminTab isSuperAdmin={user.isSuperAdmin} section={routeDetail} />}
							{activeTab === 'misc' && <MiscTab section={routeDetail} />}
						</div>
					</section>
				)}
		</SitePage>
	)
}

function navigate(href: string, replace = false) {
	window.history[replace ? 'replaceState' : 'pushState'](null, '', href)
}

function decodePathSegment(value: string) {
	try {
		return decodeURIComponent(value)
	} catch {
		return ''
	}
}
