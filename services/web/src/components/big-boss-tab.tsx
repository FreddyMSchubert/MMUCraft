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
	nickname: string;
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
}

interface BossState {
	holder: Person | null;
	reignStartedAtUnixMs: number | null;
	online: boolean;
	position: Position | null;
	transitions: Transition[];
	reigns: {
		holder: Person | null;
		startedAtUnixMs: number;
		endedAtUnixMs: number | null;
		transitionId: number;
	}[];
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

function dateSeed(username: string, now: number) {
	const parts = new Intl.DateTimeFormat('en-GB', {
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		timeZone: 'Europe/London',
	}).formatToParts(new Date(now));
	const date = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	const day = `${date.year}-${date.month}-${date.day}`;
	let value = 2166136261;
	for (const character of `${day}:${username.toLowerCase()}`) {
		value = Math.imul(value ^ character.charCodeAt(0), 16777619);
	}
	return value >>> 0;
}

function positionForDisplay(
	state: BossState,
	previous: BossState | null,
	fetchedAt: number,
	now: number,
) {
	const position = state.position;
	if (!position) return null;
	if (!state.online) {
		let seed = dateSeed(state.holder?.minecraftUsername ?? 'boss', now);
		const next = () => {
			seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
			return (seed % 85) - 42;
		};
		return {
			...position,
			x: position.x + next(),
			y: position.y + next(),
			z: position.z + next(),
		};
	}
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
	) : (
		<span>👑</span>
	);
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

export function BigBossTab({ isCommittee }: { isCommittee: boolean }) {
	const [state, setState] = useState<BossState | null>(null);
	const latest = useRef<BossState | null>(null);
	const [previous, setPrevious] = useState<BossState | null>(null);
	const [fetchedAt, setFetchedAt] = useState(0);
	const [now, setNow] = useState(0);
	const [error, setError] = useState('');
	const [target, setTarget] = useState('');
	const [reason, setReason] = useState('');
	const [warningResult, setWarningResult] = useState<{
		username: string;
		optedOut: boolean;
		cooldown: boolean;
	} | null>(null);
	const warnings = warningResult?.username === target.trim() ? warningResult : null;
	const [busy, setBusy] = useState(false);

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
			window.clearInterval(poll);
			window.clearInterval(clock);
		};
	}, []);

	useEffect(() => {
		if (!isCommittee || !target.trim()) return;
		let cancelled = false;
		const timer = window.setTimeout(async () => {
			try {
				const response = await fetch(
					`/api/big-boss/admin/${encodeURIComponent(target.trim())}/warnings`,
				);
				if (!cancelled && response.ok) {
					const value = (await response.json()) as {
						optedOut: boolean;
						cooldown: boolean;
					};
					setWarningResult({ username: target.trim(), ...value });
				}
			} catch {
				if (!cancelled) setWarningResult(null);
			}
		}, 350);
		return () => {
			cancelled = true;
			window.clearTimeout(timer);
		};
	}, [target, isCommittee]);

	async function assign(minecraftUsername: string | null) {
		setBusy(true);
		try {
			const response = await fetch('/api/big-boss/admin/assign', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ minecraftUsername, reason }),
			});
			const data = (await response.json()) as BossState & { message?: string };
			if (!response.ok) throw new Error(data.message ?? 'Could not change the Big Boss.');
			setPrevious(latest.current);
			latest.current = data;
			setState(data);
			setFetchedAt(Date.now());
			setReason('');
			setError('');
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not change the Big Boss.');
		} finally {
			setBusy(false);
		}
	}

	const position = state ? positionForDisplay(state, previous, fetchedAt, now) : null;
	return (
		<section className="bigBossTab">
			<div className="bigBossHeading">
				<h1>👑 The Big Boss</h1>
				<details className="bigBossRules">
					<summary aria-label="Big Boss rules">?</summary>
					<p>
						Kill the Big Boss to take the title. You must allow Big Boss wins in your
						profile settings and must not have held the title in the last hour. A title
						pass lets the former boss keep their items and experience. Heads and souls
						still drop.
					</p>
					<p>
						To keep the title, spend 30 minutes online outside claims each day. The day
						ends at 4 am London time. If you gain the title less than 30 minutes before
						4 am, your first deadline is the next day. A reign ends after seven days.
					</p>
					<p>
						If a reign ends, the server chooses a random eligible online player. If
						nobody qualifies, the next eligible player who joins gets the title. Admins
						can assign or clear it. Admin assignments do not count as kills or wins.
					</p>
					<p>
						Drink a Potion of Resonance while holding another in your offhand to travel
						to the Big Boss. Both potions are used on success. The normal teleport
						safety rules apply. If the boss is offline or inside a claim, neither potion
						is used.
					</p>
					<p>
						Online positions show exact blocks and update every five seconds. Offline
						positions show a rough location, shifted by up to 42 blocks on each axis.
						The rough location changes each day.
					</p>
					<p>
						Each full hour online outside claims adds five Dabloons to your lifetime Big
						Boss total. One-time advancements pay more at 1, 3, 5, 10, 15, 20, 25, 30,
						35, 40 and 42 hours.
					</p>
				</details>
			</div>
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
								<strong>
									{state.holder.nickname || state.holder.minecraftUsername}
								</strong>
								{state.holder.nickname && (
									<span>{state.holder.minecraftUsername}</span>
								)}
							</div>
						</Link>
					) : (
						<div className="bigBossVacant">
							The title is vacant. The next eligible player to join becomes the Big
							Boss.
						</div>
					)}
					<div className="bigBossStatus">
						<span className={`bigBossDot ${state.online ? 'online' : ''}`} />
						<strong>{state.online ? 'LIVE' : 'OFFLINE'}</strong>
						{position && (
							<span>
								{state.online ? '' : 'Roughly '}
								{position.x}, {position.y}, {position.z} ·{' '}
								{position.dimension.replace('minecraft:', '')}
							</span>
						)}
						{state.online && position && (
							<span className="bigBossClaim">
								{position.inClaim ? 'Inside a protected claim' : 'Outside a claim'}
							</span>
						)}
					</div>
					<h2>History</h2>
					<div className="bigBossHistory">
						{state.transitions.map((item) => {
							const reign = state.reigns.find(
								(entry) => entry.transitionId === item.id,
							);
							return (
								<div key={item.id}>
									{reign && (
										<div className="bigBossReign">
											<Head person={reign.holder} />
											<div>
												<strong>
													{reign.holder?.minecraftUsername} was the Big
													Boss for{' '}
													{duration(
														(reign.endedAtUnixMs ?? now) -
															reign.startedAtUnixMs,
													)}
													.
												</strong>
												<small>
													({TIME.format(new Date(reign.startedAtUnixMs))}{' '}
													to{' '}
													{reign.endedAtUnixMs
														? TIME.format(new Date(reign.endedAtUnixMs))
														: 'now'}
													)
												</small>
											</div>
										</div>
									)}
									<div className="bigBossTransition">
										<span>👑</span>
										<div>
											{transitionText(item)}
											<small>{TIME.format(new Date(item.atUnixMs))}</small>
										</div>
									</div>
								</div>
							);
						})}
					</div>
				</>
			) : (
				!error && <p>Loading the Big Boss…</p>
			)}
			{isCommittee && (
				<section className="bigBossAdmin">
					<h2>Admin controls</h2>
					<label>
						Player username{' '}
						<input
							value={target}
							onChange={(event) => {
								setTarget(event.target.value);
							}}
							maxLength={16}
						/>
					</label>
					{warnings?.optedOut && (
						<p role="status">
							This player has opted out. An admin assignment overrides that choice.
						</p>
					)}
					{warnings?.cooldown && (
						<p role="status">
							This player held the title within the last hour. An admin assignment
							overrides the cooldown.
						</p>
					)}
					<label>
						Reason (optional){' '}
						<input
							value={reason}
							onChange={(event) => {
								setReason(event.target.value);
							}}
							maxLength={200}
						/>
					</label>
					<div>
						<button
							type="button"
							disabled={busy || !target.trim()}
							onClick={() => void assign(target.trim())}
						>
							Assign title
						</button>
						<button
							type="button"
							disabled={busy || !state?.holder}
							onClick={() => void assign(null)}
						>
							Clear title
						</button>
					</div>
				</section>
			)}
		</section>
	);
}
