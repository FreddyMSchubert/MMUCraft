'use client';

import { type SyntheticEvent, useCallback, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import type { AdminSection } from './admin-data.types';
import {
	apiBody,
	apiMessage,
	errorMessage,
	fetchAdmin,
	formatLondonDateTime,
	formatLondonInput,
} from './admin-api';

interface Announcement {
	id: number;
	text: string;
	linkUrl: string | null;
	startsAtUnixMs: number;
	endsAtUnixMs: number;
}

export function AnnouncementAdminSection({ activeSection }: { activeSection: AdminSection }) {
	const { showAlert } = useSiteAlert();
	const [announcements, setAnnouncements] = useState<Announcement[]>([]);
	const [formOpen, setFormOpen] = useState(false);
	const [text, setText] = useState('');
	const [startsAt, setStartsAt] = useState('');
	const [endsAt, setEndsAt] = useState('');
	const [linkUrl, setLinkUrl] = useState('');
	const [busy, setBusy] = useState(false);
	const [movingId, setMovingId] = useState<number | null>(null);

	const load = useCallback(async () => {
		const body = await fetchAdmin<{ announcements: Announcement[] }>(
			'/api/admin/announcements',
			'Failed to load announcements',
		);
		setAnnouncements(body.announcements);
	}, []);

	useEffect(() => {
		if (activeSection !== 'announcements') return;
		const initialLoad = window.setTimeout(() => {
			void load().catch((caught: unknown) => {
				void showAlert({
					title: 'Could not load announcements',
					message: errorMessage(caught, 'Try again.'),
					tone: 'danger',
				});
			});
		}, 0);
		return () => {
			window.clearTimeout(initialLoad);
		};
	}, [activeSection, load, showAlert]);

	if (activeSection !== 'announcements') return null;

	function openForm() {
		setStartsAt(formatLondonInput(Date.now()));
		setFormOpen(true);
	}

	function closeForm() {
		setFormOpen(false);
		setText('');
		setStartsAt('');
		setEndsAt('');
		setLinkUrl('');
	}

	function save(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		void (async () => {
			try {
				const response = await fetch('/api/admin/announcements', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ text, startsAt, endsAt, linkUrl }),
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to create announcement'));
				closeForm();
				await load();
			} catch (caught) {
				await showAlert({
					title: 'Could not create announcement',
					message: errorMessage(caught, 'Check the announcement and try again.'),
					tone: 'danger',
				});
			} finally {
				setBusy(false);
			}
		})();
	}

	async function move(announcement: Announcement, direction: 'up' | 'down') {
		setMovingId(announcement.id);
		try {
			const response = await fetch(`/api/admin/announcements/${announcement.id}/order`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ direction }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to reorder announcement'));
			setAnnouncements(apiBody<{ announcements: Announcement[] }>(body).announcements);
		} catch (caught) {
			await showAlert({
				title: 'Could not reorder announcement',
				message: errorMessage(caught, 'The order was not changed.'),
				tone: 'danger',
			});
		} finally {
			setMovingId(null);
		}
	}

	return (
		<section className="adminSection">
			<div className="adminSectionHeader">
				<h3>Announcements</h3>
				<p>Schedule server-chat messages. Enter dates and times in British time.</p>
			</div>
			{!formOpen && (
				<button className="announcementAddButton" type="button" onClick={openForm}>
					+ Add new announcement
				</button>
			)}
			{formOpen && (
				<form className="countdownForm" onSubmit={save}>
					<label className="countdownAbstractInput">
						Announcement
						<textarea
							value={text}
							onChange={(event) => {
								setText(event.target.value);
							}}
							maxLength={1000}
							rows={4}
							required
						/>
					</label>
					<label>
						Start (UK)
						<input
							type="datetime-local"
							value={startsAt}
							onChange={(event) => {
								setStartsAt(event.target.value);
							}}
							required
						/>
					</label>
					<label>
						End (UK)
						<input
							type="datetime-local"
							value={endsAt}
							onChange={(event) => {
								setEndsAt(event.target.value);
							}}
							required
						/>
					</label>
					<label className="countdownImageInput">
						Link (optional)
						<input
							type="url"
							value={linkUrl}
							onChange={(event) => {
								setLinkUrl(event.target.value);
							}}
							placeholder="https://example.com/details"
							pattern="https://.*"
							maxLength={2000}
						/>
					</label>
					<div className="countdownFormActions">
						<button disabled={busy}>{busy ? 'Saving...' : 'Save announcement'}</button>
						<button type="button" disabled={busy} onClick={closeForm}>
							Cancel
						</button>
					</div>
				</form>
			)}

			<div className="countdownAdminList">
				{announcements.map((announcement, index) => (
					<article key={announcement.id}>
						<div>
							<strong>{announcement.text}</strong>
							<span>
								{formatLondonDateTime(announcement.startsAtUnixMs)} –{' '}
								{formatLondonDateTime(announcement.endsAtUnixMs)}
							</span>
							{announcement.linkUrl && <span>{announcement.linkUrl}</span>}
						</div>
						<div className="countdownAdminActions">
							<button
								type="button"
								aria-label={`Move announcement ${index + 1} up`}
								disabled={index === 0 || movingId !== null}
								onClick={() => void move(announcement, 'up')}
							>
								↑
							</button>
							<button
								type="button"
								aria-label={`Move announcement ${index + 1} down`}
								disabled={index === announcements.length - 1 || movingId !== null}
								onClick={() => void move(announcement, 'down')}
							>
								↓
							</button>
						</div>
					</article>
				))}
				{announcements.length === 0 && <p>No outstanding announcements.</p>}
			</div>
		</section>
	);
}
