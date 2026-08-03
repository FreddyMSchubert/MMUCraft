'use client'

import { useCallback, useEffect, useState } from 'react'
import { ASSETS } from '@/lib/assets'
import { AuthPanel } from '@/components/auth-panel'
import { AdminTab } from '@/components/admin-tab'
import { DailiesTab } from '@/components/dailies-tab'
import { KnowledgeTab } from '@/components/knowledge-tab'
import { MiscTab } from '@/components/misc-tab'
import { PlayersTab } from '@/components/players-tab'
import { ShopTab } from '@/components/shop-tab'
import { FishingTab } from '@/components/fishing-tab'
import { ClaimsTab } from '@/components/claims-tab'

interface SessionUser {
	id: number
	minecraftUsername: string
	whitelisted: boolean
	rulesAccepted: boolean
	isMember: boolean
	isCommittee: boolean
	isSuperAdmin: boolean
}

type TabId = 'dailies' | 'knowledge' | 'shop' | 'claims' | 'fishing' | 'players' | 'admin' | 'misc'

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

export function SiteShell() {
	const [user, setUser] = useState<SessionUser | null | undefined>(undefined)
	const [activeTab, setActiveTab] = useState<TabId>('knowledge')

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
		setActiveTab('knowledge')
	}

	return (
		<main className="page">
			<div className="siteBackground" style={{ backgroundImage: `url(${ASSETS.background})` }} aria-hidden="true" />

			<div className="content">
				<header className="siteHeader">
					<h1 className="title">MMU Minecraft Society</h1>
				</header>

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
									Signed in as <strong>{user.minecraftUsername}</strong>.
								</p>
							</div>

							<button type="button" onClick={signOut}>
								Sign out
							</button>
						</div>

						<nav className="dashboardTabs" aria-label="Dashboard sections">
							<button
								type="button"
								className={activeTab === 'knowledge' ? 'active' : ''}
								onClick={() => setActiveTab('knowledge')}
							>
								Knowledge
							</button>
							<button
								type="button"
								className={activeTab === 'dailies' ? 'active' : ''}
								onClick={() => setActiveTab('dailies')}
							>
								Dailies
							</button>
							<button
								type="button"
								className={activeTab === 'shop' ? 'active' : ''}
								onClick={() => setActiveTab('shop')}
							>
								Shop
							</button>
							<button
								type="button"
								className={activeTab === 'claims' ? 'active' : ''}
								onClick={() => setActiveTab('claims')}
							>
								Claims
							</button>
							<button
								type="button"
								className={activeTab === 'fishing' ? 'active' : ''}
								onClick={() => setActiveTab('fishing')}
							>
								Fishing
							</button>
							<button
								type="button"
								className={activeTab === 'players' ? 'active' : ''}
								onClick={() => setActiveTab('players')}
							>
								Players
							</button>
							{user.isCommittee && (
								<button
									type="button"
									className={activeTab === 'admin' ? 'active' : ''}
									onClick={() => setActiveTab('admin')}
								>
									Admin
								</button>
							)}
							<button
								type="button"
								className={activeTab === 'misc' ? 'active' : ''}
								onClick={() => setActiveTab('misc')}
							>
								Misc
							</button>
						</nav>

						<div className="dashboardPanel">
							{activeTab === 'dailies' && <DailiesTab />}
							{activeTab === 'knowledge' && <KnowledgeTab />}
							{activeTab === 'shop' && <ShopTab />}
							{activeTab === 'claims' && <ClaimsTab />}
							{activeTab === 'fishing' && <FishingTab />}
							{activeTab === 'players' && <PlayersTab />}
							{activeTab === 'admin' && user.isCommittee && <AdminTab isSuperAdmin={user.isSuperAdmin} />}
							{activeTab === 'misc' && <MiscTab />}
						</div>
					</section>
				)}
			</div>
		</main>
	)
}
