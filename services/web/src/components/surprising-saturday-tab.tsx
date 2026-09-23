'use client';

import { marked } from 'marked';
import { useEffect, useState } from 'react';
import { formatManchesterDateTime } from '@/lib/date-time';
import { apiMessage } from './admin/admin-api';

export interface EventSummary {
	id: number;
	status: 'upcoming' | 'live' | 'ended';
	title: string | null;
	description: string | null;
	startsAtUnixMs: number;
	endsAtUnixMs: number;
}

interface EventPlayer {
	uuid: string;
	name: string;
	color: string;
	completed: { itemId: string; atUnixMs: number }[];
}

interface EventDetail extends EventSummary {
	criteriaType?: string;
	items?: string[];
	players?: EventPlayer[];
}

interface MyServer {
	uuid: string | null;
	serverName: string | null;
	eventOnline: boolean;
}

export function SurprisingSaturdayTab({
	events,
	selectedId,
}: {
	events: EventSummary[];
	selectedId?: string;
}) {
	const live = events.find((event) => event.status === 'live');
	const selected =
		events.find((event) => String(event.id) === selectedId) ??
		live ??
		[...events].reverse().find((event) => event.status === 'ended') ??
		events.at(0);
	const selectedEventId = selected?.id;
	const [detail, setDetail] = useState<EventDetail | null>(null);
	const [me, setMe] = useState<MyServer | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');

	useEffect(() => {
		if (selectedEventId === undefined) return;
		let cancelled = false;
		let timer: number | undefined;
		async function refresh() {
			try {
				const [detailResponse, meResponse] = await Promise.all([
					fetch(`/api/surprising-saturday/${selectedEventId}`, { cache: 'no-store' }),
					fetch('/api/surprising-saturday/me', { cache: 'no-store' }),
				]);
				if (!detailResponse.ok || !meResponse.ok)
					throw new Error('Could not load event data');
				if (!cancelled) {
					setDetail((await detailResponse.json()) as EventDetail);
					setMe((await meResponse.json()) as MyServer);
					setError('');
				}
			} catch (caught) {
				if (!cancelled)
					setError(
						caught instanceof Error ? caught.message : 'Could not load event data',
					);
			} finally {
				if (!cancelled) timer = window.setTimeout(refresh, 5_000);
			}
		}
		void refresh();
		return () => {
			cancelled = true;
			window.clearTimeout(timer);
		};
	}, [selectedEventId]);

	async function switchServer() {
		if (!me) return;
		const target = me.serverName === 'surprising-saturday' ? 'main' : 'surprising-saturday';
		setBusy(true);
		try {
			const response = await fetch('/api/surprising-saturday/move', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ target }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not switch servers'));
			setError(`Moving to ${target === 'main' ? 'Main' : 'Surprising Saturday'}...`);
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not switch servers');
		} finally {
			setBusy(false);
		}
	}

	const own = detail?.players?.find((player) => player.uuid === me?.uuid);
	return (
		<div className="eventPage">
			<header className="adminSectionHeader">
				<h2>Surprising Saturday</h2>
				<p>The surprise is revealed when the event starts.</p>
			</header>
			{selected && (
				<section className="adminSection eventFeature">
					{selected.status === 'upcoming' ? (
						<>
							<p className="eventLiveLabel">
								Upcoming · {formatManchesterDateTime(selected.startsAtUnixMs)}
							</p>
							<h3 aria-label="Surprise hidden" className="eventObfuscated">
								▒▓▒▓▒▓▒▓▒▓▒
							</h3>
							<p>Find out the surprise on Saturday.</p>
						</>
					) : (
						<>
							<p className="eventLiveLabel">
								{detail?.status === 'live'
									? '🔴 LIVE NOW'
									: formatManchesterDateTime(selected.startsAtUnixMs, {
											dateStyle: 'medium',
										})}
							</p>
							<h3>{detail?.title ?? selected.title}</h3>
							{detail?.status === 'live' && (
								<div className="eventSwitch">
									<span>
										Main server
										<br />
										<small>Your normal experience</small>
									</span>
									<button
										type="button"
										disabled={
											busy ||
											!me?.serverName ||
											(!me.eventOnline &&
												me.serverName !== 'surprising-saturday')
										}
										onClick={() => void switchServer()}
									>
										{me?.serverName === 'surprising-saturday'
											? '← Switch to Main'
											: 'Switch to event →'}
									</button>
									<span>
										Event server
										<br />
										<small>Surprising Saturday</small>
									</span>
								</div>
							)}
							{detail?.description && (
								<iframe
									title="Event description"
									sandbox="allow-popups"
									className="eventDescription"
									srcDoc={`<meta name="viewport" content="width=device-width, initial-scale=1"><style>body{font:16px system-ui;color:#eee;background:#17171b;padding:1rem;line-height:1.5}a{color:#9dcfff}img{max-width:100%}</style>${marked.parse(detail.description, { async: false })}`}
								/>
							)}
							{detail?.criteriaType === 'list_completion' && (
								<>
									<details className="eventChecklist">
										<summary>
											Your completion list ({own?.completed.length ?? 0}/
											{detail.items?.length ?? 0})
										</summary>
										<ul>
											{detail.items?.map((item) => (
												<li key={item}>
													{own?.completed.some(
														(entry) => entry.itemId === item,
													)
														? '✓'
														: '○'}{' '}
													{item}
												</li>
											))}
										</ul>
									</details>
									<h4>Top players</h4>
									<div className="eventPodium">
										{detail.players?.slice(0, 3).map((player, index) => (
											<div key={player.uuid}>
												<strong>
													#{index + 1}{' '}
													<span style={{ color: player.color }}>
														{player.name}
													</span>
												</strong>
												<p>
													{player.completed.length}/{detail.items?.length}
												</p>
											</div>
										))}
									</div>
									<h4>Leaderboard</h4>
									<div className="adminTableWrap">
										<table className="adminTable">
											<thead>
												<tr>
													<th>Place</th>
													<th>Player</th>
													<th>Progress</th>
												</tr>
											</thead>
											<tbody>
												{detail.players?.map((player, index) => (
													<tr key={player.uuid}>
														<td>{index + 1}</td>
														<td style={{ color: player.color }}>
															{player.name}
														</td>
														<td>
															<progress
																value={player.completed.length}
																max={detail.items?.length ?? 1}
															/>{' '}
															{player.completed.length}/
															{detail.items?.length}
														</td>
													</tr>
												))}
												{detail.players?.length === 0 && (
													<tr>
														<td colSpan={3}>
															No players have joined this event yet.
														</td>
													</tr>
												)}
											</tbody>
										</table>
									</div>
								</>
							)}
						</>
					)}
				</section>
			)}
			{error && <p className="authError">{error}</p>}
			<section className="adminSection">
				<h3>Events</h3>
				<div className="eventHistory">
					{events.map((event) => (
						<a
							key={event.id}
							href={`/play/event/${event.id}`}
							className={event.id === selected?.id ? 'active' : ''}
						>
							<strong>{event.title ?? '▒▓▒▓▒▓▒▓▒▓▒'}</strong>
							<span>
								{event.status === 'live'
									? '🔴 Live now'
									: formatManchesterDateTime(event.startsAtUnixMs)}
							</span>
							{event.description && <small>{event.description.slice(0, 120)}</small>}
						</a>
					))}
					{events.length === 0 && <p>No events yet.</p>}
				</div>
			</section>
		</div>
	);
}
