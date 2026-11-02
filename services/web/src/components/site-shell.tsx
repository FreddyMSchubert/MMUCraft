'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { AuthPanel } from '@/components/auth-panel';
import { SitePage } from '@/components/site-page';
import { AdminTab } from '@/components/admin-tab';
import { DailiesTab } from '@/components/dailies-tab';
import { KnowledgeTab } from '@/components/knowledge-tab';
import { MiscTab } from '@/components/misc-tab';
import { BecomeMemberTab } from '@/components/become-member-tab';
import { PlayersTab } from '@/components/players-tab';
import { ShopTab } from '@/components/shop-tab';
import { FishingTab } from '@/components/fishing-tab';
import { ClaimsTab } from '@/components/claims-tab';
import { DucksTab } from '@/components/ducks-tab';
import { CharmsTab } from '@/components/charms-tab';
import { PlayerName, type PlayerEmoji } from '@/components/player-name';
import { DynamicCountdowns } from '@/components/dynamic-countdowns';
import { type EventSummary } from '@/components/surprising-saturday-tab';
import { EventsTab } from '@/components/events-tab';
import { type BuildEventSummary } from '@/components/build-events-tab';
import { normalizeAdminSection } from '@/components/admin/admin-data.types';

interface SessionUser {
	id: number;
	minecraftUsername: string;
	skinUrl: string | null;
	color: string;
	isMember: boolean;
	canBecomeMember: boolean;
	isCommittee: boolean;
	isSuperAdmin: boolean;
	customEmojis: PlayerEmoji[];
}

interface OnlinePlayer {
	minecraftUsername: string;
	color: string;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
}

type TabId =
	| 'dailies'
	| 'knowledge'
	| 'charms'
	| 'shop'
	| 'claims'
	| 'ducks'
	| 'fishing'
	| 'players'
	| 'admin'
	| 'membership'
	| 'event'
	| 'misc';

interface TabLink {
	id: TabId;
	label: string;
	emoji: string;
	href: string;
}

const TAB_IDS = new Set<TabId>([
	'dailies',
	'knowledge',
	'charms',
	'shop',
	'claims',
	'ducks',
	'fishing',
	'players',
	'admin',
	'membership',
	'event',
	'misc',
]);
const MISC_SECTIONS = new Set(['settings', 'gift-codes', 'referrals']);
const SERVER_IP = 'mmuminecraftsociety.co.uk';
const TAB_LINKS: TabLink[] = [
	{ id: 'knowledge', label: 'Knowledge', emoji: '📖', href: '/play/knowledge' },
	{ id: 'dailies', label: 'Dailies', emoji: '📋', href: '/play/dailies' },
	{ id: 'charms', label: 'Charms', emoji: '✨', href: '/play/charms' },
	{ id: 'shop', label: 'Shop', emoji: '🛍️', href: '/play/shop' },
	{ id: 'claims', label: 'Claims', emoji: '🔒', href: '/play/claims' },
	{ id: 'ducks', label: 'Ducks', emoji: '🦆', href: '/play/ducks' },
	{ id: 'fishing', label: 'Fishing', emoji: '🐟', href: '/play/fishing' },
	{ id: 'players', label: 'Players', emoji: '👤', href: '/play/players' },
	{ id: 'membership', label: 'Become a member', emoji: '⭐', href: '/play/membership' },
	{ id: 'admin', label: 'Admin', emoji: '👨‍💻', href: '/play/admin/members' },
	{ id: 'misc', label: 'Misc', emoji: '⚙️', href: '/play/misc/settings' },
];
const EVENT_LINK: TabLink = {
	id: 'event',
	label: 'Events',
	emoji: '🎉',
	href: '/play/event',
};

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
	const [ducksEnabled, setDucksEnabled] = useState<boolean | null>(null);
	const [onlinePlayers, setOnlinePlayers] = useState<OnlinePlayer[] | null>(null);
	const [capacity, setCapacity] = useState<{
		servers: {
			name: string;
			playerCount: number | null;
			maxPlayers: number;
			memberWaitingCount?: number;
			waitingCount?: number;
		}[];
	} | null>(null);
	const [events, setEvents] = useState<EventSummary[]>([]);
	const [buildEvents, setBuildEvents] = useState<BuildEventSummary[]>([]);
	const [copyLabel, setCopyLabel] = useState('Copy IP');
	const [menuOpen, setMenuOpen] = useState(false);
	const [headerHidden, setHeaderHidden] = useState(false);
	const route = useMemo(() => pathname.split('/').slice(2).map(decodePathSegment), [pathname]);
	const activeTab = TAB_IDS.has(route[0] as TabId) ? (route[0] as TabId) : 'knowledge';
	const routeDetail = route.at(1);
	const activeBuildEvents = buildEvents.filter((event) =>
		['building', 'preparing', 'voting'].includes(event.status),
	);
	const hasLiveEvent = activeBuildEvents.length > 0;
	const isCommittee = user?.isCommittee;
	const tabs = useMemo(
		() =>
			(hasLiveEvent
				? [EVENT_LINK, ...TAB_LINKS]
				: [...TAB_LINKS.slice(0, -1), EVENT_LINK, TAB_LINKS[TAB_LINKS.length - 1]]
			).filter(
				(tab) =>
					(tab.id !== 'admin' || isCommittee) &&
					(tab.id !== 'membership' || user?.canBecomeMember === true) &&
					(tab.id !== 'ducks' || ducksEnabled === true),
			),
		[hasLiveEvent, isCommittee, ducksEnabled, user?.canBecomeMember],
	);

	useEffect(() => {
		if (!user) return;
		let cancelled = false;
		async function refreshDucks() {
			try {
				const response = await fetch('/api/ducks/availability', { cache: 'no-store' });
				if (!response.ok) throw new Error('Duck availability unavailable');
				const data = (await response.json()) as { enabled?: boolean };
				if (!cancelled) setDucksEnabled(data.enabled === true);
			} catch {
				if (!cancelled) setDucksEnabled(false);
			}
		}
		void refreshDucks();
		const timer = window.setInterval(() => void refreshDucks(), 15_000);
		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, [user]);

	useEffect(() => {
		let cancelled = false;
		async function refreshEvents() {
			try {
				const response = await fetch('/api/surprising-saturday', { cache: 'no-store' });
				if (response.ok && !cancelled) setEvents((await response.json()) as EventSummary[]);
			} catch {
				// Keep the last successful history during an API outage.
			}
		}
		void refreshEvents();
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		let timer: number | undefined;
		async function refreshBuildEvents() {
			try {
				const response = await fetch('/api/build-events', { cache: 'no-store' });
				if (response.ok && !cancelled)
					setBuildEvents((await response.json()) as BuildEventSummary[]);
			} catch {
				// The next poll retries when the API is unavailable.
			} finally {
				if (!cancelled) timer = window.setTimeout(refreshBuildEvents, 15_000);
			}
		}
		void refreshBuildEvents();
		return () => {
			cancelled = true;
			window.clearTimeout(timer);
		};
	}, []);

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
				const response = await fetch('/api/velocity/online-players', { cache: 'no-store' });
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

	useEffect(() => {
		if (!user) return;
		let cancelled = false;
		async function refreshCapacity() {
			try {
				const response = await fetch('/api/velocity/capacity', { cache: 'no-store' });
				if (!response.ok) return;
				const next = (await response.json()) as {
					servers: {
						name: string;
						playerCount: number | null;
						maxPlayers: number;
						memberWaitingCount?: number;
						waitingCount?: number;
					}[];
				};
				if (!cancelled) setCapacity(next);
			} catch {
				// Keep the last known count while the server is unavailable.
			}
		}
		void refreshCapacity();
		const timer = window.setInterval(() => void refreshCapacity(), 5_000);
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
		setDucksEnabled(null);
		setOnlinePlayers(null);
		setCapacity(null);
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
		if (ducksEnabled === false && activeTab === 'ducks') {
			router.replace('/play/knowledge');
			return;
		}
		if (ducksEnabled === false && activeTab === 'admin' && routeDetail === 'ducks') {
			router.replace('/play/admin/members');
			return;
		}
		if (
			!TAB_IDS.has(route[0] as TabId) ||
			(activeTab === 'admin' && !user.isCommittee) ||
			(activeTab === 'membership' && !user.canBecomeMember)
		) {
			router.replace('/play/knowledge');
			return;
		}

		let canonicalPath: string | null = null;
		if (activeTab === 'admin')
			canonicalPath =
				routeDetail === 'build-events' && route.length <= 4
					? '/play/admin/build-events' +
						(route[2] ? '/' + encodeURIComponent(route[2]) : '') +
						(route[3] ? '/' + encodeURIComponent(route[3]) : '')
					: `/play/admin/${normalizeAdminSection(routeDetail)}`;
		else if (activeTab === 'misc')
			canonicalPath = `/play/misc/${MISC_SECTIONS.has(routeDetail ?? '') ? routeDetail : 'settings'}`;
		else if (
			activeTab === 'dailies' ||
			activeTab === 'charms' ||
			activeTab === 'claims' ||
			activeTab === 'ducks' ||
			activeTab === 'fishing' ||
			activeTab === 'membership'
		)
			canonicalPath = `/play/${activeTab}`;
		else if (route.length > 2 && !(activeTab === 'event' && routeDetail === 'build'))
			canonicalPath = routeDetail
				? `/play/${activeTab}/${encodeURIComponent(routeDetail)}`
				: `/play/${activeTab}`;

		if (canonicalPath && pathname !== canonicalPath) router.replace(canonicalPath);
	}, [activeTab, ducksEnabled, pathname, route, routeDetail, router, user]);

	useEffect(() => {
		let lastY = window.scrollY;
		function updateHeader() {
			const currentY = window.scrollY;
			if (Math.abs(currentY - lastY) < 8) return;
			setHeaderHidden(!menuOpen && currentY > lastY && currentY > 64);
			lastY = currentY;
		}

		window.addEventListener('scroll', updateHeader, { passive: true });
		return () => {
			window.removeEventListener('scroll', updateHeader);
		};
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

	const openKnowledge = useCallback(
		(pageId: string, replace = false, highlightTerms: string[] = []) => {
			const params = new URLSearchParams();
			for (const term of highlightTerms) params.append('find', term);
			const query = params.size ? `?${params.toString()}` : '';
			navigate(`/play/knowledge/${encodeURIComponent(pageId)}${query}`, replace);
		},
		[],
	);
	const searchKnowledge = useCallback((query: string) => {
		navigate(`/play/knowledge/search?q=${encodeURIComponent(query)}`);
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
						onClick={() => {
							setMenuOpen((open) => !open);
						}}
					>
						<span />
						<span />
						<span />
					</button>
				)
			}
		>
			<DynamicCountdowns className="desktopCountdowns" />
			{user &&
				activeBuildEvents.map((event) => (
					<Link
						className="eventBanner buildEventBanner"
						key={event.id}
						href={`/play/event/build/${event.id}`}
					>
						<span className="buildEventBannerLabel">
							Build competition ·{' '}
							{event.status === 'building'
								? 'Building open'
								: event.status === 'preparing'
									? 'Voting opens soon'
									: 'Voting open'}
						</span>
						<span className="eventBannerContent">
							<strong>{event.title}</strong>
							{event.shortDescription && <span>{event.shortDescription}</span>}
						</span>
						<span className="eventBannerAction">View competition →</span>
					</Link>
				))}
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
											isCommittee={user.isCommittee}
											customEmojis={user.customEmojis}
										/>
									</Link>
									{' - '}
									<button className="textAction" type="button" onClick={signOut}>
										Sign out
									</button>
								</p>
								<p className="serverDetails">
									Java Edition 26.3 - IP: <strong>{SERVER_IP}</strong> -{' '}
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
								<p className="dashboardEyebrow">
									Online players
									{capacity && (
										<CapacityCount
											capacity={capacity}
											showWaitingCounts={user.isCommittee}
										/>
									)}
								</p>
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
												isCommittee={player.isCommittee}
												customEmojis={player.customEmojis}
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
											isCommittee={user.isCommittee}
											customEmojis={user.customEmojis}
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
									Version: <strong>Java Edition 26.3</strong>
								</p>
								<p>
									IP: <strong>{SERVER_IP}</strong>
								</p>
							</div>

							<div className="mobileMenuBlock">
								<p>
									Online players
									{capacity && (
										<CapacityCount
											capacity={capacity}
											showWaitingCounts={user.isCommittee}
										/>
									)}
								</p>
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
												isCommittee={player.isCommittee}
												customEmojis={player.customEmojis}
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

						<DashboardNavigation
							key={user.id}
							userId={user.id}
							tabs={tabs}
							activeTab={activeTab}
							onNavigate={(href) => {
								setMenuOpen(false);
								navigate(href);
							}}
						/>

						<DynamicCountdowns className="mobileCountdowns" />
					</div>

					<div className="dashboardPanel">
						{activeTab === 'dailies' && <DailiesTab />}
						{activeTab === 'event' && (
							<EventsTab
								key={routeDetail ?? 'overview'}
								saturdayEvents={events}
								buildEvents={buildEvents}
								path={route.slice(1)}
							/>
						)}
						{activeTab === 'knowledge' && (
							<KnowledgeTab
								pageId={routeDetail}
								onSelectPage={openKnowledge}
								onSearch={searchKnowledge}
							/>
						)}
						{activeTab === 'charms' && <CharmsTab />}
						{activeTab === 'shop' && (
							<ShopTab
								itemId={routeDetail}
								skinUrl={user.skinUrl}
								onSelectItem={openShop}
							/>
						)}
						{activeTab === 'claims' && <ClaimsTab />}
						{activeTab === 'ducks' && ducksEnabled === true && <DucksTab />}
						{activeTab === 'fishing' && <FishingTab onSelectPlayer={openPlayer} />}
						{activeTab === 'membership' && user.canBecomeMember && (
							<BecomeMemberTab
								onPurchased={async () => {
									navigate('/play/shop');
									await reloadUser();
								}}
							/>
						)}
						{activeTab === 'players' && (
							<PlayersTab playerName={routeDetail} onSelectPlayer={openPlayer} />
						)}
						{activeTab === 'admin' && user.isCommittee && (
							<AdminTab
								isSuperAdmin={user.isSuperAdmin}
								section={routeDetail}
								subroute={route.slice(2)}
								ducksEnabled={ducksEnabled === true}
							/>
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

function DashboardNavigation({
	userId,
	tabs,
	activeTab,
	onNavigate,
}: {
	userId: number;
	tabs: TabLink[];
	activeTab: TabId;
	onNavigate: (href: string) => void;
}) {
	const navRef = useRef<HTMLElement>(null);
	const [recentTabs, setRecentTabs] = useState<TabId[]>(() => {
		try {
			const saved = JSON.parse(
				window.localStorage.getItem(`mmu-dashboard-recent-tabs-v1-${userId}`) ?? '[]',
			) as unknown;
			return Array.isArray(saved)
				? [
						...new Set(
							saved.filter(
								(id): id is TabId =>
									typeof id === 'string' && TAB_IDS.has(id as TabId),
							),
						),
					]
				: [];
		} catch {
			return [];
		}
	});
	const [compactTabs, setCompactTabs] = useState<Set<TabId>>(() => new Set());

	useLayoutEffect(() => {
		const nav = navRef.current;
		if (!nav) return;

		function fitLabels() {
			if (!nav) return;
			const links = Array.from(nav.querySelectorAll<HTMLAnchorElement>('a[data-tab-id]'));
			links.forEach((link) => {
				link.classList.remove('compact');
			});

			if (window.innerWidth > 700) {
				const priority = [activeTab, ...recentTabs, ...tabs.map((tab) => tab.id)];
				const gap = Number.parseFloat(getComputedStyle(nav).columnGap) || 0;
				const totalWidth = () =>
					links.reduce((width, link) => width + link.getBoundingClientRect().width, 0) +
					Math.max(links.length - 1, 0) * gap;

				for (const tab of [...tabs].sort(
					(a, b) => priority.indexOf(b.id) - priority.indexOf(a.id),
				)) {
					if (totalWidth() <= nav.clientWidth) break;
					if (tab.id !== activeTab) {
						links
							.find((link) => link.dataset.tabId === tab.id)
							?.classList.add('compact');
					}
				}
			}

			setCompactTabs(
				new Set(
					links
						.filter((link) => link.classList.contains('compact'))
						.map((link) => link.dataset.tabId as TabId),
				),
			);
		}

		fitLabels();
		const observer = new ResizeObserver(fitLabels);
		observer.observe(nav);
		let cancelled = false;
		void document.fonts.ready.then(() => {
			if (!cancelled) fitLabels();
		});
		return () => {
			cancelled = true;
			observer.disconnect();
		};
	}, [activeTab, recentTabs, tabs]);

	return (
		<nav ref={navRef} className="dashboardTabs" aria-label="Dashboard sections">
			{tabs.map((tab) => (
				<Link
					key={tab.id}
					data-tab-id={tab.id}
					href={tab.href}
					className={`${activeTab === tab.id ? 'active ' : ''}${compactTabs.has(tab.id) ? 'compact' : ''}`}
					aria-label={`${tab.emoji} ${tab.label}`}
					title={`${tab.emoji} ${tab.label}`}
					aria-current={activeTab === tab.id ? 'page' : undefined}
					onNavigate={(event) => {
						event.preventDefault();
						if (window.innerWidth > 700) {
							const nextRecent = [
								tab.id,
								...recentTabs.filter((id) => id !== tab.id),
							];
							setRecentTabs(nextRecent);
							try {
								window.localStorage.setItem(
									`mmu-dashboard-recent-tabs-v1-${userId}`,
									JSON.stringify(nextRecent),
								);
							} catch {
								// Navigation still works if browser storage is unavailable.
							}
						}
						onNavigate(tab.href);
					}}
				>
					<span className="dashboardTabEmoji" aria-hidden="true">
						{tab.emoji}
					</span>
					<span className="dashboardTabLabel" aria-hidden="true">
						{tab.label}
					</span>
				</Link>
			))}
		</nav>
	);
}

function CapacityCount({
	capacity,
	showWaitingCounts,
}: {
	capacity: {
		servers: {
			name: string;
			playerCount: number | null;
			maxPlayers: number;
			memberWaitingCount?: number;
			waitingCount?: number;
		}[];
	};
	showWaitingCounts: boolean;
}) {
	const servers = capacity.servers.filter((server) => server.name === 'main');
	return (
		<>
			{servers.map((server) => (
				<span key={server.name}>
					{' '}
					<span
						className={
							server.playerCount !== null && server.playerCount >= server.maxPlayers
								? 'playerCapacity-full'
								: undefined
						}
					>
						({server.playerCount ?? '?'}/{server.maxPlayers})
					</span>
					{showWaitingCounts &&
						(server.memberWaitingCount ?? 0) + (server.waitingCount ?? 0) > 0 && (
							<>
								{' '}
								(members waiting: {server.memberWaitingCount ?? 0}, others:{' '}
								{server.waitingCount ?? 0})
							</>
						)}
				</span>
			))}
		</>
	);
}

function decodePathSegment(value: string) {
	try {
		return decodeURIComponent(value);
	} catch {
		return '';
	}
}
