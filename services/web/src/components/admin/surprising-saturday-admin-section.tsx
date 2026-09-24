'use client';

import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import type { EventSummary } from '@/components/surprising-saturday-tab';
import {
	apiMessage,
	formatLondonDateTime,
	formatLondonInput,
	parseManchesterInput,
} from './admin-api';
import killShiftItems from './kill-shift-items.json';

export function SurprisingSaturdayAdminSection() {
	const { confirm, showAlert } = useSiteAlert();
	const [events, setEvents] = useState<EventSummary[]>([]);
	const [title, setTitle] = useState('');
	const [preDescription, setPreDescription] = useState('');
	const [description, setDescription] = useState('');
	const [startsAt, setStartsAt] = useState('');
	const [endsAt, setEndsAt] = useState('');
	const [items, setItems] = useState('');
	const [editingId, setEditingId] = useState<number | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	function clearForm() {
		setTitle('');
		setPreDescription('');
		setDescription('');
		setStartsAt('');
		setEndsAt('');
		setItems('');
		setEditingId(null);
	}
	const load = useCallback(async () => {
		const response = await fetch('/api/admin/surprising-saturday', { cache: 'no-store' });
		if (!response.ok) throw new Error('Could not load events');
		setEvents((await response.json()) as EventSummary[]);
	}, []);
	useEffect(() => {
		async function refresh() {
			try {
				await load();
			} catch (caught: unknown) {
				setError(String(caught));
			}
		}
		void refresh();
	}, [load]);

	async function save(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		try {
			const response = await fetch(
				editingId === null
					? '/api/admin/surprising-saturday'
					: `/api/admin/surprising-saturday/${editingId}`,
				{
					method: editingId === null ? 'POST' : 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						title,
						preDescription,
						description,
						startsAtUnixMs: parseManchesterInput(startsAt),
						endsAtUnixMs: parseManchesterInput(endsAt),
						criteriaType: 'list_completion',
						items: items
							.split(/[,\n]/)
							.map((item) => item.trim())
							.filter(Boolean),
					}),
				},
			);
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not save the event'));
			clearForm();
			setError('');
			await load();
			await showAlert({
				title: editingId === null ? 'Event created' : 'Event updated',
				message:
					'The event is scheduled. Its title and full description stay hidden until it starts.',
				tone: 'success',
			});
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not save the event');
		} finally {
			setBusy(false);
		}
	}

	async function edit(event: EventSummary) {
		setBusy(true);
		try {
			const response = await fetch(`/api/admin/surprising-saturday/${event.id}`, {
				cache: 'no-store',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not load the event'));
			const detail = body as {
				title: string;
				preDescription: string;
				description: string;
				startsAtUnixMs: number;
				endsAtUnixMs: number;
				items: string[];
			};
			setEditingId(event.id);
			setTitle(detail.title);
			setPreDescription(detail.preDescription);
			setDescription(detail.description);
			setStartsAt(formatLondonInput(detail.startsAtUnixMs));
			setEndsAt(formatLondonInput(detail.endsAtUnixMs));
			setItems(detail.items.join('\n'));
			setError('');
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not load the event');
		} finally {
			setBusy(false);
		}
	}

	async function remove(event: EventSummary) {
		if (
			!(await confirm({
				title: 'Remove upcoming event?',
				message: 'This removes the scheduled event.',
				confirmLabel: 'Remove event',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		setBusy(true);
		try {
			const response = await fetch(`/api/admin/surprising-saturday/${event.id}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not remove the event'));
			if (editingId === event.id) clearForm();
			setError('');
			await load();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not remove the event');
		} finally {
			setBusy(false);
		}
	}

	return (
		<div className="velocityAdmin">
			<section className="adminSection">
				<div className="adminSectionHeader">
					<h3>
						{editingId === null
							? 'Create Surprising Saturday'
							: 'Edit Surprising Saturday'}
					</h3>
					<p>
						The first scoring type is list completion. Use namespaced mob IDs such as
						minecraft:creeper. Killshift records the first kill of each listed mob.
					</p>
				</div>
				<button
					type="button"
					disabled={busy}
					onClick={() => {
						setTitle('Kill Shift');
						setPreDescription(
							'A new challenge begins this Saturday. How you win is revealed when the event starts.',
						);
						setDescription(
							'Kill as many unique mobs as you can. Each kill changes you into the mob you killed. Different forms have different abilities. The player with the most unique kills wins. When scores tie, the player who reached that score first wins.',
						);
						setItems(killShiftItems.join('\n'));
					}}
				>
					Use Kill Shift template
				</button>
				<form
					className="velocityScheduleForm eventForm"
					onSubmit={(event) => void save(event)}
				>
					<label>
						Title
						<input
							value={title}
							maxLength={120}
							required
							onChange={(event) => {
								setTitle(event.target.value);
							}}
						/>
					</label>
					<label>
						Starts (Europe/London)
						<input
							type="datetime-local"
							value={startsAt}
							required
							onChange={(event) => {
								setStartsAt(event.target.value);
							}}
						/>
					</label>
					<label>
						Ends (Europe/London)
						<input
							type="datetime-local"
							value={endsAt}
							required
							onChange={(event) => {
								setEndsAt(event.target.value);
							}}
						/>
					</label>
					<label>
						Winning condition
						<select defaultValue="list_completion">
							<option value="list_completion">List completion</option>
						</select>
					</label>
					<label>
						Before the event starts (Markdown and HTML)
						<textarea
							value={preDescription}
							maxLength={20_000}
							rows={5}
							onChange={(event) => {
								setPreDescription(event.target.value);
							}}
						/>
					</label>
					<label>
						After the event starts (Markdown and HTML)
						<textarea
							value={description}
							maxLength={20_000}
							rows={8}
							onChange={(event) => {
								setDescription(event.target.value);
							}}
						/>
					</label>
					<label>
						Completion list (one mob ID per line)
						<textarea
							value={items}
							rows={8}
							required
							placeholder={'minecraft:creeper\nminecraft:zombie'}
							onChange={(event) => {
								setItems(event.target.value);
							}}
						/>
					</label>
					<button type="submit" disabled={busy}>
						{editingId === null ? 'Create event' : 'Save changes'}
					</button>
					{editingId !== null && (
						<button type="button" onClick={clearForm}>
							Cancel edit
						</button>
					)}
				</form>
			</section>
			<section className="adminSection">
				<h3>Scheduled and past events</h3>
				<div className="eventHistory">
					{events.map((event) => (
						<div key={event.id}>
							<strong>{event.title ?? 'Hidden until start'}</strong>
							<span>
								{formatLondonDateTime(event.startsAtUnixMs)} · {event.status}
							</span>
							<a href={`/play/event/${event.id}`}>View event</a>
							{event.status === 'upcoming' && (
								<button
									type="button"
									disabled={busy}
									onClick={() => void edit(event)}
								>
									Edit
								</button>
							)}
							{event.status === 'upcoming' && (
								<button
									type="button"
									disabled={busy}
									onClick={() => void remove(event)}
								>
									Remove
								</button>
							)}
						</div>
					))}
				</div>
			</section>
			{error && <p className="authError">{error}</p>}
		</div>
	);
}
