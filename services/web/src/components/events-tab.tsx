'use client';

import Link from 'next/link';
import { formatManchesterDateTime } from '@/lib/date-time';
import { BuildEventsTab, type BuildEventSummary } from './build-events-tab';
import { SurprisingSaturdayTab, type EventSummary } from './surprising-saturday-tab';

interface EventEntry {
	id: string;
	type: 'Build event' | 'Surprising Saturday';
	title: string;
	description: string | null;
	date: number;
	end: number;
	status: 'active' | 'upcoming' | 'past';
	href: string;
}

export function EventsTab({
	saturdayEvents,
	buildEvents,
	path,
}: {
	saturdayEvents: EventSummary[];
	buildEvents: BuildEventSummary[];
	path: string[];
}) {
	const explicitBuild = path[0] === 'build';
	const saturdayId =
		path[0] && path[0] !== 'build' && path[0] !== 'saturday' ? path[0] : undefined;
	const buildId = explicitBuild ? path[1] : undefined;
	const detailPage = Boolean(saturdayId ?? buildId);
	const entries: EventEntry[] = [
		...saturdayEvents.map((event): EventEntry => ({
			id: 's-' + String(event.id),
			title: event.title ?? 'Surprising Saturday',
			type: 'Surprising Saturday',
			description: event.shortDescription,
			date: event.startsAtUnixMs,
			end: event.endsAtUnixMs,
			status:
				event.status === 'live'
					? 'active'
					: event.status === 'upcoming'
						? 'upcoming'
						: 'past',
			href: '/play/event/' + String(event.id),
		})),
		...buildEvents.map((event): EventEntry => ({
			id: 'b-' + String(event.id),
			title: event.title,
			type: 'Build event',
			description: event.shortDescription,
			date: event.startsAtUnixMs,
			end: event.status === 'building' ? event.buildEndsAtUnixMs : event.votingEndsAtUnixMs,
			status: ['building', 'preparing', 'voting'].includes(event.status)
				? 'active'
				: event.status === 'upcoming'
					? 'upcoming'
					: 'past',
			href: '/play/event/build/' + String(event.id),
		})),
	];
	const order = { active: 0, upcoming: 1, past: 2 };
	entries.sort(
		(a, b) =>
			order[a.status] - order[b.status] ||
			(a.status === 'active'
				? a.end - b.end
				: a.status === 'upcoming'
					? a.date - b.date
					: b.date - a.date),
	);
	const featured = detailPage
		? null
		: explicitBuild
			? entries.find((entry) => entry.type === 'Build event')
			: path[0] === 'saturday'
				? entries.find((entry) => entry.type === 'Surprising Saturday')
				: entries[0];
	const selected = detailPage
		? explicitBuild
			? { type: 'Build event' as const, id: buildId }
			: { type: 'Surprising Saturday' as const, id: saturdayId }
		: featured
			? { type: featured.type, id: featured.id.slice(2) }
			: null;
	const others = detailPage ? [] : entries.filter((entry) => entry.id !== featured?.id);
	return (
		<div className="allEventsPage">
			{selected?.type === 'Build event' ? (
				<BuildEventsTab
					events={buildEvents}
					selectedId={selected.id}
					detailPage={detailPage}
				/>
			) : selected?.type === 'Surprising Saturday' ? (
				<SurprisingSaturdayTab
					events={saturdayEvents}
					selectedId={selected.id}
					showHistory={false}
					showBackLink={detailPage}
				/>
			) : (
				<div className="eventPage">
					<header className="eventTop">
						<h3>Events</h3>
					</header>
					<p>No events have been scheduled yet.</p>
				</div>
			)}
			{others.length > 0 && (
				<section className="adminSection eventListSection eventCombinedHistory">
					<h4>All events</h4>
					<div className="eventHistory">
						{others.map((event) => (
							<Link href={event.href} key={event.id}>
								<strong>{event.title}</strong>
								<span className="eventHistoryType">{event.type}</span>
								{event.description && (
									<span className="eventHistoryDescription">
										{event.description}
									</span>
								)}
								<time>{formatManchesterDateTime(event.date)}</time>
							</Link>
						))}
					</div>
				</section>
			)}
		</div>
	);
}
