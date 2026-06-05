'use client'

import { useCallback, useEffect, useState } from 'react'
import { AuthPanel } from '@/components/auth-panel'
import { BackgroundGrid } from '@/components/background-grid'
import { DailiesTab } from '@/components/dailies-tab'
import { KnowledgeTab } from '@/components/knowledge-tab'
import { PlayersTab } from '@/components/players-tab'
import { ShopTab } from '@/components/shop-tab'

interface SessionUser {
	id: number
	email: string
	minecraftUsername: string
	whitelisted: boolean
	rulesAccepted: boolean
}

type TabId = 'dailies' | 'knowledge' | 'shop' | 'players'

async function fetchMe(): Promise<SessionUser | null> {
	const response = await fetch('/api/auth/me', {
		cache: 'no-store',
	})

	if (!response.ok) {
		return null
	}

	const data = await response.json().catch(() => null) as { user?: SessionUser | null } | null
	return data?.user ?? null
}

export function SiteShell({ images }: { images: string[] }) {
	const [user, setUser] = useState<SessionUser | null | undefined>(undefined)
	const [activeTab, setActiveTab] = useState<TabId>('dailies')

	const reloadUser = useCallback(async () => {
		setUser(await fetchMe())
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitialUser() {
			const nextUser = await fetchMe()
			if (!cancelled) {
				setUser(nextUser)
			}
		}

		void loadInitialUser()

		return () => {
			cancelled = true
		}
	}, [])

	async function signOut() {
		await fetch('/api/auth/signout', {
			method: 'POST',
		})

		setUser(null)
	}

	return (
		<main className="page">
			<BackgroundGrid images={images} />

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
								className={activeTab === 'dailies' ? 'active' : ''}
								onClick={() => setActiveTab('dailies')}
							>
								Dailies
							</button>
							<button
								type="button"
								className={activeTab === 'knowledge' ? 'active' : ''}
								onClick={() => setActiveTab('knowledge')}
							>
								Knowledge
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
								className={activeTab === 'players' ? 'active' : ''}
								onClick={() => setActiveTab('players')}
							>
								Players
							</button>
						</nav>

						<div className="dashboardPanel">
							{activeTab === 'dailies' && <DailiesTab />}
							{activeTab === 'knowledge' && <KnowledgeTab />}
							{activeTab === 'shop' && <ShopTab />}
							{activeTab === 'players' && <PlayersTab />}
						</div>
					</section>
				)}
			</div>
		</main>
	)
}
