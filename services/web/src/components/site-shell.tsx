'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthPanel } from '@/components/auth-panel';
import { SitePage } from '@/components/site-page';
import { AdminTab } from '@/components/admin-tab';
import { DailiesTab } from '@/components/dailies-tab';
import { KnowledgeTab } from '@/components/knowledge-tab';
import { MiscTab } from '@/components/misc-tab';
import { PlayersTab } from '@/components/players-tab';
import { ShopTab } from '@/components/shop-tab';
import { FishingTab } from '@/components/fishing-tab';
import { ClaimsTab } from '@/components/claims-tab';
import { CharmsTab } from '@/components/charms-tab';
import { PlayerName } from '@/components/player-name';
import { DynamicCountdowns } from '@/components/dynamic-countdowns';

interface SessionUser {
	id: number;
	minecraftUsername: string;
	color: string;
	whitelisted: boolean;
	rulesAccepted: boolean;
	isMember: boolean;
	isCommittee: boolean;
	isSuperAdmin: boolean;
}

interface OnlinePlayer {
	minecraftUsername: string;
	color: string;
}

type TabId =
	| 'dailies'
	| 'knowledge'
	| 'charms'
	| 'shop'
	| 'claims'
	| 'fishing'
	| 'players'
	| 'admin'
	| 'misc';

const TAB_IDS = new Set<TabId>([
	'dailies',
	'knowledge',
	'charms',
	'shop',
	'claims',
	'fishing',
	'players',
	'admin',
	'misc',
]);
const ADMIN_SECTIONS = new Set([
	'members',
	'claims',
	'whitelist',
	'bans',
	'gifts',
	'countdowns',
	'discord-commands',
	'dailies',
]);
const MISC_SECTIONS = new Set(['settings', 'gift-codes']);
const SERVER_IP = 'mmuminecraftsociety.co.uk';
const TAB_LINKS: { id: TabId; label: string; emoji: string; href: string }[] = [
	{ id: 'knowledge', label: 'Knowledge', emoji: '📖', href: '/play/knowledge' },
	{ id: 'dailies', label: 'Dailies', emoji: '📋', href: '/play/dailies' },
	{ id: 'charms', label: 'Charms', emoji: '✨', href: '/play/charms' },
	{ id: 'shop', label: 'Shop', emoji: '🛍️', href: '/play/shop' },
	{ id: 'claims', label: 'Claims', emoji: '🔒', href: '/play/claims' },
	{ id: 'fishing', label: 'Fishing', emoji: '🪝', href: '/play/fishing' },
	{ id: 'players', label: 'Players', emoji: '👤', href: '/play/players' },
	{ id: 'admin', label: 'Admin', emoji: '🪄', href: '/play/admin/members' },
	{ id: 'misc', label: 'Misc', emoji: '⚙️', href: '/play/misc/settings' },
];

async function fetchMe(): Promise<SessionUser | null> {
	const response = await fetch('/api/auth/me', {
		cache: 'no-store',
	});

	if (!response.ok) {
		throw new Error('Failed to check session');
	}

	const data = (await response.json().catch(() => null)) as { user?: SessionUser | null } | null;
	return data?.user ?? null;
}

export function SiteShell({ background, splash }: { background: string; splash: string }) {
	const pathname = usePathname();
	const router = useRouter();
	const [user, setUser] = useState<SessionUser | null | undefined>(undefined);
	const [onlinePlayers, setOnlinePlayers] = useState<OnlinePlayer[] | null>(null);
	const [copyLabel, setCopyLabel] = useState('Copy IP');
	const [menuOpen, setMenuOpen] = useState(false);
	const [headerHidden, setHeaderHidden] = useState(false);
	const route = useMemo(() => pathname.split('/').slice(2).map(decodePathSegment), [pathname]);
	const activeTab = TAB_IDS.has(route[0] as TabId) ? (route[0] as TabId) : 'knowledge';
	const routeDetail = route.at(1);

	const reloadUser = useCallback(async () => {
		setUser(await fetchMe());
	}, []);

	useEffect(() => {
		let cancelled = false;
		let retryTimer: number | undefined;

		async function loadInitialUser() {
			try {
				const nextUser = await fetchMe();
				if (!cancelled) setUser(nextUser);
			} catch {
				if (!cancelled) retryTimer = window.setTimeout(() => void loadInitialUser(), 1000);
			}
		}

		void loadInitialUser();

		return () => {
			cancelled = true;
			window.clearTimeout(retryTimer);
		};
	}, []);

	useEffect(() => {
		if (!user) return;

		let cancelled = false;
		async function loadOnlinePlayers() {
			try {
				const response = await fetch('/api/players/online', { cache: 'no-store' });
				if (!response.ok) return;
				const data = (await response.json()) as { players?: OnlinePlayer[] };
				if (!cancelled) setOnlinePlayers(data.players ?? []);
			} catch {
				// Keep the last successful roster during a temporary server outage.
			}
		}

		void loadOnlinePlayers();
		const timer = window.setInterval(() => void loadOnlinePlayers(), 60_000);
		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, [user]);

	async function signOut() {
		await fetch('/api/auth/signout', {
			method: 'POST',
		});

		setMenuOpen(false);
		setUser(null);
		setOnlinePlayers(null);
	}

	async function copyServerIp() {
		await navigator.clipboard.writeText(SERVER_IP);
		setCopyLabel('Copied');
		window.setTimeout(() => {
			setCopyLabel('Copy IP');
		}, 1000);
	}

	useEffect(() => {
		if (!user) return;
		if (!TAB_IDS.has(route[0] as TabId) || (activeTab === 'admin' && !user.isCommittee)) {
			router.replace('/play/knowledge');
			return;
		}

		let canonicalPath: string | null = null;
		if (activeTab === 'admin')
			canonicalPath = `/play/admin/${ADMIN_SECTIONS.has(routeDetail ?? '') ? routeDetail : 'members'}`;
		else if (activeTab === 'misc')
			canonicalPath = `/play/misc/${MISC_SECTIONS.has(routeDetail ?? '') ? routeDetail : 'settings'}`;
		else if (
			activeTab === 'dailies' ||
			activeTab === 'charms' ||
			activeTab === 'claims' ||
			activeTab === 'fishing'
		)
			canonicalPath = `/play/${activeTab}`;
		else if (route.length > 2)
			canonicalPath = routeDetail
				? `/play/${activeTab}/${encodeURIComponent(routeDetail)}`
				: `/play/${activeTab}`;

		if (canonicalPath && pathname !== canonicalPath) router.replace(canonicalPath);
	}, [activeTab, pathname, route, routeDetail, router, user]);

	useEffect(() => {
		let lastY = window.scrollY;
		function updateHeader() {
			const currentY = window.scrollY;
			if (Math.abs(currentY - lastY) < 8) return;
			setHeaderHidden(!menuOpen && currentY > lastY && currentY > 64);
			lastY = currentY;
		}

		window.addEventListener('scroll', updateHeader, { passive: true });
		return () => window.removeEventListener('scroll', updateHeader);
	}, [menuOpen]);

	useEffect(() => {
		if (!menuOpen) return;
		const previousOverflow = document.body.style.overflow;
		function closeOnEscape(event: KeyboardEvent) {
			if (event.key === 'Escape') setMenuOpen(false);
		}

		document.body.style.overflow = 'hidden';
		window.addEventListener('keydown', closeOnEscape);
		return () => {
			document.body.style.overflow = previousOverflow;
			window.removeEventListener('keydown', closeOnEscape);
		};
	}, [menuOpen]);

	const openKnowledge = useCallback((pageId: string, replace = false) => {
		const href = `/play/knowledge/${encodeURIComponent(pageId)}`;
		navigate(href, replace);
	}, []);

	const openShop = useCallback((itemId: string | null, replace = false) => {
		const href = itemId ? `/play/shop/${encodeURIComponent(itemId)}` : '/play/shop';
		navigate(href, replace);
	}, []);

	const openPlayer = useCallback((playerName: string | null, replace = false) => {
		const href = playerName
			? `/play/players/${encodeURIComponent(playerName)}`
			: '/play/players';
		navigate(href, replace);
	}, []);

	return (
		<SitePage
			background={background}
			splash={splash}
			className={`playPage ${menuOpen ? 'menuOpen' : ''} ${headerHidden ? 'mobileHeaderHidden' : ''}`}
			headerActions={
				user && (
					<button
						className="mobileMenuButton"
						type="button"
						aria-label={menuOpen ? 'Close menu' : 'Open menu'}
						aria-expanded={menuOpen}
						aria-controls="dashboard-menu"
						onClick={() => setMenuOpen((open) => !open)}
					>
						<span />
						<span />
						<span />
					</button>
				)
			}
		>
			<DynamicCountdowns className="desktopCountdowns" />
			{user === undefined && (
				<section className="authCard">
					<div className="authForm">
						<p>Loading...</p>
					</div>
				</section>
			)}

			{user === null && <AuthPanel onSignedIn={reloadUser} />}

			{user && (
				<section className="dashboard">
					<div className="dashboardMenu" id="dashboard-menu">
						<div className="dashboardTop">
							<div className="dashboardIdentity">
								<p className="dashboardEyebrow">
									Signed in as{' '}
									<Link
										className="signedInPlayer"
										href={`/play/players/${encodeURIComponent(user.minecraftUsername)}`}
										onNavigate={(event) => {
											event.preventDefault();
											setMenuOpen(false);
											openPlayer(user.minecraftUsername);
										}}
									>
										<PlayerName
											name={user.minecraftUsername}
											color={user.color}
										/>
									</Link>
									{' - '}
									<button className="textAction" type="button" onClick={signOut}>
										Sign out
									</button>
								</p>
								<p className="serverDetails">
									Java Edition 26.2 - IP: <strong>{SERVER_IP}</strong> -{' '}
									<button
										className="textAction"
										type="button"
										onClick={() => void copyServerIp()}
									>
										{copyLabel}
									</button>
								</p>
							</div>

							<div className="onlinePlayers">
								<p className="dashboardEyebrow">Online players</p>
								<div className="onlinePlayerList">
									{onlinePlayers?.map((player) => (
										<Link
											key={player.minecraftUsername}
											href={`/play/players/${encodeURIComponent(player.minecraftUsername)}`}
											onNavigate={(event) => {
												event.preventDefault();
												setMenuOpen(false);
												openPlayer(player.minecraftUsername);
											}}
										>
											<PlayerName
												name={player.minecraftUsername}
												color={player.color}
											/>
										</Link>
									))}
									{onlinePlayers?.length === 0 && (
										<span className="onlinePlayersEmpty">
											No players online
										</span>
									)}
								</div>
							</div>
						</div>

						<div className="mobileMenuMeta">
							<div className="mobileMenuBlock">
								<p>
									Signed in as{' '}
									<Link
										className="signedInPlayer"
										href={`/play/players/${encodeURIComponent(user.minecraftUsername)}`}
										onNavigate={(event) => {
											event.preventDefault();
											setMenuOpen(false);
											openPlayer(user.minecraftUsername);
										}}
									>
										<PlayerName
											name={user.minecraftUsername}
											color={user.color}
										/>
									</Link>
								</p>
								<button className="textAction" type="button" onClick={signOut}>
									Sign out
								</button>
							</div>

							<div className="mobileMenuBlock">
								<p>Server:</p>
								<p>
									Version: <strong>Java Edition 26.2</strong>
								</p>
								<p>
									IP: <strong>{SERVER_IP}</strong>
								</p>
							</div>

							<div className="mobileMenuBlock">
								<p>Online players</p>
								<div className="onlinePlayerList">
									{onlinePlayers?.map((player) => (
										<Link
											key={player.minecraftUsername}
											href={`/play/players/${encodeURIComponent(player.minecraftUsername)}`}
											onNavigate={(event) => {
												event.preventDefault();
												setMenuOpen(false);
												openPlayer(player.minecraftUsername);
											}}
										>
											<PlayerName
												name={player.minecraftUsername}
												color={player.color}
											/>
										</Link>
									))}
									{onlinePlayers?.length === 0 && (
										<span className="onlinePlayersEmpty">
											No players online
										</span>
									)}
								</div>
							</div>
						</div>

						<nav className="dashboardTabs" aria-label="Dashboard sections">
							{TAB_LINKS.filter((tab) => tab.id !== 'admin' || user.isCommittee).map(
								(tab) => (
									<Link
										key={tab.id}
										href={tab.href}
										className={activeTab === tab.id ? 'active' : ''}
										aria-current={activeTab === tab.id ? 'page' : undefined}
										onNavigate={(event) => {
											event.preventDefault();
											setMenuOpen(false);
											navigate(tab.href);
										}}
									>
										<span className="dashboardTabEmoji" aria-hidden="true">
											{tab.emoji}
										</span>
										<span>{tab.label}</span>
									</Link>
								),
							)}
						</nav>

						<DynamicCountdowns className="mobileCountdowns" />
					</div>

					<div className="dashboardPanel">
						{activeTab === 'dailies' && <DailiesTab />}
						{activeTab === 'knowledge' && (
							<KnowledgeTab pageId={routeDetail} onSelectPage={openKnowledge} />
						)}
						{activeTab === 'charms' && <CharmsTab />}
						{activeTab === 'shop' && (
							<ShopTab itemId={routeDetail} onSelectItem={openShop} />
						)}
						{activeTab === 'claims' && <ClaimsTab />}
						{activeTab === 'fishing' && <FishingTab onSelectPlayer={openPlayer} />}
						{activeTab === 'players' && (
							<PlayersTab playerName={routeDetail} onSelectPlayer={openPlayer} />
						)}
						{activeTab === 'admin' && user.isCommittee && (
							<AdminTab isSuperAdmin={user.isSuperAdmin} section={routeDetail} />
						)}
						{activeTab === 'misc' && <MiscTab section={routeDetail} />}
					</div>
				</section>
			)}
		</SitePage>
	);
}

function navigate(href: string, replace = false) {
	window.history[replace ? 'replaceState' : 'pushState'](null, '', href);
}

function decodePathSegment(value: string) {
	try {
		return decodeURIComponent(value);
	} catch {
		return '';
	}
}
