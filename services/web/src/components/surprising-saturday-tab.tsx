'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useMemo, useRef, useState } from 'react';
import sanitizeHtml from 'sanitize-html';
import { formatManchesterDateTime } from '@/lib/date-time';
import { formatDabloons } from '@/lib/dabloons';
import { PlayerName, type PlayerEmoji } from './player-name';
import { knowledgeMarkdown } from './knowledge/knowledge-markdown-renderer';

export interface CompletionTarget {
	id: string;
	name: string;
	url: string;
	points: number;
	description?: string;
	amount?: number;
}

export interface EventSummary {
	id: number;
	status: 'upcoming' | 'live' | 'ended';
	title: string | null;
	titleWordLengths: number[];
	description: string | null;
	shortDescription: string | null;
	startsAtUnixMs: number;
	endsAtUnixMs: number;
}

interface EventPlayer {
	uuid: string;
	name: string;
	avatarUrl: string | null;
	color: string;
	pronouns: string;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
	points: number;
	completed: { itemId: string; atUnixMs: number }[];
}

interface EventDetail extends EventSummary {
	criteriaType?: string;
	items?: CompletionTarget[];
	players?: EventPlayer[];
}

const GLYPHS =
	'ABCDEFGHJKLMNOPQRSTUVWXYZabcdeghjmnopqrsuvwxyz0123456789?#$%&+-=/\\^_' +
	'¢£¥§¬¯±µ¿ÀÁÂÃÄÅÇÈÉÊËÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåçèéêëðñòóôõö÷øùúûüý';

export function ObfuscatedEventName({
	wordLengths,
	label = 'Event name hidden',
}: {
	wordLengths: number[];
	label?: string;
}) {
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
		<span ref={text} role="img" aria-label={label} className="eventObfuscated">
			{mask}
		</span>
	);
}

export function EventDescription({ description }: { description: string }) {
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

function EventPlayerLink({
	player,
	withHead = false,
}: {
	player: EventPlayer;
	withHead?: boolean;
}) {
	const content = (
		<>
			{withHead &&
				(player.avatarUrl ? (
					<Image unoptimized src={player.avatarUrl} alt="" width={28} height={28} />
				) : (
					<span className="eventEarningHeadFallback">{player.name.charAt(0)}</span>
				))}
			<PlayerName
				name={player.name}
				color={player.color}
				isCommittee={player.isCommittee}
				customEmojis={player.customEmojis}
			/>
			{player.pronouns && <span className="eventPlayerPronouns"> ({player.pronouns})</span>}
		</>
	);
	if (player.name === 'Unknown player')
		return <span className={withHead ? 'eventEarningPlayer' : undefined}>{content}</span>;
	return (
		<Link
			className={withHead ? 'eventEarningPlayer' : 'eventPlayerLink'}
			href={`/play/players/${encodeURIComponent(player.name)}`}
		>
			{content}
		</Link>
	);
}

export function SurprisingSaturdayTab({
	events,
	selectedId,
	showHistory = true,
	showBackLink,
}: {
	events: EventSummary[];
	selectedId?: string;
	showHistory?: boolean;
	showBackLink?: boolean;
}) {
	const isDetailPage = selectedId !== undefined;
	const history = events.filter((event) => event.status === 'ended').reverse();
	const selected = isDetailPage ? events.find((event) => String(event.id) === selectedId) : null;
	const selectedEventId = selected?.id;
	const [detail, setDetail] = useState<EventDetail | null>(null);
	const [error, setError] = useState('');
	const [committeeVisibleFor, setCommitteeVisibleFor] = useState<number | null>(null);
	const showCommittee = committeeVisibleFor !== null && committeeVisibleFor === selectedEventId;
	useEffect(() => {
		let cancelled = false;
		async function refresh() {
			try {
				const detailResponse =
					selectedEventId === undefined
						? null
						: await fetch(`/api/surprising-saturday/${selectedEventId}`, {
								cache: 'no-store',
							});
				if (detailResponse && !detailResponse.ok)
					throw new Error('Could not load event data');
				const eventData = detailResponse ? await detailResponse.json() : null;
				if (!cancelled) {
					setDetail(eventData as EventDetail | null);
					setError('');
				}
			} catch (caught) {
				if (!cancelled)
					setError(
						caught instanceof Error ? caught.message : 'Could not load event data',
					);
			}
		}
		void refresh();
		return () => {
			cancelled = true;
		};
	}, [selectedEventId]);

	const currentDetail = detail?.id === selectedEventId ? detail : null;
	const furthestGoalByPlayer = new Map<string, string>();
	for (const player of currentDetail?.players ?? []) {
		const completed = new Set(player.completed.map((entry) => entry.itemId));
		for (const item of currentDetail?.items ?? []) {
			if (completed.has(item.id)) furthestGoalByPlayer.set(player.uuid, item.id);
		}
	}
	const shownPlayers =
		currentDetail?.players?.filter((player) => showCommittee || !player.isCommittee) ?? [];
	const totalPoints = currentDetail?.items?.reduce((sum, item) => sum + item.points, 0) ?? 0;
	return (
		<div className="eventPage">
			{(showBackLink ?? isDetailPage) && (
				<Link className="eventBack" href="/play/event">
					← All events
				</Link>
			)}
			<header className="eventTop">
				<h3>Events</h3>
			</header>

			{selected && (
				<section className="adminSection eventFeature">
					<h4 className="eventTitle">
						{currentDetail?.title ?? selected.title ?? (
							<ObfuscatedEventName wordLengths={selected.titleWordLengths} />
						)}
					</h4>
					<EventDescription
						description={currentDetail?.description ?? selected.description ?? ''}
					/>
					<div className="eventFeatureMeta">
						<span className="eventStatus eventStatus-ended">
							Surprising Saturday · Finished
						</span>
						<time dateTime={new Date(selected.startsAtUnixMs).toISOString()}>
							{formatManchesterDateTime(selected.startsAtUnixMs)} –{' '}
							{formatManchesterDateTime(selected.endsAtUnixMs)}
						</time>
					</div>
				</section>
			)}
			{isDetailPage && !selected && (
				<section className="adminSection">This event could not be found.</section>
			)}

			{currentDetail?.criteriaType === 'joined_earning' && (
				<section className="adminSection eventScores">
					<div className="eventSectionHeading">
						<h4>Goals</h4>
						<p>Final goals and results from this event.</p>
					</div>
					<ol className="eventEarningGoals">
						{currentDetail.items?.map((item) => {
							const finishers =
								currentDetail.players?.filter(
									(player) => furthestGoalByPlayer.get(player.uuid) === item.id,
								) ?? [];
							return (
								<li key={item.id}>
									<div className="eventEarningGoalHeading">
										<div>
											<strong>{item.name}</strong>
											{item.description && <p>{item.description}</p>}
										</div>
										<span>{formatDabloons(item.amount ?? 0)}</span>
									</div>
									<div className="eventEarningPlayers">
										{finishers.map((player) => (
											<EventPlayerLink
												key={player.uuid}
												player={player}
												withHead
											/>
										))}
									</div>
								</li>
							);
						})}
					</ol>
				</section>
			)}
			{currentDetail?.criteriaType === 'list_completion' && (
				<section className="adminSection eventScores">
					<div className="eventSectionHeading">
						<h4>Leaderboard</h4>
						<p>Most points wins. Ties go to the player who reached that score first.</p>
					</div>
					<label className="eventCommitteeToggle">
						<input
							type="checkbox"
							checked={showCommittee}
							onChange={(event) => {
								setCommitteeVisibleFor(
									event.target.checked ? (selectedEventId ?? null) : null,
								);
							}}
						/>
						Show committee players
					</label>
					{showCommittee && (
						<p className="eventUnofficial" role="alert">
							These are not the official results. Committee members play for fun and
							cannot win.
						</p>
					)}
					{shownPlayers.length > 0 && (
						<div className="eventPodium">
							{shownPlayers.slice(0, 3).map((player, index) => (
								<div key={player.uuid}>
									<small>#{index + 1}</small>
									<strong>
										<EventPlayerLink player={player} />
									</strong>
									<span>
										{player.points} {player.points === 1 ? 'point' : 'points'}
										{player.isCommittee && ' · for fun'}
									</span>
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
									<th>Score</th>
								</tr>
							</thead>
							<tbody>
								{shownPlayers.map((player, index) => (
									<tr key={player.uuid}>
										<td>{index + 1}</td>
										<td>
											<EventPlayerLink player={player} />
											{player.isCommittee && (
												<span className="eventForFun"> · for fun</span>
											)}
										</td>
										<td>
											<progress
												aria-label={`${player.name} score`}
												value={player.points}
												max={totalPoints || 1}
											/>{' '}
											{player.points}/{totalPoints} pts ·{' '}
											{player.completed.length}/{currentDetail.items?.length}{' '}
											goals
										</td>
									</tr>
								))}
								{shownPlayers.length === 0 && (
									<tr>
										<td colSpan={3}>
											{currentDetail.players?.length
												? 'No eligible players have joined this event yet.'
												: 'No players have joined this event yet.'}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</section>
			)}
			{error && <p className="authError">{error}</p>}

			{history.length > 0 && !isDetailPage && showHistory && (
				<section className="adminSection eventListSection">
					<h4>Past events</h4>
					<div className="eventHistory">
						{history.map((event) => (
							<Link key={event.id} href={`/play/event/${event.id}`}>
								<strong>{event.title}</strong>
								{event.shortDescription && (
									<span className="eventHistoryDescription">
										{event.shortDescription}
									</span>
								)}
								<time>{formatManchesterDateTime(event.startsAtUnixMs)}</time>
							</Link>
						))}
					</div>
				</section>
			)}
		</div>
	);
}
