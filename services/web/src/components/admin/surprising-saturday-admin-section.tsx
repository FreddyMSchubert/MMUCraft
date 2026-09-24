'use client';

import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import type { CompletionTarget, EventSummary } from '@/components/surprising-saturday-tab';
import {
	apiMessage,
	formatLondonDateTime,
	formatLondonInput,
	parseManchesterInput,
} from './admin-api';
import killShiftItems from './kill-shift-items.json';

type EditableTarget = Omit<CompletionTarget, 'points'> & { key: string; points: string };

function targetFromId(id: string): EditableTarget {
	return {
		key: crypto.randomUUID(),
		id,
		name:
			id
				.split(':')
				.at(-1)
				?.replaceAll('_', ' ')
				.replace(/\b\w/g, (letter) => letter.toUpperCase()) ?? id,
		url: '',
		points: '1',
	};
}

export function SurprisingSaturdayAdminSection() {
	const { confirm, showAlert } = useSiteAlert();
	const [events, setEvents] = useState<EventSummary[]>([]);
	const [title, setTitle] = useState('');
	const [shortDescription, setShortDescription] = useState('');
	const [preDescription, setPreDescription] = useState('');
	const [description, setDescription] = useState('');
	const [startsAt, setStartsAt] = useState('');
	const [endsAt, setEndsAt] = useState('');
	const [items, setItems] = useState<EditableTarget[]>([]);
	const [editingEvent, setEditingEvent] = useState<EventSummary | null>(null);
	const editingId = editingEvent?.id ?? null;
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	function clearForm() {
		setTitle('');
		setShortDescription('');
		setPreDescription('');
		setDescription('');
		setStartsAt('');
		setEndsAt('');
		setItems([]);
		setEditingEvent(null);
	}
	function updateTarget(key: string, changes: Partial<EditableTarget>) {
		setItems((current) =>
			current.map((target) => (target.key === key ? { ...target, ...changes } : target)),
		);
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
		if (editingEvent && editingEvent.startsAtUnixMs <= Date.now()) {
			if (
				!(await confirm({
					title: 'Change a started event?',
					message:
						'Changing targets, points, or times can change the leaderboard and live server routing.',
					confirmLabel: 'Review final warning',
					tone: 'danger',
				}))
			)
				return;
			if (
				!(await confirm({
					title: 'Final confirmation',
					message: `Save changes to “${editingEvent.title}”? Players may see different results immediately.`,
					confirmLabel: 'Save started event',
					confirmTone: 'danger',
					tone: 'danger',
				}))
			)
				return;
		}
		const creating = editingId === null;
		setBusy(true);
		try {
			const response = await fetch(
				creating
					? '/api/admin/surprising-saturday'
					: `/api/admin/surprising-saturday/${editingId}`,
				{
					method: creating ? 'POST' : 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						title,
						shortDescription,
						preDescription,
						description,
						startsAtUnixMs: parseManchesterInput(startsAt),
						endsAtUnixMs: parseManchesterInput(endsAt),
						criteriaType: 'list_completion',
						items: items.map(({ id, name, url, points }) => ({
							id: id.trim(),
							name: name.trim(),
							url: url.trim(),
							points: Number(points),
						})),
					}),
				},
			);
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not save the event'));
			clearForm();
			setError('');
			await load();
			await showAlert({
				title: creating ? 'Event created' : 'Event updated',
				message: creating
					? 'The event is scheduled. Its title and full description stay hidden until it starts.'
					: 'The changes are now visible to players.',
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
				shortDescription: string;
				preDescription: string;
				description: string;
				startsAtUnixMs: number;
				endsAtUnixMs: number;
				items: CompletionTarget[];
			};
			setEditingEvent(event);
			setTitle(detail.title);
			setShortDescription(detail.shortDescription);
			setPreDescription(detail.preDescription);
			setDescription(detail.description);
			setStartsAt(formatLondonInput(detail.startsAtUnixMs));
			setEndsAt(formatLondonInput(detail.endsAtUnixMs));
			setItems(
				detail.items.map((item) => ({
					...item,
					key: crypto.randomUUID(),
					points: String(item.points),
				})),
			);
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
				title: 'Delete this event?',
				message: `Deleting “${event.title}” permanently removes the event, its participants, and all recorded completions.`,
				confirmLabel: 'Continue to final warning',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		if (
			!(await confirm({
				title: 'Final confirmation',
				message:
					event.startsAtUnixMs <= Date.now()
						? 'This event has started. Deleting it removes its results and can send players back to main. This cannot be undone.'
						: 'This scheduled event and its data will be deleted. This cannot be undone.',
				confirmLabel: 'Permanently delete event',
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
						Use namespaced mob IDs such as minecraft:creeper. Killshift records the
						first kill of each listed mob. Set the points for each target below.
					</p>
				</div>
				<button
					type="button"
					disabled={busy}
					onClick={() => {
						setTitle('Kill Shift');
						setShortDescription(
							'Kill unique mobs, change shape, and earn points for each new form.',
						);
						setPreDescription(
							'A new challenge begins this Saturday. How you win is revealed when the event starts.',
						);
						setDescription(
							'Kill as many unique mobs as you can. Each kill changes you into the mob you killed. Different forms have different abilities. The player with the most points wins. When scores tie, the player who reached that score first wins.',
						);
						setItems(killShiftItems.map(targetFromId));
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
						Short description (plain text)
						<textarea
							value={shortDescription}
							maxLength={280}
							rows={2}
							required
							onChange={(event) => {
								setShortDescription(event.target.value);
							}}
						/>
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
					<div className="eventTargetEditor">
						<h4>Completion targets</h4>
						{items.map((item) => (
							<div className="eventTargetRow" key={item.key}>
								<label>
									Mob ID
									<input
										value={item.id}
										required
										placeholder="minecraft:creeper"
										onChange={(event) => {
											updateTarget(item.key, { id: event.target.value });
										}}
									/>
								</label>
								<label>
									Display name
									<input
										value={item.name}
										maxLength={120}
										required
										placeholder="Creeper"
										onChange={(event) => {
											updateTarget(item.key, { name: event.target.value });
										}}
									/>
								</label>
								<label>
									Link (optional)
									<input
										type="url"
										value={item.url}
										maxLength={2048}
										placeholder="https://..."
										onChange={(event) => {
											updateTarget(item.key, { url: event.target.value });
										}}
									/>
								</label>
								<label>
									Points
									<input
										type="number"
										min={1}
										max={1000}
										step={1}
										value={item.points}
										required
										onChange={(event) => {
											updateTarget(item.key, { points: event.target.value });
										}}
									/>
								</label>
								<button
									type="button"
									aria-label={`Delete ${item.name || item.id || 'target'}`}
									onClick={() => {
										setItems((current) =>
											current.filter((target) => target.key !== item.key),
										);
									}}
								>
									Delete
								</button>
							</div>
						))}
						<button
							type="button"
							disabled={items.length >= 256}
							onClick={() => {
								setItems((current) => [...current, targetFromId('')]);
							}}
						>
							Add new completion target
						</button>
					</div>
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
				<h3>All events</h3>
				<div className="eventHistory">
					{events.map((event) => (
						<div key={event.id}>
							<strong>{event.title ?? 'Hidden until start'}</strong>
							<span>
								{formatLondonDateTime(event.startsAtUnixMs)} · {event.status}
							</span>
							<a href={`/play/event/${event.id}`}>View event</a>
							<button type="button" disabled={busy} onClick={() => void edit(event)}>
								Edit
							</button>
							<button
								type="button"
								disabled={busy}
								onClick={() => void remove(event)}
							>
								Delete
							</button>
						</div>
					))}
				</div>
			</section>
			{error && <p className="authError">{error}</p>}
		</div>
	);
}
