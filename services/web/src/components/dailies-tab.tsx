'use client'

import { useCallback, useEffect, useState } from 'react'

interface DailyTask {
	id: string
	number: number
	title: string
	rewardDabloons: number
	claimed: boolean
	item?: string
	count?: number
	dabloonsPerItem?: number
	advancement?: {
		advancementId: string
		title: string
		tabTitle: string
		iconItem: string
		baseRewardDabloons: number
		bonusRewardDabloons: number
	} | null
	unavailableMessage?: string
}

interface DailiesResponse {
	resetHour: number
	resetTimeZone: string
	tasks: DailyTask[]
}

export function DailiesTab() {
	const [data, setData] = useState<DailiesResponse | null>(null)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [claimingTaskId, setClaimingTaskId] = useState<string | null>(null)

	const load = useCallback(async () => {
		const response = await fetch('/api/dailies', {
			cache: 'no-store',
		})
		const body = await response.json().catch(() => null)

		if (!response.ok) {
			throw new Error(body?.message ?? 'Failed to load dailies')
		}

		setData(body as DailiesResponse)
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitial() {
			try {
				if (!cancelled) {
					await load()
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load dailies')
				}
			}
		}

		void loadInitial()

		return () => {
			cancelled = true
		}
	}, [load])

	async function claimTask(task: DailyTask) {
		setError('')
		setMessage('')
		setClaimingTaskId(task.id)

		const path = task.id === 'item_submission'
			? '/api/dailies/item-submission/claim'
			: task.id === 'advancement_bonus'
				? '/api/dailies/advancement-bonus/claim'
				: '/api/dailies/login-bonus/claim'

		try {
			const response = await fetch(path, {
				method: 'POST',
			})
			const body = await response.json().catch(() => null)

			if (!response.ok) {
				const text = body?.message ?? 'You have to be online on the server to receive the money.'
				window.alert(text)
				throw new Error(text)
			}

			setMessage(body?.message ?? 'Daily bonus claimed.')
			await load()
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to claim daily bonus')
		} finally {
			setClaimingTaskId(null)
		}
	}

	if (error && !data) {
		return <p className="authError">{error}</p>
	}

	if (!data) {
		return <p>Loading dailies...</p>
	}

	return (
		<div className="dailiesPanel">
			<div className="dailiesHeader">
				<h3>Dailies</h3>
				<p>Resets daily at {data.resetHour}:00 {data.resetTimeZone}.</p>
				<p>You have to be online on the server to redeem dailies.</p>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="dailyTasks">
				{data.tasks.map((task) => (
					<div className="dailyTask" key={task.id}>
						<div className="dailyNumber">{task.number}</div>
						<div className="dailyTaskBody">
							<h4>{task.title}</h4>
							{task.id === 'item_submission' ? (
								<p>
									Claim removes {task.count}x {formatItemName(task.item ?? '')} from your inventory for {task.rewardDabloons} dabloons.
								</p>
							) : task.id === 'advancement_bonus' ? (
								task.advancement ? (
									<div className="dailyAdvancement">
										<img
											alt=""
											className="dailyIcon"
											src={minecraftItemIconPath(task.advancement.iconItem)}
											onError={(event) => {
												event.currentTarget.style.display = 'none'
											}}
										/>
										<div>
											<p>{task.advancement.title}</p>
											<p>Tab: {task.advancement.tabTitle}</p>
											<p>
												Finish it today, then claim {task.advancement.bonusRewardDabloons} bonus dabloons.
											</p>
										</div>
									</div>
								) : (
									<p>{task.unavailableMessage ?? 'No daily advancement is available right now.'}</p>
								)
							) : (
								<p>Click claim while online for {task.rewardDabloons} dabloons.</p>
							)}
						</div>
						<button
							type="button"
							disabled={task.claimed || claimingTaskId === task.id || (task.id === 'advancement_bonus' && !task.advancement)}
							onClick={() => claimTask(task)}
						>
							{task.claimed ? 'Claimed' : claimingTaskId === task.id ? 'Claiming...' : 'Claim'}
						</button>
					</div>
				))}
			</div>

			<p className="dailyFootnote">
				You can always earn money, even if today&apos;s dailies are already done or too hard, by completing advancements.
			</p>
		</div>
	)
}

function formatItemName(item: string) {
	return item.replace(/^minecraft:/, '').replace(/_/g, ' ')
}

function minecraftItemIconPath(item: string) {
	const itemName = item.replace(/^minecraft:/, '')
	return `/assets/mc_respack/assets/minecraft/textures/item/${itemName}.png`
}
