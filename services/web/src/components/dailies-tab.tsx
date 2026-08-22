'use client';

import { useCallback, useEffect, useState } from 'react';
import { MinecraftItemIcon } from '@/components/minecraft-item-icon';
import { apiMessage } from '@/lib/api-response';

interface DailyTask {
	id: string;
	emoji: string;
	name: string;
	description?: string;
	rewardDabloons: number;
	claimed: boolean;
	current: number;
	max: number;
	progressLabel?: string;
	progressUnit?: string;
	advancement?: {
		advancementId: string;
		title: string;
		tabTitle: string;
		iconItem: string;
		modelUrl: string | null;
		textureUrl: string | null;
		baseRewardDabloons: number;
		bonusRewardDabloons: number;
	} | null;
	unavailableMessage?: string;
}

interface DailiesResponse {
	resetHour: number;
	resetTimeZone: string;
	loginStreak: number;
	nextLoginRewardDabloons: number;
	completion: {
		completedTaskCount: number;
		totalTaskCount: number;
		eligible: boolean;
		claimed: boolean;
		baseRewardDabloons: number;
		sundayBonusDabloons: number;
		memberBonusDabloons: number;
		isSunday: boolean;
		isMember: boolean;
		rewardDabloons: number;
	};
	tasks: DailyTask[];
}

export function DailiesTab() {
	const [data, setData] = useState<DailiesResponse | null>(null);
	const [error, setError] = useState('');
	const [message, setMessage] = useState('');
	const [claimingTaskId, setClaimingTaskId] = useState<string | null>(null);

	const load = useCallback(async () => {
		const response = await fetch('/api/dailies', {
			cache: 'no-store',
		});
		const body = await response.json().catch(() => null);

		if (!response.ok) {
			throw new Error(apiMessage(body, 'Failed to load dailies'));
		}

		setData(body as DailiesResponse);
	}, []);

	useEffect(() => {
		let cancelled = false;

		async function loadInitial() {
			try {
				if (!cancelled) {
					await load();
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load dailies');
				}
			}
		}

		void loadInitial();

		return () => {
			cancelled = true;
		};
	}, [load]);

	useEffect(() => {
		const source = new EventSource('/api/dailies/events');
		source.onmessage = (messageEvent) => {
			const event = JSON.parse(String(messageEvent.data)) as { type?: string };
			if (event.type === 'daily-update') {
				void load().catch((caught: unknown) => {
					setError(errorMessage(caught));
				});
			}
		};
		return () => {
			source.close();
		};
	}, [load]);

	async function claimTask(task: DailyTask) {
		setError('');
		setMessage('');
		setClaimingTaskId(task.id);

		const path =
			task.id === 'advancement_bonus'
				? '/api/dailies/advancement-bonus/claim'
				: task.id === 'login_bonus'
					? '/api/dailies/login-bonus/claim'
					: `/api/dailies/tasks/${encodeURIComponent(task.id)}/claim`;

		try {
			const response = await fetch(path, {
				method: 'POST',
			});
			const body = await response.json().catch(() => null);

			if (!response.ok) {
				const text = apiMessage(
					body,
					'You have to be online on the server to receive the money.',
				);
				window.alert(text);
				throw new Error(text);
			}

			setMessage(apiMessage(body, 'Daily bonus claimed.'));
			await load();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to claim daily bonus');
		} finally {
			setClaimingTaskId(null);
		}
	}

	async function finishDailies() {
		setError('');
		setMessage('');
		setClaimingTaskId('daily_completion');

		try {
			const response = await fetch('/api/dailies/completion/claim', { method: 'POST' });
			const body = await response.json().catch(() => null);

			if (!response.ok) {
				const text = apiMessage(body, 'Failed to finish dailies.');
				window.alert(text);
				throw new Error(text);
			}

			setMessage(apiMessage(body, 'Dailies finished!'));
			await load();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to finish dailies');
		} finally {
			setClaimingTaskId(null);
		}
	}

	if (error && !data) {
		return <p className="authError">{error}</p>;
	}

	if (!data) {
		return <p>Loading dailies...</p>;
	}

	const loginBonusClaimed = data.tasks.some((task) => task.id === 'login_bonus' && task.claimed);

	return (
		<div className="dailiesPanel">
			<div className="dailiesHeader">
				<div>
					<h3>Dailies</h3>
					<p>Resets daily at 4 am.</p>
					<p>You have to be online on the server to claim dailies.</p>
				</div>
				<div
					className={`loginStreak${loginBonusClaimed ? ' claimed' : ''}`}
					aria-label={`Login streak: ${data.loginStreak} days`}
				>
					<span>Login Streak</span>
					<strong>{data.loginStreak}</strong>
				</div>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="dailyTasks">
				{data.tasks.map((task) => (
					<div className={`dailyTask${task.claimed ? ' completed' : ''}`} key={task.id}>
						<div className="dailyNumber" aria-hidden="true">
							{task.emoji}
						</div>
						<div className="dailyTaskBody">
							<h4>
								{task.name}
								{task.rewardDabloons > 0
									? ` - ${task.rewardDabloons} Dabloons`
									: ''}
							</h4>
							{task.id === 'advancement_bonus' ? (
								task.advancement ? (
									<div className="dailyAdvancement">
										<MinecraftItemIcon
											className="dailyIcon dailyModelIcon"
											itemId={task.advancement.iconItem}
											modelUrl={task.advancement.modelUrl}
											textureUrl={task.advancement.textureUrl}
										/>
										<div>
											<p>{task.advancement.title}</p>
											<p>Tab: {task.advancement.tabTitle}</p>
											<p>
												{task.claimed ? (
													<>
														Claimed: Finished the advancement and earned{' '}
														{task.advancement.bonusRewardDabloons} bonus
														dabloons.
													</>
												) : task.current >= task.max ? (
													<>
														Complete — claim{' '}
														{task.advancement.bonusRewardDabloons} bonus
														dabloons.
													</>
												) : (
													<>
														Finish it today, then claim{' '}
														{task.advancement.bonusRewardDabloons} bonus
														dabloons in addition to the advancements
														reward.
													</>
												)}
											</p>
										</div>
									</div>
								) : (
									<p>
										{task.unavailableMessage ??
											'No daily advancement is available right now.'}
									</p>
								)
							) : task.id === 'login_bonus' ? (
								<p>
									{task.claimed ? (
										<>
											Claimed: Login again tomorrow for{' '}
											{data.nextLoginRewardDabloons} dabloons.
										</>
									) : (
										<>
											Click claim while online to extend your login streak and
											earn {task.rewardDabloons} dabloons.
										</>
									)}
								</p>
							) : (
								<>
									<p>
										{task.claimed
											? `Claimed: ${task.description ?? task.name}`
											: task.description}
									</p>
									{task.max > 1 && <DailyProgress task={task} />}
									{task.max === 1 &&
										task.current >= task.max &&
										!task.claimed && (
											<p className="dailyTaskReady">
												Complete — claim your reward.
											</p>
										)}
								</>
							)}
						</div>
						<button
							type="button"
							disabled={
								task.claimed ||
								claimingTaskId === task.id ||
								(task.id === 'advancement_bonus' && !task.advancement) ||
								(task.id !== 'login_bonus' &&
									task.id !== 'advancement_bonus' &&
									task.max !== -1 &&
									task.current < task.max)
							}
							onClick={() => claimTask(task)}
						>
							{task.claimed
								? 'Claimed'
								: claimingTaskId === task.id
									? 'Claiming...'
									: task.max !== -1 && task.current < task.max
										? 'In progress'
										: 'Claim'}
						</button>
					</div>
				))}
			</div>

			<div className="dailiesFooter">
				<p className="dailyFootnote">
					You can always earn money, even if today&apos;s dailies are already done or too
					hard, by completing advancements.<br></br>
					If a daily seems impossible or ludicrously frustrating, contact the committee.
				</p>
				<section
					className={`dailyCompletion${data.completion.claimed ? ' claimed' : ''}`}
					aria-labelledby="daily-completion-title"
				>
					<h4 id="daily-completion-title">
						Completed {data.completion.completedTaskCount}/
						{data.completion.totalTaskCount}
					</h4>
					<div
						className="dailyCompletionCalculation"
						aria-label={`Completion reward: ${data.completion.rewardDabloons} dabloons`}
					>
						<span>Base reward</span>
						<strong>+{data.completion.baseRewardDabloons}</strong>
						<span className={data.completion.isSunday ? '' : 'notApplied'}>
							Sunday bonus
						</span>
						<strong className={data.completion.isSunday ? '' : 'notApplied'}>
							+{data.completion.sundayBonusDabloons}
						</strong>
						<span className={data.completion.isMember ? '' : 'notApplied'}>
							Member bonus
						</span>
						<strong className={data.completion.isMember ? '' : 'notApplied'}>
							+{data.completion.memberBonusDabloons}
						</strong>
						<span>Total</span>
						<strong>{data.completion.rewardDabloons} dabloons</strong>
					</div>
					{data.completion.eligible && (
						<button
							type="button"
							disabled={
								data.completion.claimed || claimingTaskId === 'daily_completion'
							}
							onClick={() => void finishDailies()}
						>
							{data.completion.claimed
								? 'Dailies finished'
								: claimingTaskId === 'daily_completion'
									? 'Finishing...'
									: 'Finish dailies'}
						</button>
					)}
				</section>
			</div>
		</div>
	);
}

function DailyProgress({ task }: { task: DailyTask }) {
	const percentage = Math.round((task.current / task.max) * 100);
	const amount = `${task.current}/${task.max}${task.progressUnit ? ` ${task.progressUnit}` : ''}`;
	return (
		<div className="dailyProgress">
			<div>
				<span>{task.progressLabel ?? 'Progress'}</span>
				<strong>{amount}</strong>
			</div>
			<progress value={task.current} max={task.max}>
				{percentage}%
			</progress>
		</div>
	);
}

function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : 'Failed to load dailies';
}
