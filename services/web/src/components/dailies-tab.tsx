'use client';

import { useCallback, useEffect, useState } from 'react';
import { MinecraftItemIcon } from '@/components/minecraft-item-icon';
import { DabloonAmount, DabloonText } from '@/components/dabloon-amount';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import { formatDabloonWord } from '@/lib/dabloons';

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
	const { showAlert } = useSiteAlert();
	const [data, setData] = useState<DailiesResponse | null>(null);
	const [error, setError] = useState('');
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
				void load().catch(() => undefined);
			}
		};
		return () => {
			source.close();
		};
	}, [load]);

	async function claimTask(task: DailyTask) {
		setError('');
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
				throw new Error(
					apiMessage(
						body,
						'Join the Minecraft server, then try to claim this daily again.',
					),
				);
			}

			await load();
		} catch (caught) {
			await showAlert({
				title: `Could not claim “${task.name}”`,
				message:
					caught instanceof Error
						? caught.message
						: 'The daily reward could not be claimed. Please try again.',
				tone: 'danger',
			});
		} finally {
			setClaimingTaskId(null);
		}
	}

	async function finishDailies() {
		setError('');
		setClaimingTaskId('daily_completion');

		try {
			const response = await fetch('/api/dailies/completion/claim', { method: 'POST' });
			const body = await response.json().catch(() => null);

			if (!response.ok) {
				throw new Error(
					apiMessage(
						body,
						'Complete every daily and stay online in Minecraft before claiming the completion reward.',
					),
				);
			}

			await load();
			await showAlert({
				title: 'Daily set complete',
				message: apiMessage(
					body,
					data
						? `You received ${formatDabloonWord(data.completion.rewardDabloons)}. Come back tomorrow for a new set.`
						: 'You received your completion reward. Come back tomorrow for a new set.',
				),
				tone: 'success',
			});
		} catch (caught) {
			await showAlert({
				title: 'Could not claim the completion reward',
				message:
					caught instanceof Error
						? caught.message
						: 'The completion reward could not be claimed. Please try again.',
				tone: 'danger',
			});
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
	const remainingDailies = Math.max(
		0,
		data.completion.totalTaskCount - data.completion.completedTaskCount,
	);

	return (
		<div className="dailiesPanel">
			<div className="dailiesHeader">
				<div>
					<h3>Dailies</h3>
					<p className="tabSubtitle">
						Every day you get a new set of challenges. You can gain a bunch of{' '}
						<DabloonText>Dabloons</DabloonText> by completing them, especially if you
						manage to complete all the dailies on a given day.
					</p>
				</div>
				<div
					className={`loginStreak${loginBonusClaimed ? ' claimed' : ''}`}
					aria-label={`Login streak: ${data.loginStreak} days`}
				>
					<span>Login Streak</span>
					<strong>{data.loginStreak}</strong>
				</div>
			</div>

			<div className="dailyTasks">
				{data.tasks.map((task) => (
					<div className={`dailyTask${task.claimed ? ' completed' : ''}`} key={task.id}>
						<div className="dailyNumber" aria-hidden="true">
							{task.emoji}
						</div>
						<div className="dailyTaskBody">
							<h4>
								{task.name}
								{task.rewardDabloons > 0 && (
									<>
										{' '}
										<DabloonAmount
											amount={task.rewardDabloons}
											format="delta"
										/>
									</>
								)}
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
														<DabloonAmount
															amount={
																task.advancement.bonusRewardDabloons
															}
															format="full"
															tone="inherit"
														/>
														.
													</>
												) : task.current >= task.max ? (
													<>
														Complete — claim{' '}
														<DabloonAmount
															amount={
																task.advancement.bonusRewardDabloons
															}
															format="full"
															tone="inherit"
														/>
														.
													</>
												) : (
													<>
														Finish it today, then claim{' '}
														<DabloonAmount
															amount={
																task.advancement.bonusRewardDabloons
															}
															format="full"
															tone="inherit"
														/>{' '}
														in addition to the advancement reward.
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
											<DabloonAmount
												amount={data.nextLoginRewardDabloons}
												format="full"
												tone="inherit"
											/>
											.
										</>
									) : (
										<>
											Click claim while online to extend your login streak and
											earn{' '}
											<DabloonAmount
												amount={task.rewardDabloons}
												format="full"
												tone="inherit"
											/>
											.
										</>
									)}
								</p>
							) : (
								<>
									<p>
										<DabloonText>
											{task.claimed
												? `Claimed: ${task.description ?? task.name}`
												: (task.description ?? '')}
										</DabloonText>
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
					You can always earn <DabloonText>Dabloons</DabloonText>, even if today&apos;s
					dailies are already done or too hard, by completing advancements.<br></br>
					If a daily seems impossible or ludicrously frustrating, contact the committee.
				</p>
				<section
					className={`dailyCompletion${data.completion.claimed ? ' claimed' : ''}`}
					aria-labelledby="daily-completion-title"
				>
					<h4 id="daily-completion-title">Full completion reward</h4>
					<div
						className="dailyCompletionCalculation"
						aria-label={`Completion reward: ${data.completion.rewardDabloons} Dabloons`}
					>
						<span>Base reward</span>
						<strong>
							<DabloonAmount
								amount={data.completion.baseRewardDabloons}
								tone="inherit"
							/>
						</strong>
						<span className={data.completion.isSunday ? '' : 'notApplied'}>
							Sunday bonus
						</span>
						<strong className={data.completion.isSunday ? '' : 'notApplied'}>
							<DabloonAmount
								amount={data.completion.sundayBonusDabloons}
								tone="inherit"
							/>
						</strong>
						<span className={data.completion.isMember ? '' : 'notApplied'}>
							Member bonus
						</span>
						<strong className={data.completion.isMember ? '' : 'notApplied'}>
							<DabloonAmount
								amount={data.completion.memberBonusDabloons}
								tone="inherit"
							/>
						</strong>
						<span>Total</span>
						<strong>
							<DabloonAmount
								amount={data.completion.rewardDabloons}
								format="full"
								tone="inherit"
							/>
						</strong>
					</div>
					<button
						type="button"
						disabled={
							!data.completion.eligible ||
							data.completion.claimed ||
							claimingTaskId === 'daily_completion'
						}
						onClick={() => void finishDailies()}
					>
						{data.completion.claimed
							? 'Dailies finished'
							: claimingTaskId === 'daily_completion'
								? 'Finishing...'
								: remainingDailies > 0
									? `${remainingDailies} ${remainingDailies === 1 ? 'daily' : 'dailies'} to do`
									: 'All done - claim bonus'}
					</button>
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
