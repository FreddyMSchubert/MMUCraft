'use client'

import { useCallback, useEffect, useState } from 'react'

interface DailyTask {
	id: string
	number: number
	title: string
	rewardDabloons: number
	claimed: boolean
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

	async function claimLoginBonus(taskId: string) {
		setError('')
		setMessage('')
		setClaimingTaskId(taskId)

		try {
			const response = await fetch('/api/dailies/login-bonus/claim', {
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
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="dailyTasks">
				{data.tasks.map((task) => (
					<div className="dailyTask" key={task.id}>
						<div className="dailyNumber">{task.number}</div>
						<div className="dailyTaskBody">
							<h4>{task.title}</h4>
							<p>{task.rewardDabloons} dabloons</p>
						</div>
						<button
							type="button"
							disabled={task.claimed || claimingTaskId === task.id}
							onClick={() => claimLoginBonus(task.id)}
						>
							{task.claimed ? 'Claimed' : claimingTaskId === task.id ? 'Claiming...' : 'Claim'}
						</button>
					</div>
				))}
			</div>
		</div>
	)
}
