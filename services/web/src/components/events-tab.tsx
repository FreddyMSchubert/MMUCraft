'use client';

import Link from 'next/link';
import { formatManchesterDateTime } from '@/lib/date-time';
import { BuildEventsTab, type BuildEventSummary } from './build-events-tab';
import { SurprisingSaturdayTab, type EventSummary } from './surprising-saturday-tab';

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
	const saturdayLive = saturdayEvents.some((event) => event.status === 'live');
	const buildActive = buildEvents.some((event) =>
		['building', 'preparing', 'voting', 'visiting'].includes(event.status),
	);
	const showBuild = explicitBuild || (!path[0] && !saturdayLive && buildActive);
	const history = [
		...saturdayEvents
			.filter((event) => event.status === 'ended')
			.map((event) => ({
				id: 's-' + String(event.id),
				title: event.title ?? 'Surprising Saturday',
				type: 'Surprising Saturday',
				description: event.shortDescription,
				date: event.startsAtUnixMs,
				href: '/play/event/' + String(event.id),
			})),
		...buildEvents
			.filter((event) => event.status === 'visiting' || event.status === 'ended')
			.map((event) => ({
				id: 'b-' + String(event.id),
				title: event.title,
				type: 'Build event',
				description: event.shortDescription,
				date: event.startsAtUnixMs,
				href: '/play/event/build/' + String(event.id),
			})),
	].sort((a, b) => b.date - a.date);
	return (
		<div className="allEventsPage">
			<nav className="eventTypeNavigation" aria-label="Event types">
				<Link className={!showBuild ? 'active' : ''} href="/play/event/saturday">
					Surprising Saturday
				</Link>
				<Link className={showBuild ? 'active' : ''} href="/play/event/build">
					Build events
				</Link>
			</nav>
			{showBuild ? (
				<BuildEventsTab events={buildEvents} selectedId={buildId} />
			) : (
				<SurprisingSaturdayTab
					events={saturdayEvents}
					selectedId={saturdayId}
					showHistory={false}
				/>
			)}
			{!detailPage && history.length > 0 && (
				<section className="adminSection eventListSection eventCombinedHistory">
					<h4>Past events</h4>
					<div className="eventHistory">
						{history.map((event) => (
							<Link href={event.href} key={event.id}>
								<span className="eventHistoryType">{event.type}</span>
								<strong>{event.title}</strong>
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
