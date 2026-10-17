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

type EditableTarget = Omit<CompletionTarget, 'points' | 'amount'> & {
	key: string;
	points: string;
	amount: string;
	description: string;
};
type RewardKey = 'first' | 'second' | 'third' | 'fourth' | 'fifth' | 'participation';
const rewardPlaces: { key: RewardKey; label: string }[] = [
	{ key: 'first', label: '1st place' },
	{ key: 'second', label: '2nd place' },
	{ key: 'third', label: '3rd place' },
	{ key: 'fourth', label: '4th place' },
	{ key: 'fifth', label: '5th place' },
	{ key: 'participation', label: 'Participation (6th place and below)' },
];
const emptyRewards = () => ({
	first: '0',
	second: '0',
	third: '0',
	fourth: '0',
	fifth: '0',
	participation: '0',
	participationMinItems: '1',
});

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
		amount: '',
		description: '',
	};
}

const chunklockGoals = [
	['unlock_1', 'Help unlock 1 chunk', '', 20],
	['unlock_3', 'Help unlock 3 chunks', '', 20],
	['unlock_5', 'Help unlock 5 chunks', '', 20],
	...([100, 200, 300, 400, 500, 750, 1000] as const).map(
		(count) => [`online_${count}`, `Be online when chunk #${count} opens`, '', 10] as const,
	),
	['villager', 'Interact with a villager', '', 10],
	['swim', 'Swim in water', '', 10],
	['desert', 'Stand in a desert', '', 5],
	['illager', 'Help kill a pillager/illager', '', 25],
	['raid_experience', 'Experience a raid', '', 15],
	['ravager', 'Help kill a ravager', '', 25],
	['raid_survive', 'Survive a raid', '', 175],
	['trial_spawner', 'Activate a trial spawner', '', 25],
	['trial_key', 'Use a trial key', '', 75],
	['ominous_trial_key', 'Use an ominous trial key', '', 125],
	['wither', 'Help kill a wither', '', 250],
	['dragon', 'Help kill the Ender Dragon', '', 250],
] as const;

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
	const [criteriaType, setCriteriaType] = useState<'list_completion' | 'joined_earning'>(
		'list_completion',
	);
	const [earnings, setEarnings] = useState<
		{ name: string; uuid: string; amount: number; paid: number }[]
	>([]);
	const [rewards, setRewards] = useState(emptyRewards);
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
		setCriteriaType('list_completion');
		setEarnings([]);
		setRewards(emptyRewards());
		setEditingEvent(null);
	}
	function updateTarget(key: string, changes: Partial<EditableTarget>) {
		setItems((current) =>
			current.map((target) => (target.key === key ? { ...target, ...changes } : target)),
		);
	}
	function moveTarget(index: number, direction: -1 | 1) {
		setItems((current) => {
			if (!current[index] || !current[index + direction]) return current;
			const next = [...current];
			[next[index], next[index + direction]] = [next[index + direction], next[index]];
			return next;
		});
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
						'Changing targets, points, rewards, or times can change the leaderboard, payouts, and live server routing.',
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
		if (items.length === 0) {
			setError('Add at least one completion target.');
			return;
		}
		const invalidTarget = items.findIndex(({ id }) => !id.trim() || id.length > 120);
		if (invalidTarget !== -1) {
			setError(`Target ${invalidTarget + 1} needs an ID of up to 120 characters.`);
			return;
		}
		if (new Set(items.map(({ id }) => id.trim())).size !== items.length) {
			setError('Each completion target needs a unique ID.');
			return;
		}
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
						criteriaType,
						items: items.map(({ id, name, url, points, description, amount }) =>
							criteriaType === 'joined_earning'
								? {
										id: id.trim(),
										name: name.trim(),
										description: description.trim(),
										amount: Number(amount),
									}
								: {
										id: id.trim(),
										name: name.trim(),
										url: url.trim(),
										points: Number(points),
									},
						),
						rewards: {
							...Object.fromEntries(
								rewardPlaces.map(({ key }) => [key, Number(rewards[key])]),
							),
							participationMinItems: Number(rewards.participationMinItems),
						},
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
				criteriaType: 'list_completion' | 'joined_earning';
				earnings: { name: string; uuid: string; amount: number; paid: number }[];
				rewards: Record<RewardKey, number> & { participationMinItems: number };
			};
			setEditingEvent(event);
			setCriteriaType(detail.criteriaType);
			setEarnings(detail.earnings);
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
					amount: String(item.amount ?? 0),
					description: item.description ?? '',
				})),
			);
			setRewards({
				first: String(detail.rewards.first),
				second: String(detail.rewards.second),
				third: String(detail.rewards.third),
				fourth: String(detail.rewards.fourth),
				fifth: String(detail.rewards.fifth),
				participation: String(detail.rewards.participation),
				participationMinItems: String(detail.rewards.participationMinItems),
			});
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
					<p>Choose a leaderboard or give each goal its own Dabloon reward.</p>
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
						setCriteriaType('list_completion');
					}}
				>
					Use Kill Shift template
				</button>
				<button
					type="button"
					disabled={busy}
					onClick={() => {
						setTitle('Chunklock');
						setShortDescription(
							'Complete Chunklock goals and earn Dabloons for each one.',
						);
						setPreDescription(
							'A new challenge begins this Saturday. The goals are revealed when the event starts.',
						);
						setDescription(
							'Dabloons are paid after the event ends, on the main server.',
						);
						setCriteriaType('joined_earning');
						setItems(
							chunklockGoals.map(([id, name, description, amount]) => ({
								...targetFromId(`chunklock:${id}`),
								name,
								description,
								amount: String(amount),
							})),
						);
					}}
				>
					Use Chunklock template
				</button>
				{editingId !== null && criteriaType === 'joined_earning' && (
					<details className="eventTargetEditor">
						<summary>Player earnings</summary>
						<p>
							Earned amounts come from recorded goals. Paid amounts come from main
							server payment records.
						</p>
						<div className="adminTableWrap">
							<table className="adminTable">
								<thead>
									<tr>
										<th>Player</th>
										<th>Earned</th>
										<th>Paid</th>
									</tr>
								</thead>
								<tbody>
									{earnings.map((entry) => (
										<tr key={entry.uuid}>
											<td>{entry.name}</td>
											<td>{entry.amount}</td>
											<td>{entry.paid}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</details>
				)}
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
						<select
							value={criteriaType}
							onChange={(event) => {
								setCriteriaType(
									event.target.value as 'list_completion' | 'joined_earning',
								);
							}}
						>
							<option value="list_completion">Leaderboard</option>
							<option value="joined_earning">Goal rewards</option>
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
						<p>Give each goal a unique ID, such as a or find_diamond.</p>
						{items.map((item, index) => (
							<div className="eventTargetRow" key={item.key}>
								<label>
									Goal ID
									<input
										value={item.id}
										maxLength={120}
										required
										placeholder="find_diamond"
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
								{criteriaType === 'list_completion' && (
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
								)}
								{criteriaType === 'joined_earning' && (
									<label>
										Description (optional)
										<input
											value={item.description}
											maxLength={2000}
											onChange={(event) => {
												updateTarget(item.key, {
													description: event.target.value,
												});
											}}
										/>
									</label>
								)}
								<label>
									{criteriaType === 'joined_earning' ? 'Dabloons' : 'Points'}
									<input
										type="number"
										min={1}
										max={criteriaType === 'joined_earning' ? 1_000_000 : 1000}
										step={1}
										value={
											criteriaType === 'joined_earning'
												? item.amount
												: item.points
										}
										required
										onChange={(event) => {
											updateTarget(
												item.key,
												criteriaType === 'joined_earning'
													? { amount: event.target.value }
													: { points: event.target.value },
											);
										}}
									/>
								</label>
								<div className="eventTargetActions">
									<button
										type="button"
										disabled={index === 0}
										aria-label={`Move ${item.name || item.id || 'target'} up`}
										onClick={() => {
											moveTarget(index, -1);
										}}
									>
										Up
									</button>
									<button
										type="button"
										disabled={index === items.length - 1}
										aria-label={`Move ${item.name || item.id || 'target'} down`}
										onClick={() => {
											moveTarget(index, 1);
										}}
									>
										Down
									</button>
									<button
										className="eventTargetDelete"
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
					{criteriaType === 'list_completion' && (
						<div className="eventTargetEditor">
							<h4>Dabloon rewards</h4>
							<p>
								Rewards are paid once after the event ends. Players online on main
								receive them automatically; others receive them on their next
								main-server join. Committee results do not count.
							</p>
							{rewardPlaces.map(({ key, label }) => (
								<label key={key}>
									{label}
									<input
										type="number"
										min={0}
										max={1_000_000}
										step={1}
										required
										value={rewards[key]}
										onChange={(event) => {
											setRewards((current) => ({
												...current,
												[key]: event.target.value,
											}));
										}}
									/>
								</label>
							))}
							<label>
								Completed list items needed for participation
								<input
									type="number"
									min={1}
									max={Math.max(items.length, 1)}
									step={1}
									required
									value={rewards.participationMinItems}
									onChange={(event) => {
										setRewards((current) => ({
											...current,
											participationMinItems: event.target.value,
										}));
									}}
								/>
							</label>
						</div>
					)}
					<button type="submit" disabled={busy}>
						{editingId === null ? 'Create event' : 'Save changes'}
					</button>
					{error && (
						<p className="authError" role="alert">
							{error}
						</p>
					)}
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
		</div>
	);
}
