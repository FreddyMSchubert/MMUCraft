'use client';

import { useEffect, useState } from 'react';
import { AdventCalendarView } from '@/components/advent-calendar-view';
import { AdventGrid, type MinedBlock } from '@/components/advent-grid';
import { useSiteAlert } from '@/components/site-alert';
import { ASSETS } from '@/lib/assets';
import { perfectStreakTotal, type AdventLayout, type AdventRewardPreview } from '@/lib/advent';

interface AdminCalendar {
	layout: AdventLayout;
	revision: number;
	rewards: { rewards: AdventRewardPreview[] } | null;
	streakLocked: boolean;
}

const NEW_LAYER = { url: `${ASSETS.minecraft.vanilla}/textures/block/stone.png`, rotation: 0 };

export function AdventAdminSection() {
	const [server, setServer] = useState<AdminCalendar | null>(null);
	const [layout, setLayout] = useState<AdventLayout | null>(null);
	const [selected, setSelected] = useState<{ x: number; y: number }>({ x: 5, y: 3 });
	const [mode, setMode] = useState<'edit' | 'simulate'>('edit');
	const [simulatedDay, setSimulatedDay] = useState(1);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const [busyDay, setBusyDay] = useState<number | null>(null);
	const [saving, setSaving] = useState(false);
	const [simulatedClaims, setSimulatedClaims] = useState<number[]>([]);
	const [simulatedSecretClaimed, setSimulatedSecretClaimed] = useState(false);
	const { showAlert } = useSiteAlert();

	useEffect(() => {
		let cancelled = false;
		void fetch('/api/admin/advent', { cache: 'no-store' })
			.then(async (response) => {
				if (!response.ok) throw new Error('The Advent editor is unavailable.');
				return (await response.json()) as AdminCalendar;
			})
			.then((value) => {
				if (!cancelled) {
					setServer(value);
					setLayout(value.layout);
				}
			})
			.catch((cause: unknown) => {
				if (!cancelled)
					setError(cause instanceof Error ? cause.message : 'Could not load Advent.');
			});
		return () => {
			cancelled = true;
		};
	}, []);

	function edit(change: (draft: AdventLayout) => void) {
		setLayout((current) => {
			if (!current) return current;
			const draft = structuredClone(current);
			change(draft);
			return draft;
		});
		setNotice('');
	}

	function moveRow(y: number, direction: -1 | 1) {
		if (!layout || y + direction < 0 || y + direction >= layout.rows.length) return;
		edit((draft) => {
			[draft.rows[y], draft.rows[y + direction]] = [draft.rows[y + direction], draft.rows[y]];
			for (const day of draft.days) {
				if (day.y === y) day.y = y + direction;
				else if (day.y === y + direction) day.y = y;
			}
		});
		setSelected({
			...selected,
			y: selected.y === y ? y + direction : selected.y === y + direction ? y : selected.y,
		});
	}

	function removeRow(y: number) {
		if (!layout || layout.rows.length <= 1) return;
		edit((draft) => {
			draft.rows.splice(y, 1);
			for (const day of draft.days) {
				if (day.y === y) {
					day.x = -1;
					day.y = -1;
				} else if (day.y > y) day.y--;
			}
		});
		setSelected({ x: 0, y: Math.min(y, layout.rows.length - 2) });
	}

	function editLayer(
		group: 'background' | 'foreground',
		index: number,
		field: 'url' | 'rotation',
		value: string,
	) {
		edit((draft) => {
			const cell = draft.rows[selected.y][selected.x];
			if (cell) {
				if (field === 'rotation') cell[group][index].rotation = Number(value);
				else cell[group][index].url = value;
			}
		});
	}

	function assignDay(value: number) {
		edit((draft) => {
			const old = draft.days.find((day) => day.x === selected.x && day.y === selected.y);
			if (old) {
				old.x = -1;
				old.y = -1;
			}
			if (value) {
				const day = draft.days[value - 1];
				day.x = selected.x;
				day.y = selected.y;
			}
		});
	}

	async function save() {
		if (!layout || !server) return;
		setSaving(true);
		setError('');
		try {
			const response = await fetch('/api/admin/advent', {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ layout, revision: server.revision }),
			});
			const data = (await response.json()) as { revision?: number; message?: string };
			if (!response.ok) throw new Error(data.message ?? 'Could not save the calendar.');
			setServer({ ...server, layout, revision: data.revision ?? server.revision + 1 });
			setSimulatedClaims([]);
			setSimulatedSecretClaimed(false);
			setNotice('Calendar layout saved for everyone.');
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not save the calendar.');
		} finally {
			setSaving(false);
		}
	}

	async function giveToAdmin(day: number) {
		setBusyDay(day);
		setError('');
		setNotice('');
		try {
			const response = await fetch(`/api/admin/advent/${day}/preview-gift`, {
				method: 'POST',
			});
			const data = (await response.json()) as { message?: string };
			if (!response.ok)
				throw new Error(data.message ?? 'Could not deliver the preview gift.');
			setNotice(data.message ?? `Day ${day} was delivered to MMUAdmin.`);
		} catch (cause) {
			setError(
				cause instanceof Error ? cause.message : 'Could not deliver the preview gift.',
			);
		} finally {
			setBusyDay(null);
		}
	}

	async function simulateMine(x: number, y: number): Promise<MinedBlock> {
		try {
			const response = await fetch(`/api/admin/advent/${simulatedDay}/mine`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ x, y }),
			});
			const result = (await response.json()) as MinedBlock & { message?: string };
			if (!response.ok) throw new Error(result.message ?? 'Could not inspect the block.');
			return result;
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not inspect the block.');
			return { secret: false };
		}
	}

	function simulateGift(day: number) {
		const reward = server?.rewards?.rewards.find((entry) => entry.day === day);
		const settings = server?.layout.days[day - 1];
		const available =
			!!settings && day <= simulatedDay && simulatedDay <= day + settings.lookbackDays;
		if (available) setSimulatedClaims((current) => [...current, day]);
		void showAlert({
			title: `Day ${day}: ${reward?.title ?? 'Present'}`,
			message: (
				<div>
					{reward ? (
						<>
							<p>{reward.note}</p>
							{reward.alternate_note && <p>Alternate: {reward.alternate_note}</p>}
						</>
					) : (
						<p>Reward details are unavailable while Minecraft is offline.</p>
					)}
					<p>Streak boost: +{settings?.streakIncrease ?? 0} Dabloons.</p>
					<p>
						{available
							? 'This gift is claimable on the simulated day.'
							: 'This gift is outside its claim window on the simulated day.'}
					</p>
					<p>Simulation only. No backpack or money was granted.</p>
				</div>
			),
		});
	}

	function simulateSecret() {
		setSimulatedSecretClaimed(true);
		void showAlert({
			title: 'Hidden present found',
			message: `This would award ${Math.max(8, simulatedDay)} Dabloons. Simulation only: no money was granted.`,
			tone: 'success',
		});
	}

	if (!layout)
		return (
			<section className="adminSection">
				<h2>Advent Calendar</h2>
				<p>{error || 'Loading the calendar editor...'}</p>
			</section>
		);
	const cell = layout.rows[selected.y]?.[selected.x] ?? null;
	const tileDay =
		layout.days.find((day) => day.x === selected.x && day.y === selected.y)?.day ?? 0;
	const perfectTotal = perfectStreakTotal(layout.days);

	return (
		<section className="adminSection adventAdmin">
			<div className="adventAdminHeading">
				<div>
					<h2>Advent Calendar</h2>
					<p>
						Edit the shared world and preview any December day. Public claims use the
						saved layout.
					</p>
				</div>
				<button type="button" onClick={() => void save()} disabled={saving}>
					{saving ? 'Saving...' : 'Save layout'}
				</button>
			</div>
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="adventNotice" role="status">
					{notice}
				</p>
			)}
			<div className="adventAdminToolbar">
				<button
					type="button"
					className={mode === 'edit' ? 'active' : ''}
					onClick={() => {
						setMode('edit');
					}}
				>
					Edit blocks
				</button>
				<button
					type="button"
					className={mode === 'simulate' ? 'active' : ''}
					onClick={() => {
						setMode('simulate');
					}}
				>
					Simulate a day
				</button>
				{mode === 'simulate' && (
					<label>
						December day{' '}
						<input
							type="number"
							min="1"
							max="24"
							value={simulatedDay}
							onChange={(event) => {
								setSimulatedDay(
									Math.max(1, Math.min(24, Number(event.target.value) || 1)),
								);
								setSimulatedClaims([]);
								setSimulatedSecretClaimed(false);
							}}
						/>
					</label>
				)}
				<span>Perfect streak: {perfectTotal} / 400 Dabloons</span>
			</div>
			{mode === 'simulate' ? (
				<AdventCalendarView
					gridKey={`${simulatedDay}-${server?.revision ?? 0}`}
					streakRate={
						simulatedClaims.includes(simulatedDay)
							? (server?.layout.days[simulatedDay - 1]?.streakIncrease ?? 0)
							: 0
					}
					grid={{
						layout: server?.layout ?? layout,
						mode: 'simulate',
						today: simulatedDay,
						claimedDays: simulatedClaims,
						secretClaimed: simulatedSecretClaimed,
						onGift: simulateGift,
						onSecretGift: simulateSecret,
						onFutureDay: () =>
							showAlert({
								title: 'Caught mining ahead',
								message:
									"HEY, CHEATER! You're not allowed to open that one yet! Just for that, I just took 1 dabloon off you, and you're not getting it back! Cheaters never prosper. Simulation only: no Dabloon was taken.",
								tone: 'danger',
							}).then(() => undefined),
						onFullyMined: simulateMine,
					}}
				/>
			) : (
				<AdventGrid
					key="edit"
					layout={layout}
					mode="edit"
					today={simulatedDay}
					selected={selected}
					onSelect={(x, y) => {
						setSelected({ x, y });
					}}
				/>
			)}
			{mode === 'edit' && (
				<div className="adventEditorPanel">
					<h3>
						Block {selected.x + 1}, {selected.y + 1}
					</h3>
					<div className="adventEditorActions">
						<label>
							Day on this block{' '}
							<select
								value={tileDay}
								disabled={!cell?.foreground.length}
								onChange={(event) => {
									assignDay(Number(event.target.value));
								}}
							>
								<option value="0">No day</option>
								{layout.days.map((day) => (
									<option key={day.day} value={day.day}>
										{day.day}
									</option>
								))}
							</select>
						</label>
						{cell ? (
							<button
								type="button"
								onClick={() => {
									edit((draft) => {
										draft.rows[selected.y][selected.x] = null;
										const day = draft.days.find(
											(candidate) =>
												candidate.x === selected.x &&
												candidate.y === selected.y,
										);
										if (day) {
											day.x = -1;
											day.y = -1;
										}
									});
								}}
							>
								Delete block
							</button>
						) : (
							<button
								type="button"
								onClick={() => {
									edit((draft) => {
										draft.rows[selected.y][selected.x] = {
											background: [],
											foreground: [{ ...NEW_LAYER }],
										};
									});
								}}
							>
								Add block
							</button>
						)}
					</div>
					{cell &&
						(['foreground', 'background'] as const).map((group) => (
							<div key={group} className="adventLayerGroup">
								<h4>
									{group === 'foreground'
										? 'Foreground (first layer opens)'
										: 'Background (darker)'}
								</h4>
								{cell[group].map((layer, index) => (
									<div className="adventLayerRow" key={index}>
										<input
											aria-label={`${group} layer ${index + 1} texture URL`}
											value={layer.url}
											onChange={(event) => {
												editLayer(group, index, 'url', event.target.value);
											}}
										/>
										<input
											aria-label={`${group} layer ${index + 1} rotation`}
											type="number"
											min="-360"
											max="360"
											value={layer.rotation}
											onChange={(event) => {
												editLayer(
													group,
													index,
													'rotation',
													event.target.value,
												);
											}}
										/>
										<button
											type="button"
											aria-label={`Move ${group} layer ${index + 1} forward`}
											disabled={index === 0}
											onClick={() => {
												edit((draft) => {
													const current =
														draft.rows[selected.y]?.[selected.x];
													if (!current) return;
													const layers = current[group];
													[layers[index - 1], layers[index]] = [
														layers[index],
														layers[index - 1],
													];
												});
											}}
										>
											↑
										</button>
										<button
											type="button"
											aria-label={`Move ${group} layer ${index + 1} backward`}
											disabled={index === cell[group].length - 1}
											onClick={() => {
												edit((draft) => {
													const current =
														draft.rows[selected.y]?.[selected.x];
													if (!current) return;
													const layers = current[group];
													[layers[index + 1], layers[index]] = [
														layers[index],
														layers[index + 1],
													];
												});
											}}
										>
											↓
										</button>
										<button
											type="button"
											aria-label={`Remove ${group} layer ${index + 1}`}
											onClick={() => {
												edit((draft) => {
													const current =
														draft.rows[selected.y]?.[selected.x];
													if (!current) return;
													current[group].splice(index, 1);
													if (
														group === 'foreground' &&
														current.foreground.length === 0
													) {
														const day = draft.days.find(
															(candidate) =>
																candidate.x === selected.x &&
																candidate.y === selected.y,
														);
														if (day) {
															day.x = -1;
															day.y = -1;
														}
													}
												});
											}}
										>
											×
										</button>
									</div>
								))}
								<button
									type="button"
									onClick={() => {
										edit((draft) => {
											const current = draft.rows[selected.y]?.[selected.x];
											if (current) current[group].push({ ...NEW_LAYER });
										});
									}}
								>
									Add {group} layer
								</button>
							</div>
						))}
				</div>
			)}
			<div className="adventRows">
				<h3>Rows</h3>
				<button
					type="button"
					onClick={() => {
						edit((draft) => {
							draft.rows.push(
								Array.from({ length: draft.rows[0]?.length ?? 16 }, () => null),
							);
						});
					}}
				>
					Add row at bottom
				</button>
				<div>
					{layout.rows.map((_, y) => (
						<span key={y}>
							Row {y + 1}{' '}
							<button
								type="button"
								aria-label={`Move row ${y + 1} up`}
								disabled={y === 0}
								onClick={() => {
									moveRow(y, -1);
								}}
							>
								↑
							</button>
							<button
								type="button"
								aria-label={`Move row ${y + 1} down`}
								disabled={y === layout.rows.length - 1}
								onClick={() => {
									moveRow(y, 1);
								}}
							>
								↓
							</button>
							<button
								type="button"
								aria-label={`Remove row ${y + 1}`}
								disabled={layout.rows.length === 1}
								onClick={() => {
									removeRow(y);
								}}
							>
								×
							</button>
						</span>
					))}
				</div>
			</div>
			<div className="adventDayList">
				<h3>Days and gifts</h3>
				<p>
					Set a late-claim window per day. Late claims give the same gift and no streak
					money. Use the grid to move a day number.
				</p>
				{server?.streakLocked && (
					<p>Streak increases are locked because a player has claimed a present.</p>
				)}
				<div className="adventDayCards">
					{layout.days.map((day) => {
						const reward = server?.rewards?.rewards.find(
							(item) => item.day === day.day,
						);
						return (
							<article key={day.day} className="adventDayCard">
								<div className="adventDayCardHead">
									<strong>Day {day.day}</strong>
									<span>
										{day.x < 0
											? 'Unplaced'
											: `Column ${day.x + 1}, row ${day.y + 1}`}
									</span>
								</div>
								<p>
									{reward?.title ??
										'Reward preview unavailable while Minecraft is offline.'}
								</p>
								{reward && (
									<>
										<p>{reward.note}</p>
										{reward.alternate_note && (
											<p>Alternate: {reward.alternate_note}</p>
										)}
									</>
								)}
								<div className="adventDayFields">
									<label>
										Late days{' '}
										<input
											type="number"
											min="0"
											max="31"
											value={day.lookbackDays}
											onChange={(event) => {
												edit((draft) => {
													draft.days[day.day - 1].lookbackDays = Number(
														event.target.value,
													);
												});
											}}
										/>
									</label>
									<label>
										Streak increase{' '}
										<input
											type="number"
											min="0"
											max="100"
											disabled={server?.streakLocked}
											value={day.streakIncrease}
											onChange={(event) => {
												edit((draft) => {
													draft.days[day.day - 1].streakIncrease = Number(
														event.target.value,
													);
												});
											}}
										/>
									</label>
								</div>
								<button
									type="button"
									disabled={busyDay !== null}
									onClick={() => void giveToAdmin(day.day)}
								>
									{busyDay === day.day ? 'Sending...' : 'Give to MMUAdmin'}
								</button>
								<small>Repeatable. MMUAdmin must be online. No streak money.</small>
							</article>
						);
					})}
				</div>
			</div>
		</section>
	);
}
