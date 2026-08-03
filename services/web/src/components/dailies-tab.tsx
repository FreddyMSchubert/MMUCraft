'use client'

import { useCallback, useEffect, useState } from 'react'
import { ASSETS } from '@/lib/assets'

const RESOURCE_PACK_ROOT = ASSETS.minecraft.root

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
	loginStreak: number
	nextLoginRewardDabloons: number
	completion: {
		completedTaskCount: number
		totalTaskCount: number
		eligible: boolean
		claimed: boolean
		baseRewardDabloons: number
		sundayBonusDabloons: number
		memberBonusDabloons: number
		isSunday: boolean
		isMember: boolean
		rewardDabloons: number
	}
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

	async function finishDailies() {
		setError('')
		setMessage('')
		setClaimingTaskId('daily_completion')

		try {
			const response = await fetch('/api/dailies/completion/claim', { method: 'POST' })
			const body = await response.json().catch(() => null)

			if (!response.ok) {
				const text = body?.message ?? 'Failed to finish dailies.'
				window.alert(text)
				throw new Error(text)
			}

			setMessage(body?.message ?? 'Dailies finished!')
			await load()
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to finish dailies')
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
				<div>
					<h3>Dailies</h3>
					<p>Resets daily at 4 am.</p>
					<p>You have to be online on the server to claim dailies.</p>
				</div>
				<div className="loginStreak" aria-label={`Login streak: ${data.loginStreak} days`}>
					<span>Login Streak</span>
					<strong>{data.loginStreak}</strong>
				</div>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="dailyTasks">
				{data.tasks.map((task) => (
					<div className="dailyTask" key={task.id}>
						<div className="dailyNumber">{task.number}</div>
						<div className="dailyTaskBody">
							<h4>{task.title}{task.rewardDabloons > 0 ? (' - ' + task.rewardDabloons + ' Dabloons') : ''}</h4>
							{task.id === 'item_submission' ? (
								<p>
									{task.claimed
										? <>Claimed: Submitted {task.count}x {formatItemName(task.item ?? '')} from your inventory for {task.rewardDabloons} dabloons.</>
										: <>Click claim while holding {task.count}x {formatItemName(task.item ?? '')} in your inventory for {task.rewardDabloons} dabloons.</>}
								</p>
							) : task.id === 'advancement_bonus' ? (
								task.advancement ? (
									<div className="dailyAdvancement">
										<MinecraftModelIcon item={task.advancement.iconItem} />
										<div>
											<p>{task.advancement.title}</p>
											<p>Tab: {task.advancement.tabTitle}</p>
											<p>{task.claimed
												? <>Claimed: Finished the advancement and earned {task.advancement.bonusRewardDabloons} bonus dabloons.</>
												: <>Finish it today, then claim {task.advancement.bonusRewardDabloons} bonus dabloons in addition to the advancements reward.</>}</p>
										</div>
									</div>
								) : (
									<p>{task.unavailableMessage ?? 'No daily advancement is available right now.'}</p>
								)
							) : (
								<p>{task.claimed
									? <>Claimed: Login again tomorrow for {data.nextLoginRewardDabloons} dabloons.</>
									: <>Click claim while online to extend your login streak and earn {task.rewardDabloons} dabloons.</>}</p>
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

			<div className="dailiesFooter">
				<p className="dailyFootnote">
					You can always earn money, even if today&apos;s dailies are already done or too hard, by completing advancements.
				</p>
				<section className="dailyCompletion" aria-labelledby="daily-completion-title">
					<h4 id="daily-completion-title">Completed {data.completion.completedTaskCount}/{data.completion.totalTaskCount}</h4>
					<div className="dailyCompletionCalculation" aria-label={`Completion reward: ${data.completion.rewardDabloons} dabloons`}>
						<span>Base reward</span><strong>+{data.completion.baseRewardDabloons}</strong>
						<span className={data.completion.isSunday ? '' : 'notApplied'}>Sunday bonus</span><strong className={data.completion.isSunday ? '' : 'notApplied'}>+{data.completion.sundayBonusDabloons}</strong>
						<span className={data.completion.isMember ? '' : 'notApplied'}>Member bonus</span><strong className={data.completion.isMember ? '' : 'notApplied'}>+{data.completion.memberBonusDabloons}</strong>
						<span>Total</span><strong>{data.completion.rewardDabloons} dabloons</strong>
					</div>
					{data.completion.eligible && (
						<button type="button" disabled={data.completion.claimed || claimingTaskId === 'daily_completion'} onClick={() => void finishDailies()}>
							{data.completion.claimed ? 'Dailies finished' : claimingTaskId === 'daily_completion' ? 'Finishing...' : 'Finish dailies'}
						</button>
					)}
				</section>
			</div>
		</div>
	)
}

function formatItemName(item: string) {
	return item.replace(/^minecraft:/, '').replace(/_/g, ' ')
}

interface MinecraftModel {
	parent?: string
	textures?: Record<string, string>
}

function MinecraftModelIcon({ item }: { item: string }) {
	const [iconPath, setIconPath] = useState<string | null>(null)

	useEffect(() => {
		let cancelled = false

		async function resolveIcon() {
			const path = await resolveMinecraftModelIcon(item)
			if (!cancelled) {
				setIconPath(path)
			}
		}

		void resolveIcon()

		return () => {
			cancelled = true
		}
	}, [item])

	if (!iconPath) {
		return <div aria-hidden="true" className="dailyIcon dailyIconFallback" />
	}

	return (
		<img
			alt=""
			className="dailyIcon"
			src={iconPath}
			onError={() => setIconPath(null)}
		/>
	)
}

async function resolveMinecraftModelIcon(item: string) {
	const itemId = parseResourceId(item)
	const directItemTexture = texturePath({ namespace: itemId.namespace, path: `item/${itemId.path}` })
	const model = await loadModel({ namespace: itemId.namespace, folder: 'item', path: itemId.path })

	if (!model) {
		return directItemTexture
	}

	const resolved = await resolveModelTextures(model, itemId.namespace, new Set())
	const texture = pickIconTexture(resolved)

	return texture ? texturePath(texture) : directItemTexture
}

async function resolveModelTextures(model: MinecraftModel, namespace: string, seen: Set<string>) {
	const textures: Record<string, string> = {}
	let parentTextures: Record<string, string> = {}

	if (model.parent) {
		const parentId = parseResourceId(model.parent, namespace)
		const key = `${parentId.namespace}:${parentId.path}`

		if (!seen.has(key)) {
			seen.add(key)
			const [folder, ...pathParts] = parentId.path.split('/')
			const parentModel = await loadModel({
				namespace: parentId.namespace,
				folder: folder === 'block' ? 'block' : 'item',
				path: pathParts.length > 0 ? pathParts.join('/') : parentId.path,
			})

			if (parentModel) {
				parentTextures = await resolveModelTextures(parentModel, parentId.namespace, seen)
			}
		}
	}

	Object.assign(textures, parentTextures)

	for (const [key, value] of Object.entries(model.textures ?? {})) {
		textures[key] = resolveTextureReference(value, textures, namespace)
	}

	return textures
}

function pickIconTexture(textures: Record<string, string>) {
	for (const key of ['layer0', 'all', 'front', 'side', 'north', 'south', 'top', 'particle']) {
		const value = textures[key]
		if (value) {
			return parseResourceId(value)
		}
	}

	const firstTexture = Object.values(textures).find(Boolean)
	return firstTexture ? parseResourceId(firstTexture) : null
}

function resolveTextureReference(value: string, textures: Record<string, string>, namespace: string): string {
	let resolved = value
	const seen = new Set<string>()

	while (resolved.startsWith('#')) {
		const key = resolved.slice(1)
		if (seen.has(key)) {
			break
		}

		seen.add(key)
		resolved = textures[key] ?? resolved
	}

	const id = parseResourceId(resolved, namespace)
	return `${id.namespace}:${id.path}`
}

async function loadModel({ namespace, folder, path }: { namespace: string; folder: 'item' | 'block'; path: string }) {
	const response = await fetch(`${RESOURCE_PACK_ROOT}/${namespace}/models/${folder}/${path}.json`, {
		cache: 'force-cache',
	})

	if (!response.ok) {
		return null
	}

	return await response.json().catch(() => null) as MinecraftModel | null
}

function texturePath(texture: { namespace: string; path: string }) {
	return `${RESOURCE_PACK_ROOT}/${texture.namespace}/textures/${texture.path}.png`
}

function parseResourceId(value: string, fallbackNamespace = 'minecraft') {
	const [namespace, path] = value.includes(':')
		? value.split(':', 2)
		: [fallbackNamespace, value]

	return { namespace, path }
}
