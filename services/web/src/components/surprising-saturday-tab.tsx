'use client';

import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';
import sanitizeHtml from 'sanitize-html';
import { formatManchesterDateTime } from '@/lib/date-time';
import { knowledgeMarkdown } from './knowledge/knowledge-markdown-renderer';

export interface EventSummary {
	id: number;
	status: 'upcoming' | 'live' | 'ended';
	title: string | null;
	titleWordLengths: number[];
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
	eventReady: boolean;
}

const GLYPHS =
	'ABCDEFGHJKLMNOPQRSTUVWXYZabcdeghjmnopqrsuvwxyz0123456789?#$%&+-=/\\^_' +
	'¢£¥§¬¯±µ¿ÀÁÂÃÄÅÇÈÉÊËÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåçèéêëðñòóôõö÷øùúûüý';

function ObfuscatedEventName({ wordLengths }: { wordLengths: number[] }) {
	const text = useRef<HTMLSpanElement>(null);
	const mask = wordLengths.map((length) => 'X'.repeat(length)).join(' ');
	useEffect(() => {
		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
		let frame: number;
		function shuffle() {
			if (text.current)
				text.current.textContent = Array.from(mask, (character) =>
					character === ' ' ? ' ' : GLYPHS[Math.floor(Math.random() * GLYPHS.length)],
				).join('');
			frame = window.requestAnimationFrame(shuffle);
		}
		frame = window.requestAnimationFrame(shuffle);
		return () => {
			window.cancelAnimationFrame(frame);
		};
	}, [mask]);
	return (
		<span ref={text} role="img" aria-label="Event name hidden" className="eventObfuscated">
			{mask}
		</span>
	);
}

function EventDescription({ description }: { description: string }) {
	const html = useMemo(
		() =>
			sanitizeHtml(knowledgeMarkdown.parse(description, { async: false }), {
				allowedTags: [...sanitizeHtml.defaults.allowedTags, 'img'],
				allowedAttributes: { ...sanitizeHtml.defaults.allowedAttributes, '*': ['class'] },
			}),
		[description],
	);
	if (!description) return null;
	return (
		<div
			className="knowledgePage eventDescription"
			dangerouslySetInnerHTML={{ __html: html }}
		/>
	);
}

export function SurprisingSaturdayTab({
	events,
	selectedId,
}: {
	events: EventSummary[];
	selectedId?: string;
}) {
	const isDetailPage = selectedId !== undefined;
	const upcoming = events.filter((event) => event.status === 'upcoming');
	const history = events.filter((event) => event.status === 'ended').reverse();
	const selected = isDetailPage
		? events.find((event) => String(event.id) === selectedId)
		: (events.find((event) => event.status === 'live') ?? upcoming[0]);
	const selectedEventId = selected?.id;
	const [detail, setDetail] = useState<EventDetail | null>(null);
	const [me, setMe] = useState<MyServer | null>(null);
	const [error, setError] = useState('');
	const [moving, setMoving] = useState(false);
	const [moveFeedback, setMoveFeedback] = useState('');

	useEffect(() => {
		let cancelled = false;
		let timer: number | undefined;
		async function refresh() {
			try {
				const [detailResponse, meResponse] = await Promise.all([
					selectedEventId === undefined
						? Promise.resolve(null)
						: fetch(`/api/surprising-saturday/${selectedEventId}`, {
								cache: 'no-store',
							}),
					fetch('/api/surprising-saturday/me', { cache: 'no-store' }),
				]);
				if ((detailResponse && !detailResponse.ok) || !meResponse.ok)
					throw new Error('Could not load event data');
				const [eventData, myServer] = await Promise.all([
					detailResponse ? detailResponse.json() : Promise.resolve(null),
					meResponse.json(),
				]);
				if (!cancelled) {
					setDetail(eventData as EventDetail | null);
					setMe(myServer as MyServer);
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

	const currentDetail = detail?.id === selectedEventId ? detail : null;
	const own = currentDetail?.players?.find((player) => player.uuid === me?.uuid);
	const completedItems = new Set(own?.completed.map((entry) => entry.itemId));
	const otherUpcoming = upcoming.filter((event) => event.id !== selectedEventId);
	const onEventServer = me?.serverName === 'surprising-saturday';

	async function switchServer() {
		setMoving(true);
		setMoveFeedback('');
		try {
			const response = await fetch('/api/surprising-saturday/me/server', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					serverName: onEventServer ? 'main' : 'surprising-saturday',
				}),
			});
			if (!response.ok) {
				const body = (await response.json()) as { message?: string | string[] };
				throw new Error(
					Array.isArray(body.message)
						? body.message.join(', ')
						: (body.message ?? 'Switch failed'),
				);
			}
			setMoveFeedback('Switch requested. You will move in a few seconds.');
		} catch (caught) {
			setMoveFeedback(caught instanceof Error ? caught.message : 'Switch failed');
		} finally {
			setMoving(false);
		}
	}

	return (
		<div className="eventPage">
			<header className="eventTop">
				<div>
					<h3>Surprising Saturday</h3>
					<p className="tabSubtitle">
						{selected?.status === 'upcoming'
							? 'How you win is revealed when the event starts.'
							: 'Play the event and follow the scores.'}
					</p>
				</div>
				{isDetailPage && (
					<Link className="eventBack" href="/play/event">
						← All events
					</Link>
				)}
			</header>

			{selected && (
				<section className="adminSection eventFeature">
					<div className="eventFeatureMeta">
						<span className={`eventStatus eventStatus-${selected.status}`}>
							{selected.status === 'live'
								? 'Live now'
								: selected.status === 'upcoming'
									? 'Coming up'
									: 'Finished'}
						</span>
						<time dateTime={new Date(selected.startsAtUnixMs).toISOString()}>
							{formatManchesterDateTime(selected.startsAtUnixMs)} –{' '}
							{formatManchesterDateTime(selected.endsAtUnixMs)}
						</time>
					</div>
					<h4 className="eventTitle">
						{selected.title ?? (
							<ObfuscatedEventName wordLengths={selected.titleWordLengths} />
						)}
					</h4>
					<EventDescription
						description={currentDetail?.description ?? selected.description ?? ''}
					/>
					{selected.status === 'live' && (
						<div className="eventJoin">
							<button
								type="button"
								disabled={!me?.serverName || !me.eventReady || moving}
								onClick={() => void switchServer()}
							>
								{moving
									? 'Switching…'
									: onEventServer
										? 'Return to main server'
										: 'Join event server'}
							</button>
							{!me?.serverName && <span>Join Minecraft to switch servers.</span>}
							{me?.serverName && !me.eventReady && (
								<span>The event server is starting.</span>
							)}
							{moveFeedback && <span role="status">{moveFeedback}</span>}
						</div>
					)}
				</section>
			)}
			{isDetailPage && !selected && (
				<section className="adminSection">This event could not be found.</section>
			)}

			{selected?.status !== 'upcoming' &&
				currentDetail?.criteriaType === 'list_completion' && (
					<section className="adminSection eventScores">
						<div className="eventSectionHeading">
							<h4>Leaderboard</h4>
							<p>
								Most unique kills wins. Ties go to the player who reached that score
								first.
							</p>
						</div>
						<details className="eventChecklist">
							<summary>
								Your completion list ({own?.completed.length ?? 0}/
								{currentDetail.items?.length ?? 0})
							</summary>
							<ul>
								{currentDetail.items?.map((item) => (
									<li
										key={item}
										aria-label={`${item}: ${completedItems.has(item) ? 'complete' : 'incomplete'}`}
									>
										<span
											className={
												completedItems.has(item)
													? 'eventComplete'
													: 'eventIncomplete'
											}
											aria-hidden="true"
										>
											{completedItems.has(item) ? '✓' : '○'}
										</span>{' '}
										{item}
									</li>
								))}
							</ul>
						</details>
						{currentDetail.players && currentDetail.players.length > 0 && (
							<div className="eventPodium">
								{currentDetail.players.slice(0, 3).map((player, index) => (
									<div key={player.uuid}>
										<small>#{index + 1}</small>
										<strong style={{ color: player.color }}>
											{player.name}
										</strong>
										<span>{player.completed.length} unique kills</span>
									</div>
								))}
							</div>
						)}
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
									{currentDetail.players?.map((player, index) => (
										<tr key={player.uuid}>
											<td>{index + 1}</td>
											<td style={{ color: player.color }}>{player.name}</td>
											<td>
												<progress
													value={player.completed.length}
													max={currentDetail.items?.length ?? 1}
												/>{' '}
												{player.completed.length}/
												{currentDetail.items?.length}
											</td>
										</tr>
									))}
									{currentDetail.players?.length === 0 && (
										<tr>
											<td colSpan={3}>
												No players have joined this event yet.
											</td>
										</tr>
									)}
								</tbody>
							</table>
						</div>
					</section>
				)}
			{error && <p className="authError">{error}</p>}

			{otherUpcoming.length > 0 && !isDetailPage && (
				<section className="adminSection eventListSection">
					<h4>Also coming up</h4>
					<div className="eventHistory">
						{otherUpcoming.map((event) => (
							<Link key={event.id} href={`/play/event/${event.id}`}>
								<strong>
									<ObfuscatedEventName wordLengths={event.titleWordLengths} />
								</strong>
								<time>{formatManchesterDateTime(event.startsAtUnixMs)}</time>
							</Link>
						))}
					</div>
				</section>
			)}
			{history.length > 0 && (
				<section className="adminSection eventListSection">
					<h4>Past events</h4>
					<div className="eventHistory">
						{history.map((event) => (
							<Link
								key={event.id}
								href={`/play/event/${event.id}`}
								className={
									event.id === selected?.id && isDetailPage ? 'active' : ''
								}
							>
								<strong>{event.title}</strong>
								<time>{formatManchesterDateTime(event.startsAtUnixMs)}</time>
							</Link>
						))}
					</div>
				</section>
			)}
		</div>
	);
}
