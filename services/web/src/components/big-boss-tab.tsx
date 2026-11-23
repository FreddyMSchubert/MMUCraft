'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useRef, useState } from 'react';

const TIME = new Intl.DateTimeFormat('en-GB', {
	day: 'numeric',
	month: 'short',
	hour: 'numeric',
	minute: '2-digit',
	hour12: true,
	timeZone: 'Europe/London',
});

interface Person {
	minecraftUsername: string;
	userId: number | null;
	avatarUrl: string | null;
}

interface Position {
	dimension: string;
	x: number;
	y: number;
	z: number;
	inClaim: boolean;
	sampledAtUnixMs: number | null;
}

interface Transition {
	id: number;
	atUnixMs: number;
	previous: Person | null;
	next: Person | null;
	reason: string;
	detail: string;
	reign: {
		holder: Person;
		startedAtUnixMs: number;
		endedAtUnixMs: number;
	} | null;
}

interface BossState {
	holder: Person | null;
	reignStartedAtUnixMs: number | null;
	latestTransitionId: number;
	online: boolean;
	position: Position | null;
}

interface HistoryPage {
	entries: Transition[];
	nextCursor: number | null;
}

function duration(ms: number) {
	const minutes = Math.max(0, Math.floor(ms / 60_000));
	const days = Math.floor(minutes / 1440);
	const hours = Math.floor((minutes % 1440) / 60);
	const values = [
		[days, 'day'],
		[hours, 'hour'],
		[minutes % 60, 'minute'],
	] as const;
	const parts = values
		.filter(([value]) => value > 0)
		.map(([value, unit]) => `${value} ${unit}${value === 1 ? '' : 's'}`);
	return parts.length ? parts.join(', ') : '0 minutes';
}

function positionForDisplay(
	state: BossState,
	previous: BossState | null,
	fetchedAt: number,
	now: number,
) {
	const position = state.position;
	if (!position) return null;
	if (!state.online) return position;
	if (
		!previous?.online ||
		!previous.position ||
		previous.holder?.minecraftUsername !== state.holder?.minecraftUsername ||
		previous.position.dimension !== position.dimension
	)
		return position;
	const fraction = Math.min(1, Math.max(0, (now - fetchedAt) / 5_000));
	return {
		...position,
		x: Math.round(previous.position.x + (position.x - previous.position.x) * fraction),
		y: Math.round(previous.position.y + (position.y - previous.position.y) * fraction),
		z: Math.round(previous.position.z + (position.z - previous.position.z) * fraction),
	};
}

function Head({
	person,
	large = false,
	link = true,
}: {
	person: Person | null;
	large?: boolean;
	link?: boolean;
}) {
	const content = person?.avatarUrl ? (
		<Image
			src={person.avatarUrl}
			alt=""
			width={large ? 104 : 42}
			height={large ? 104 : 42}
			unoptimized
		/>
	) : large ? (
		<span>👑</span>
	) : null;
	return person && link ? (
		<Link
			className={`bigBossHead ${large ? 'large' : ''}`}
			href={`/play/players/${encodeURIComponent(person.minecraftUsername)}`}
			aria-label={`View ${person.minecraftUsername}'s profile`}
		>
			{content}
		</Link>
	) : (
		<span className={`bigBossHead ${large ? 'large' : ''}`}>{content}</span>
	);
}

function transitionText(item: Transition) {
	const from = item.previous?.minecraftUsername;
	const to = item.next?.minecraftUsername;
	if (item.reason === 'seed') return `${to} started as the Big Boss.`;
	if (item.reason === 'kill')
		return `${item.detail || `${from} was killed by ${to}.`} ${to} became the Big Boss.`;
	if (item.reason === 'inactive')
		return `${from} missed the daily 30 minutes outside claims. ${to ? `${to} was assigned at random.` : 'The title became vacant.'}`;
	if (item.reason === 'term_limit')
		return `${from} reached the seven-day limit. ${to ? `${to} was assigned at random.` : 'The title became vacant.'}`;
	if (item.reason === 'random_assignment')
		return `${to} was assigned the vacant title at random.`;
	if (item.reason === 'admin_reassigned')
		return `An admin assigned the title to ${to}.${item.detail ? ` Reason: ${item.detail}` : ''}`;
	return `An admin cleared the title.${item.detail ? ` Reason: ${item.detail}` : ''}`;
}

export function BigBossTab() {
	const [state, setState] = useState<BossState | null>(null);
	const latest = useRef<BossState | null>(null);
	const [previous, setPrevious] = useState<BossState | null>(null);
	const [fetchedAt, setFetchedAt] = useState(0);
	const [now, setNow] = useState(0);
	const [error, setError] = useState('');
	const [history, setHistory] = useState<Transition[]>([]);
	const [nextCursor, setNextCursor] = useState<number | null>(null);
	const [historyError, setHistoryError] = useState('');
	const [loadingHistory, setLoadingHistory] = useState(false);
	const [rulesOpen, setRulesOpen] = useState(false);
	const historyLoading = useRef(false);
	const historyAbort = useRef<AbortController | null>(null);
	const historyVersion = useRef<number | null>(null);
	const historyEnd = useRef<HTMLDivElement>(null);
	const rulesDialog = useRef<HTMLDialogElement>(null);

	async function loadHistory(cursor: number | null, reset = false) {
		if (historyLoading.current && !reset) return;
		if (reset) historyAbort.current?.abort();
		const request = new AbortController();
		historyAbort.current = request;
		historyLoading.current = true;
		setLoadingHistory(true);
		try {
			const response = await fetch(
				`/api/big-boss/history${cursor === null ? '' : `?cursor=${cursor}`}`,
				{ cache: 'no-store', signal: request.signal },
			);
			if (!response.ok) throw new Error('Could not load the history.');
			const page = (await response.json()) as HistoryPage;
			setHistory((current) => (reset ? page.entries : [...current, ...page.entries]));
			setNextCursor(page.nextCursor);
			setHistoryError('');
		} catch (cause) {
			if (!request.signal.aborted)
				setHistoryError(
					cause instanceof Error ? cause.message : 'Could not load the history.',
				);
		} finally {
			if (historyAbort.current === request) {
				historyAbort.current = null;
				historyLoading.current = false;
				setLoadingHistory(false);
			}
		}
	}

	useEffect(() => {
		let cancelled = false;
		let loading = false;
		async function refresh() {
			if (loading) return;
			loading = true;
			try {
				const response = await fetch('/api/big-boss', { cache: 'no-store' });
				if (!response.ok) throw new Error('Could not load the Big Boss.');
				const next = (await response.json()) as BossState;
				if (cancelled) return;
				setPrevious(latest.current);
				latest.current = next;
				setState(next);
				if (historyVersion.current !== next.latestTransitionId) {
					historyVersion.current = next.latestTransitionId;
					void loadHistory(null, true);
				}
				setFetchedAt(Date.now());
				setError('');
			} catch (cause) {
				if (!cancelled)
					setError(
						cause instanceof Error ? cause.message : 'Could not load the Big Boss.',
					);
			} finally {
				loading = false;
			}
		}
		void refresh();
		const poll = window.setInterval(() => void refresh(), 5_000);
		const clock = window.setInterval(() => {
			setNow(Date.now());
		}, 100);
		return () => {
			cancelled = true;
			historyAbort.current?.abort();
			historyVersion.current = null;
			window.clearInterval(poll);
			window.clearInterval(clock);
		};
	}, []);

	useEffect(() => {
		const end = historyEnd.current;
		if (!end || nextCursor === null) return;
		const observer = new IntersectionObserver(
			(entries) => {
				if (entries[0]?.isIntersecting) void loadHistory(nextCursor);
			},
			{ rootMargin: '300px' },
		);
		observer.observe(end);
		return () => {
			observer.disconnect();
		};
	}, [nextCursor]);

	useEffect(() => {
		const dialog = rulesDialog.current;
		if (!dialog) return;
		if (rulesOpen && !dialog.open) dialog.showModal();
		if (!rulesOpen && dialog.open) dialog.close();
	}, [rulesOpen]);

	const position = state ? positionForDisplay(state, previous, fetchedAt, now) : null;
	return (
		<section className="bigBossTab">
			<div className="bigBossHeading">
				<h1>👑 The Big Boss</h1>
				<button
					type="button"
					className="bigBossRulesButton"
					aria-label="About the Big Boss"
					onClick={() => {
						setRulesOpen(true);
					}}
				>
					?
				</button>
			</div>
			<dialog
				ref={rulesDialog}
				className="bigBossRulesDialog"
				onClose={() => {
					setRulesOpen(false);
				}}
				onClick={(event) => {
					if (event.target === event.currentTarget) setRulesOpen(false);
				}}
			>
				<button
					type="button"
					className="bigBossRulesClose"
					aria-label="Close"
					onClick={() => {
						setRulesOpen(false);
					}}
				>
					×
				</button>
				<h2>The crown is yours to take.</h2>
				<p className="bigBossRulesLead">
					One player rules as the Big Boss. Kill them and claim the title. Your name and
					location go on display for the whole server. Hold the crown for as long as you
					can.
				</p>
				<h3>How do I keep it?</h3>
				<p>
					Play for 30 minutes outside claims each day. The day ends at 4 am London time. A
					reign lasts at most seven days.
				</p>
				<h3>Why might a kill not pass the title?</h3>
				<p>
					If you opted out in your profile or held the crown within the last hour, the
					kill is a normal death.
				</p>
				<h3>How do I find the Big Boss?</h3>
				<p>
					Watch this page, or drink a Potion of Resonance with another in your offhand to
					travel near them. The potion cannot find them while they are offline or inside a
					protected claim. Claims still stop projectiles, but they cannot stop a player
					from striking the Big Boss.
				</p>
				<h3>What happens to my gear?</h3>
				<p>
					When a kill passes the title, the former boss keeps their inventory and
					experience. Their head and soul still drop.
				</p>
			</dialog>
			{error && (
				<p role="alert" className="bigBossError">
					{error}
				</p>
			)}
			{state ? (
				<>
					{state.holder ? (
						<Link
							className="bigBossCard"
							href={`/play/players/${encodeURIComponent(state.holder.minecraftUsername)}`}
						>
							<Head person={state.holder} large link={false} />
							<div className="bigBossIdentity">
								<span className="bigBossTitle">The Big Boss</span>
								<strong>{state.holder.minecraftUsername}</strong>
								<div className="bigBossStatus">
									<span
										className={`bigBossDot ${state.online ? 'online' : ''}`}
									/>
									<b>{state.online ? 'LIVE' : 'OFFLINE'}</b>
									{position && (
										<span>
											{state.online ? '' : 'Roughly '}
											{position.x}, {position.y}, {position.z} ·{' '}
											{position.dimension.replace('minecraft:', '')}
										</span>
									)}
									{state.online && position && (
										<span className="bigBossClaim">
											{position.inClaim
												? 'Inside a protected claim'
												: 'Outside a claim'}
										</span>
									)}
								</div>
							</div>
						</Link>
					) : (
						<div className="bigBossVacant">The throne is empty. For now.</div>
					)}
					<h2>History</h2>
					<div className="bigBossHistory">
						{history.map((item) => {
							const reign = item.reign;
							return (
								<div key={item.id}>
									{reign && (
										<div className="bigBossReign">
											<Head person={reign.holder} />
											<div>
												<strong>
													{reign.holder.minecraftUsername} was the Big
													Boss for{' '}
													{duration(
														reign.endedAtUnixMs - reign.startedAtUnixMs,
													)}
													.
												</strong>
												<small>
													({TIME.format(new Date(reign.startedAtUnixMs))}{' '}
													to {TIME.format(new Date(reign.endedAtUnixMs))})
												</small>
											</div>
										</div>
									)}
									<div className="bigBossTransition">
										<div>
											{transitionText(item)}
											<small>{TIME.format(new Date(item.atUnixMs))}</small>
										</div>
									</div>
								</div>
							);
						})}
						<div ref={historyEnd} aria-hidden="true" />
					</div>
					{loadingHistory && <p>Loading history…</p>}
					{historyError && (
						<p role="alert" className="bigBossError">
							{historyError}{' '}
							<button
								type="button"
								onClick={() => void loadHistory(nextCursor, !history.length)}
							>
								Retry
							</button>
						</p>
					)}
				</>
			) : (
				!error && <p>Loading the Big Boss…</p>
			)}
		</section>
	);
}
