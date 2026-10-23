'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { PlayerSelector, type PlayerSelectorOption } from '@/components/player-selector';
import {
	BuildSubmissionDialog,
	BuildTeam,
	type BuildCategory,
	type BuildEventDetail,
	type BuildEventSummary,
	type BuildSubmission,
} from '@/components/build-events-tab';
import {
	formatManchesterDateTime,
	formatManchesterInput,
	parseManchesterInput,
} from '@/lib/date-time';
import { apiMessage, fetchAdmin } from './admin-api';

const ROOT = '/play/admin/build-events';
const API = '/api/admin/build-events';
const dateFields = [
	['startsAtUnixMs', 'Building opens'],
	['buildEndsAtUnixMs', 'Building closes'],
	['votingStartsAtUnixMs', 'Voting opens'],
	['votingEndsAtUnixMs', 'Voting closes'],
	['warpEndsAtUnixMs', 'Warping closes'],
] as const;
type DateField = (typeof dateFields)[number][0];
type EventDraft = {
	title: string;
	shortDescription: string;
	description: string;
	minTeamSize: number;
	maxTeamSize: number;
	categories: BuildCategory[];
} & Record<DateField, string>;

function initialEvent(event?: BuildEventDetail): EventDraft {
	return {
		title: event?.title ?? '',
		shortDescription: event?.shortDescription ?? '',
		description: event?.description ?? '',
		startsAtUnixMs: event ? formatManchesterInput(event.startsAtUnixMs) : '',
		buildEndsAtUnixMs: event ? formatManchesterInput(event.buildEndsAtUnixMs) : '',
		votingStartsAtUnixMs: event ? formatManchesterInput(event.votingStartsAtUnixMs) : '',
		votingEndsAtUnixMs: event ? formatManchesterInput(event.votingEndsAtUnixMs) : '',
		warpEndsAtUnixMs: event ? formatManchesterInput(event.warpEndsAtUnixMs) : '',
		minTeamSize: event?.minTeamSize ?? 1,
		maxTeamSize: event?.maxTeamSize ?? 3,
		categories: event?.categories ?? [
			{ id: 'creativity', name: 'Creativity / uniqueness', description: '', min: 1, max: 10 },
			{ id: 'detail', name: 'Detail / build quality', description: '', min: 1, max: 10 },
			{ id: 'theme', name: 'Adherence to theme', description: '', min: 1, max: 10 },
			{ id: 'enjoyability', name: 'Enjoyability', description: '', min: 1, max: 10 },
		],
	};
}

async function send(path: string, method: string, payload?: unknown) {
	const response = await fetch(path, {
		method,
		headers: payload === undefined ? undefined : { 'content-type': 'application/json' },
		body: payload === undefined ? undefined : JSON.stringify(payload),
	});
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, 'Could not save changes'));
	return body;
}

function EventSettings({ event }: { event?: BuildEventDetail }) {
	const router = useRouter();
	const [draft, setDraft] = useState<EventDraft>(() => initialEvent(event));
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [now, setNow] = useState<number | null>(null);
	useEffect(() => {
		const timer = window.setInterval(() => {
			setNow(Date.now());
		}, 1000);
		return () => {
			window.clearInterval(timer);
		};
	}, []);
	const categoryLocked = Boolean(
		event &&
		(['voting', 'visiting', 'ended'].includes(event.status) ||
			(now !== null && now >= event.votingStartsAtUnixMs)),
	);
	function editCategory(index: number, changes: Partial<BuildCategory>) {
		setDraft((current) => ({
			...current,
			categories: current.categories.map((category, cursor) =>
				cursor === index ? { ...category, ...changes } : category,
			),
		}));
	}
	async function save(formEvent: SyntheticEvent<HTMLFormElement>) {
		formEvent.preventDefault();
		setBusy(true);
		setError('');
		try {
			const dates = Object.fromEntries(
				dateFields.map(([field]) => [field, parseManchesterInput(draft[field])]),
			);
			if (Object.values(dates).some((value) => !Number.isFinite(value)))
				throw new Error('Enter all five dates and times.');
			const result = (await send(
				event ? API + '/' + String(event.id) : API,
				event ? 'PATCH' : 'POST',
				{
					...draft,
					...dates,
					categories: draft.categories,
				},
			)) as BuildEventDetail;
			router.push(ROOT + '/' + String(result.id));
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not save event');
		} finally {
			setBusy(false);
		}
	}
	return (
		<section className="adminSection buildAdminSettings">
			<Link href={event ? ROOT + '/' + String(event.id) : ROOT}>← Competitions</Link>
			<h3>{event ? 'Edit build competition' : 'Create build competition'}</h3>
			<form onSubmit={(formEvent) => void save(formEvent)}>
				<label>
					Title
					<input
						required
						value={draft.title}
						onChange={(e) => {
							setDraft({ ...draft, title: e.target.value });
						}}
					/>
				</label>
				<label>
					Short description (plain text, shown in the banner)
					<textarea
						value={draft.shortDescription}
						maxLength={280}
						rows={2}
						onChange={(e) => {
							setDraft({ ...draft, shortDescription: e.target.value });
						}}
					/>
				</label>
				<label>
					Event description (Markdown)
					<textarea
						value={draft.description}
						rows={8}
						onChange={(e) => {
							setDraft({ ...draft, description: e.target.value });
						}}
					/>
				</label>
				<div className="buildAdminGrid">
					{dateFields.map(([field, label]) => (
						<label key={field}>
							{label}
							<input
								type="datetime-local"
								required
								value={draft[field]}
								onChange={(e) => {
									setDraft({ ...draft, [field]: e.target.value });
								}}
							/>
						</label>
					))}
					<label>
						Minimum team size
						<input
							type="number"
							min={1}
							step={1}
							required
							value={draft.minTeamSize}
							onChange={(e) => {
								setDraft({ ...draft, minTeamSize: Number(e.target.value) });
							}}
						/>
					</label>
					<label>
						Maximum team size
						<input
							type="number"
							min={1}
							step={1}
							required
							value={draft.maxTeamSize}
							onChange={(e) => {
								setDraft({ ...draft, maxTeamSize: Number(e.target.value) });
							}}
						/>
					</label>
				</div>
				<h4>Voting categories</h4>
				{categoryLocked && <p>Categories are locked because voting has started.</p>}
				{draft.categories.map((category, index) => (
					<fieldset key={index} className="buildCategoryEditor" disabled={categoryLocked}>
						<legend>Category {index + 1}</legend>
						<div className="buildAdminGrid">
							<label>
								Name
								<input
									required
									value={category.name}
									onChange={(e) => {
										editCategory(index, { name: e.target.value });
									}}
								/>
							</label>
							<label>
								Minimum
								<input
									type="number"
									step={1}
									required
									value={category.min}
									onChange={(e) => {
										editCategory(index, { min: Number(e.target.value) });
									}}
								/>
							</label>
							<label>
								Maximum
								<input
									type="number"
									step={1}
									required
									value={category.max}
									onChange={(e) => {
										editCategory(index, { max: Number(e.target.value) });
									}}
								/>
							</label>
						</div>
						<label>
							Description
							<textarea
								rows={2}
								value={category.description}
								onChange={(e) => {
									editCategory(index, { description: e.target.value });
								}}
							/>
						</label>
						<button
							type="button"
							disabled={categoryLocked || draft.categories.length === 1}
							onClick={() => {
								setDraft((current) => ({
									...current,
									categories: current.categories.filter(
										(_, cursor) => cursor !== index,
									),
								}));
							}}
						>
							Remove category
						</button>
					</fieldset>
				))}
				{!categoryLocked && (
					<button
						type="button"
						onClick={() => {
							setDraft((current) => ({
								...current,
								categories: [
									...current.categories,
									{
										id:
											'category_' +
											String(Date.now()) +
											'_' +
											String(current.categories.length),
										name: '',
										description: '',
										min: 1,
										max: 10,
									},
								],
							}));
						}}
					>
						Add category
					</button>
				)}
				<div className="buildAdminActions">
					<button type="submit" disabled={busy}>
						{event ? 'Save event' : 'Create event'}
					</button>
					<Link href={event ? ROOT + '/' + String(event.id) : ROOT}>Cancel</Link>
				</div>
				{error && (
					<p role="alert" className="authError">
						{error}
					</p>
				)}
			</form>
		</section>
	);
}

interface SubmissionDraft {
	title: string;
	description: string;
	memberUserIds: number[];
	memberSearch: string;
	images: string[];
	position: { dimension: string; x: number; y: number; z: number; yaw: number; pitch: number };
	area: { firstX: string; firstZ: string; secondX: string; secondZ: string };
}

function initialSubmission(submission?: BuildSubmission): SubmissionDraft {
	return {
		title: submission?.title ?? '',
		description: submission?.description ?? '',
		memberUserIds: submission?.memberUserIds ?? [],
		memberSearch: '',
		images: submission?.images.map((image) => image.shareUrl) ?? [],
		position: submission?.position ?? {
			dimension: 'minecraft:overworld',
			x: 0,
			y: 64,
			z: 0,
			yaw: 0,
			pitch: 0,
		},
		area: {
			firstX: submission?.area ? String(submission.area.firstX) : '',
			firstZ: submission?.area ? String(submission.area.firstZ) : '',
			secondX: submission?.area ? String(submission.area.secondX) : '',
			secondZ: submission?.area ? String(submission.area.secondZ) : '',
		},
	};
}

function SubmissionEditor({
	event,
	submission,
	players,
	onClose,
	onSaved,
}: {
	event: BuildEventDetail;
	submission?: BuildSubmission;
	players: PlayerSelectorOption[];
	onClose: () => void;
	onSaved: () => Promise<void>;
}) {
	const [draft, setDraft] = useState<SubmissionDraft>(() => initialSubmission(submission));
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const selectedPlayer = players.find(
		(player) =>
			player.minecraftUsername.toLowerCase() === draft.memberSearch.trim().toLowerCase(),
	);
	async function fillAdminPosition() {
		setBusy(true);
		setError('');
		try {
			const point = await fetchAdmin<
				SubmissionDraft['position'] & { chunkX: number; chunkZ: number }
			>(API + '/admin-position', 'Could not get the MMU admin position');
			setDraft((current) => ({
				...current,
				position: {
					dimension: point.dimension,
					x: point.x,
					y: point.y,
					z: point.z,
					yaw: point.yaw,
					pitch: point.pitch,
				},
				area:
					!submission && Object.values(current.area).every((value) => value === '')
						? {
								firstX: String(point.chunkX),
								firstZ: String(point.chunkZ),
								secondX: String(point.chunkX),
								secondZ: String(point.chunkZ),
							}
						: current.area,
			}));
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not get the position');
		} finally {
			setBusy(false);
		}
	}
	async function save(formEvent: SyntheticEvent<HTMLFormElement>) {
		formEvent.preventDefault();
		setBusy(true);
		setError('');
		try {
			if (
				draft.memberUserIds.length < event.minTeamSize ||
				draft.memberUserIds.length > event.maxTeamSize
			)
				throw new Error(
					'Select ' +
						String(event.minTeamSize) +
						' to ' +
						String(event.maxTeamSize) +
						' players.',
				);
			const corners = ['firstX', 'firstZ', 'secondX', 'secondZ'] as const;
			if (
				corners.some(
					(field) =>
						!draft.area[field].trim() ||
						!Number.isSafeInteger(Number(draft.area[field])),
				)
			)
				throw new Error('Enter both corner chunks for the build area.');
			await send(
				API +
					'/' +
					String(event.id) +
					'/submissions' +
					(submission ? '/' + String(submission.id) : ''),
				submission ? 'PATCH' : 'POST',
				{
					title: draft.title,
					description: draft.description,
					memberUserIds: draft.memberUserIds,
					images: draft.images.filter((image) => image.trim()),
					position: draft.position,
					area: {
						firstX: Number(draft.area.firstX),
						firstZ: Number(draft.area.firstZ),
						secondX: Number(draft.area.secondX),
						secondZ: Number(draft.area.secondZ),
					},
				},
			);
			await onSaved();
			onClose();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not save submission');
		} finally {
			setBusy(false);
		}
	}
	return (
		<div
			className="buildDialogBackdrop"
			onMouseDown={(e) => {
				if (e.target === e.currentTarget) onClose();
			}}
		>
			<section
				className="buildDialog buildAdminEditor"
				role="dialog"
				aria-modal="true"
				aria-label={submission ? 'Edit submission' : 'Add submission'}
			>
				<button
					className="buildDialogClose"
					type="button"
					onClick={onClose}
					aria-label="Close submission editor"
				>
					×
				</button>
				<h3>{submission ? 'Edit submission' : 'Add submission'}</h3>
				<form onSubmit={(formEvent) => void save(formEvent)}>
					<label>
						Build title
						<input
							required
							value={draft.title}
							onChange={(e) => {
								setDraft({ ...draft, title: e.target.value });
							}}
						/>
					</label>
					<label>
						Build description (plain text)
						<textarea
							rows={5}
							value={draft.description}
							onChange={(e) => {
								setDraft({ ...draft, description: e.target.value });
							}}
						/>
					</label>
					<h4>
						Team ({draft.memberUserIds.length} / {event.minTeamSize}–{event.maxTeamSize}
						)
					</h4>
					<div className="buildAdminTeam">
						{draft.memberUserIds.map((id) => (
							<button
								type="button"
								key={id}
								onClick={() => {
									setDraft((current) => ({
										...current,
										memberUserIds: current.memberUserIds.filter(
											(item) => item !== id,
										),
									}));
								}}
							>
								{players.find((player) => player.id === id)?.minecraftUsername ??
									id}{' '}
								×
							</button>
						))}
					</div>
					<div className="buildAdminAddPlayer">
						<PlayerSelector
							options={players}
							value={draft.memberSearch}
							onChange={(memberSearch) => {
								setDraft({ ...draft, memberSearch });
							}}
							datalistId="build-team-player-search"
							ariaLabel="Search server players"
						/>
						<button
							type="button"
							disabled={
								!selectedPlayer ||
								draft.memberUserIds.includes(selectedPlayer.id) ||
								draft.memberUserIds.length >= event.maxTeamSize
							}
							onClick={() => {
								if (selectedPlayer)
									setDraft((current) => ({
										...current,
										memberSearch: '',
										memberUserIds: [
											...current.memberUserIds,
											selectedPlayer.id,
										],
									}));
							}}
						>
							Add player
						</button>
					</div>
					<h4>Warp position</h4>
					<button type="button" disabled={busy} onClick={() => void fillAdminPosition()}>
						Use current MMU admin position
					</button>
					<div className="buildAdminGrid">
						<label>
							Dimension
							<input
								required
								value={draft.position.dimension}
								onChange={(e) => {
									setDraft({
										...draft,
										position: {
											...draft.position,
											dimension: e.target.value,
										},
									});
								}}
							/>
						</label>
						{(['x', 'y', 'z', 'yaw', 'pitch'] as const).map((field) => (
							<label key={field}>
								{field.toUpperCase()}
								<input
									type="number"
									required
									step={field === 'yaw' || field === 'pitch' ? 'any' : 1}
									value={draft.position[field]}
									onChange={(e) => {
										setDraft({
											...draft,
											position: {
												...draft.position,
												[field]: Number(e.target.value),
											},
										});
									}}
								/>
							</label>
						))}
					</div>
					<h4>Build area: two opposite corner chunks</h4>
					<p>
						The warp position must be inside this area. You can edit the corners later.
						The full height of each chunk counts. On a new submission, using the MMU
						admin position also fills empty corners with that chunk.
					</p>
					<div className="buildAdminGrid">
						{(['firstX', 'firstZ', 'secondX', 'secondZ'] as const).map((field) => (
							<label key={field}>
								{field}
								<input
									type="number"
									step={1}
									required
									value={draft.area[field]}
									onChange={(e) => {
										setDraft({
											...draft,
											area: { ...draft.area, [field]: e.target.value },
										});
									}}
								/>
							</label>
						))}
					</div>
					<h4>Screenshots ({draft.images.length} / 10)</h4>
					<p>Paste individual public Google Drive image share links.</p>
					{draft.images.map((image, index) => (
						<div className="buildAdminImageRow" key={index}>
							<input
								type="url"
								value={image}
								aria-label={'Screenshot ' + String(index + 1)}
								placeholder="https://drive.google.com/file/d/…/view"
								onChange={(e) => {
									setDraft((current) => ({
										...current,
										images: current.images.map((item, cursor) =>
											cursor === index ? e.target.value : item,
										),
									}));
								}}
							/>
							<button
								type="button"
								onClick={() => {
									setDraft((current) => ({
										...current,
										images: current.images.filter(
											(_, cursor) => cursor !== index,
										),
									}));
								}}
							>
								Remove
							</button>
						</div>
					))}
					{draft.images.length < 10 && (
						<button
							type="button"
							onClick={() => {
								setDraft((current) => ({
									...current,
									images: [...current.images, ''],
								}));
							}}
						>
							Add screenshot link
						</button>
					)}
					<div className="buildAdminActions">
						<button type="submit" disabled={busy}>
							Save submission
						</button>
						<button type="button" onClick={onClose}>
							Cancel
						</button>
					</div>
					{error && (
						<p className="authError" role="alert">
							{error}
						</p>
					)}
				</form>
			</section>
		</div>
	);
}

export function BuildEventsAdminSection({ subroute }: { subroute: string[] }) {
	const router = useRouter();
	const { confirm } = useSiteAlert();
	const [events, setEvents] = useState<BuildEventSummary[]>([]);
	const [detail, setDetail] = useState<BuildEventDetail | null>(null);
	const [players, setPlayers] = useState<PlayerSelectorOption[]>([]);
	const [editingSubmission, setEditingSubmission] = useState<BuildSubmission | 'new' | null>(
		null,
	);
	const [viewingSubmission, setViewingSubmission] = useState<BuildSubmission | null>(null);
	const [error, setError] = useState('');
	const [busy, setBusy] = useState(false);
	const id = subroute[0] && subroute[0] !== 'new' ? Number(subroute[0]) : null;
	const settings = subroute[0] === 'new' || subroute[1] === 'edit';
	const refresh = useCallback(async () => {
		const list = await fetchAdmin<BuildEventSummary[]>(API, 'Could not load build events');
		setEvents(list);
		if (id !== null && Number.isSafeInteger(id)) {
			setDetail(
				await fetchAdmin<BuildEventDetail>(
					API + '/' + String(id),
					'Could not load this competition',
				),
			);
		} else setDetail(null);
	}, [id]);
	useEffect(() => {
		const timer = window.setTimeout(() => {
			void refresh().catch((caught: unknown) => {
				setError(String(caught));
			});
		}, 0);
		return () => {
			window.clearTimeout(timer);
		};
	}, [refresh]);
	useEffect(() => {
		void fetchAdmin<{ players: PlayerSelectorOption[] }>(
			'/api/admin/players',
			'Could not load server players',
		)
			.then((body) => {
				setPlayers(body.players);
			})
			.catch((caught: unknown) => {
				setError(String(caught));
			});
	}, []);
	async function change(path: string, method: string, payload?: unknown) {
		setBusy(true);
		setError('');
		try {
			await send(path, method, payload);
			await refresh();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not save changes');
		} finally {
			setBusy(false);
		}
	}
	async function removeEvent(event: BuildEventSummary) {
		if (
			!(await confirm({
				title: 'Permanently delete competition?',
				message:
					'Delete "' +
					event.title +
					'" and all its submissions and votes? This cannot be undone.',
				confirmLabel: 'Delete competition and votes',
				confirmTone: 'danger',
			}))
		)
			return;
		await change(API + '/' + String(event.id), 'DELETE');
		if (id === event.id) router.push(ROOT);
	}
	async function removeSubmission(submission: BuildSubmission) {
		if (
			!detail ||
			!(await confirm({
				title: 'Permanently delete submission?',
				message:
					'Delete "' +
					(submission.title ?? 'this build') +
					'" and all votes for it? This cannot be undone.',
				confirmLabel: 'Delete submission and votes',
				confirmTone: 'danger',
			}))
		)
			return;
		await change(
			API + '/' + String(detail.id) + '/submissions/' + String(submission.id),
			'DELETE',
		);
	}
	const selected = detail?.id === id ? detail : null;
	const selectedSubmission =
		viewingSubmission && selected?.submissions.find((item) => item.id === viewingSubmission.id);
	return (
		<div className="buildAdmin">
			{settings ? (
				subroute[0] === 'new' || selected ? (
					<EventSettings key={subroute.join('/')} event={selected ?? undefined} />
				) : (
					<section className="adminSection">
						<p>Loading event settings…</p>
					</section>
				)
			) : (
				<>
					<section className="adminSection buildAdminList">
						<h3>Build competitions</h3>
						<div className="buildAdminEventList">
							{events.map((event) => (
								<Link
									key={event.id}
									href={ROOT + '/' + String(event.id)}
									className={id === event.id ? 'active' : ''}
								>
									<strong>{event.title}</strong>
									<span>
										{formatManchesterDateTime(event.startsAtUnixMs)} ·{' '}
										{event.status}
									</span>
								</Link>
							))}
						</div>
						<Link className="buildAdminAdd" href={ROOT + '/new'}>
							+ Add competition
						</Link>
					</section>
					{selected && (
						<section className="adminSection buildAdminCompetition">
							<div className="buildAdminHeading">
								<div>
									<h3>{selected.title}</h3>
									<p>
										{selected.status} · {selected.submissions.length}{' '}
										submissions
									</p>
								</div>
								<div className="buildAdminActions">
									<Link href={'/play/event/build/' + String(selected.id)}>
										Public page
									</Link>
									<Link href={ROOT + '/' + String(selected.id) + '/edit'}>
										Edit event settings
									</Link>
									<button
										type="button"
										disabled={busy}
										onClick={() => void removeEvent(selected)}
									>
										Delete event
									</button>
								</div>
							</div>
							<div className="buildAdminActions">
								<button
									type="button"
									onClick={() => {
										setEditingSubmission('new');
									}}
								>
									+ Add submission
								</button>
								{(selected.status === 'visiting' ||
									selected.status === 'ended') && (
									<button
										type="button"
										disabled={busy}
										onClick={() =>
											void change(
												API + '/' + String(selected.id) + '/reveal',
												'PATCH',
												{ revealed: !selected.resultsRevealed },
											)
										}
									>
										{selected.resultsRevealed
											? 'Hide public results'
											: 'Reveal public results'}
									</button>
								)}
							</div>
							<div className="buildAdminSubmissionList">
								{selected.submissions.map((submission) => (
									<article key={submission.id} className="buildAdminSubmission">
										<div className="buildAdminSubmissionSummary">
											<div>
												<strong>
													{submission.rank
														? '#' + String(submission.rank) + ' '
														: ''}
													{submission.title}
												</strong>
												<BuildTeam members={submission.team} />
												<span>
													{submission.voteCount} counted votes ·{' '}
													{submission.score
														? submission.score.total.toFixed(3) +
															' points'
														: 'No score yet'}
												</span>
											</div>
											<div className="buildAdminActions">
												<button
													type="button"
													onClick={() => {
														setViewingSubmission(submission);
													}}
												>
													Details / visit
												</button>
												<button
													type="button"
													onClick={() => {
														setEditingSubmission(submission);
													}}
												>
													Edit
												</button>
												<button
													type="button"
													disabled={busy}
													onClick={() =>
														void removeSubmission(submission)
													}
												>
													Delete
												</button>
											</div>
										</div>
										<details className="buildAdminVotes">
											<summary>
												{submission.votes?.length ?? 0} individual votes
											</summary>
											{submission.score?.averages.map((average) => (
												<span key={average.id}>
													{selected.categories.find(
														(c) => c.id === average.id,
													)?.name ?? average.id}
													: {average.average.toFixed(3)} ·{' '}
												</span>
											))}
											{(submission.votes ?? []).map((vote) => (
												<div className="buildAdminVote" key={vote.voter.id}>
													<span>
														{vote.voter.name}:{' '}
														{selected.categories
															.map(
																(category) =>
																	category.name +
																	' ' +
																	String(
																		vote.scores[category.id],
																	),
															)
															.join(', ')}
													</span>
													{vote.selfVote && (
														<span> · Own build, uncounted</span>
													)}
													<label>
														<input
															type="checkbox"
															checked={vote.excluded}
															onChange={(e) =>
																void change(
																	API +
																		'/' +
																		String(selected.id) +
																		'/submissions/' +
																		String(submission.id) +
																		'/votes/' +
																		String(vote.voter.id),
																	'PATCH',
																	{ excluded: e.target.checked },
																)
															}
														/>
														Exclude this vote
													</label>
													<label>
														<input
															type="checkbox"
															checked={vote.globallyExcluded}
															onChange={(e) =>
																void change(
																	API +
																		'/' +
																		String(selected.id) +
																		'/voters/' +
																		String(vote.voter.id),
																	'PATCH',
																	{ excluded: e.target.checked },
																)
															}
														/>
														Exclude this player from the competition
													</label>
												</div>
											))}
										</details>
									</article>
								))}
								{selected.submissions.length === 0 && <p>No submissions yet.</p>}
							</div>
						</section>
					)}
				</>
			)}
			{editingSubmission && selected && (
				<SubmissionEditor
					key={editingSubmission === 'new' ? 'new' : editingSubmission.id}
					event={selected}
					submission={editingSubmission === 'new' ? undefined : editingSubmission}
					players={players}
					onClose={() => {
						setEditingSubmission(null);
					}}
					onSaved={refresh}
				/>
			)}
			{selected && selectedSubmission && (
				<BuildSubmissionDialog
					eventId={selected.id}
					stage={selected.status}
					submission={selectedSubmission}
					categories={selected.categories}
					onClose={() => {
						setViewingSubmission(null);
					}}
					onUpdated={refresh}
				/>
			)}
			{error && (
				<p role="alert" className="authError">
					{error}
				</p>
			)}
		</div>
	);
}
