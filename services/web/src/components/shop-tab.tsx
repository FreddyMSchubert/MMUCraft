'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { MinecraftModelRenderer } from '@/lib/minecraft-model-renderer'

type ShopItemType = 'charm' | 'cosmetic' | 'generic'
type ShopOrder = 'random' | 'price-desc' | 'price-asc'

interface ShopItem {
	id: string
	title: string
	type: ShopItemType
	modelType: string
	rarity: string
	priceDabloons: number
	description: string
	unlockMessage: string | null
	unlockPriority: number
	iconUrl: string | null
	renderMode: 'texture' | 'model'
	modelUrl: string | null
	textureUrl: string | null
	animated: boolean
	dyeable: boolean
	animation: {
		frameDelayMs: number
		frames: number[] | null
	} | null
	owned: boolean
	available: boolean
}

interface ShopResponse {
	availability: {
		knowledge: boolean
		charms: boolean
		cosmetics: boolean
	}
	items: ShopItem[]
}

const TYPE_OPTIONS: Array<{ value: 'all' | ShopItemType; label: string }> = [
	{ value: 'all', label: 'All types' },
	{ value: 'charm', label: 'Charms' },
	{ value: 'cosmetic', label: 'Cosmetics' },
	{ value: 'generic', label: 'Items' },
]

const RARITY_OPTIONS = [
	'all',
	'common',
	'uncommon',
	'rare',
	'epic',
	'legendary',
	'mythical',
] as const

const ORDER_OPTIONS: Array<{ value: ShopOrder; label: string }> = [
	{ value: 'random', label: 'Random' },
	{ value: 'price-desc', label: 'Price: high to low' },
	{ value: 'price-asc', label: 'Price: low to high' },
]

export function ShopTab() {
	const [data, setData] = useState<ShopResponse | null>(null)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [typeFilter, setTypeFilter] = useState<'all' | ShopItemType>('all')
	const [rarityFilter, setRarityFilter] = useState<(typeof RARITY_OPTIONS)[number]>('all')
	const [order, setOrder] = useState<ShopOrder>('random')
	const [randomSeed, setRandomSeed] = useState(() => Math.random().toString(36))
	const [buyingItemId, setBuyingItemId] = useState<string | null>(null)
	const [hoveredItemId, setHoveredItemId] = useState<string | null>(null)

	const load = useCallback(async () => {
		const response = await fetch('/api/shop', {
			cache: 'no-store',
		})
		const body = await response.json().catch(() => null)

		if (!response.ok) {
			throw new Error(body?.message ?? 'Failed to load shop')
		}

		setData(body as ShopResponse)
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
					setError(caught instanceof Error ? caught.message : 'Failed to load shop')
				}
			}
		}

		void loadInitial()

		return () => {
			cancelled = true
		}
	}, [load])

	const visibleItems = useMemo(() => {
		const items = data?.items ?? []
		const filtered = items.filter((item) => {
			if (typeFilter !== 'all' && item.type !== typeFilter) return false
			if (rarityFilter !== 'all' && item.rarity !== rarityFilter) return false
			return true
		})

		return [...filtered].sort((left, right) => {
			if (order === 'price-desc') {
				return right.priceDabloons - left.priceDabloons || left.title.localeCompare(right.title, 'en')
			}
			if (order === 'price-asc') {
				return left.priceDabloons - right.priceDabloons || left.title.localeCompare(right.title, 'en')
			}

			return seededRank(`${randomSeed}:${left.id}`) - seededRank(`${randomSeed}:${right.id}`)
		})
	}, [data?.items, order, randomSeed, rarityFilter, typeFilter])

	async function buy(item: ShopItem) {
		if (item.owned || !item.available || buyingItemId) {
			return
		}

		const accepted = window.confirm(`Buy ${item.title} for ${item.priceDabloons} dabloons?`)
		if (!accepted) {
			return
		}

		setBuyingItemId(item.id)
		setError('')
		setMessage('')

		try {
			const response = await fetch('/api/shop/purchase', {
				method: 'POST',
				headers: {
					'content-type': 'application/json',
				},
				body: JSON.stringify({ itemId: item.id }),
			})
			const body = await response.json().catch(() => null)

			if (!response.ok) {
				const text = body?.message ?? 'Purchase failed.'
				window.alert(text)
				throw new Error(text)
			}

			const text = body?.message ?? `${item.title} purchased.`
			window.alert(text)
			setMessage(text)
			await load()
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Purchase failed')
		} finally {
			setBuyingItemId(null)
		}
	}

	if (error && !data) {
		return <p className="authError">{error}</p>
	}

	if (!data) {
		return <p>Loading shop...</p>
	}

	return (
		<div className="shopPanel">
			<div className="shopTop">
				<h3>Shop</h3>
				<div className="shopControls">
					<label>
						<span>Type</span>
						<select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as 'all' | ShopItemType)}>
							{TYPE_OPTIONS.map((option) => (
								<option key={option.value} value={option.value}>{option.label}</option>
							))}
						</select>
					</label>
					<label>
						<span>Rarity</span>
						<select value={rarityFilter} onChange={(event) => setRarityFilter(event.target.value as (typeof RARITY_OPTIONS)[number])}>
							{RARITY_OPTIONS.map((rarity) => (
								<option key={rarity} value={rarity}>{formatOption(rarity)}</option>
							))}
						</select>
					</label>
					<label>
						<span>Order</span>
						<select
							value={order}
							onChange={(event) => {
								const nextOrder = event.target.value as ShopOrder
								setOrder(nextOrder)
								if (nextOrder === 'random') {
									setRandomSeed(Math.random().toString(36))
								}
							}}
						>
							{ORDER_OPTIONS.map((option) => (
								<option key={option.value} value={option.value}>{option.label}</option>
							))}
						</select>
					</label>
				</div>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="shopGrid">
				{visibleItems.map((item) => (
					<button
						type="button"
						key={item.id}
						className={[
							'shopCard',
							`shopCard-${item.type}`,
							`rarity-${item.rarity}`,
							item.owned ? 'owned' : '',
							!item.available ? 'unavailable' : '',
						].filter(Boolean).join(' ')}
						disabled={item.owned || !item.available || buyingItemId !== null}
						onClick={() => void buy(item)}
						onFocus={() => setHoveredItemId(item.id)}
						onBlur={() => setHoveredItemId((current) => current === item.id ? null : current)}
						onPointerEnter={() => setHoveredItemId(item.id)}
						onPointerLeave={() => setHoveredItemId((current) => current === item.id ? null : current)}
						aria-label={`Buy ${item.title}`}
					>
						<div className="shopImageFrame" aria-hidden="true">
							<ShopPreview item={item} hovered={hoveredItemId === item.id} />
							<ShopMetaIcons item={item} />
						</div>

						<div className="shopCardBody">
							<div className="shopBadges">
								<span>{formatOption(item.type)}</span>
								<span>{formatOption(item.rarity)}</span>
							</div>
							<h4>{item.title}</h4>
							<p className="shopPrice">{item.priceDabloons} dabloons</p>
						</div>

						<div className="shopCardFoot">
							<span>{item.owned ? 'Owned' : item.available ? buyingItemId === item.id ? 'Buying...' : 'Buy' : 'Unavailable'}</span>
						</div>

						<p className="shopHoverDescription">{item.description}</p>
					</button>
				))}
			</div>

			{visibleItems.length === 0 && (
				<p className="shopEmptyState">No items match those filters.</p>
			)}
		</div>
	)
}

function ShopPreview({ item, hovered }: { item: ShopItem; hovered: boolean }) {
	if (item.renderMode === 'model' && item.modelUrl && item.textureUrl) {
		return <ShopModelPreview item={item} hovered={hovered} />
	}

	if (item.iconUrl) {
		return <img src={item.iconUrl} alt="" className="shopItemIcon" />
	}

	return <div className="shopItemEmpty" />
}

function ShopModelPreview({ item, hovered }: { item: ShopItem; hovered: boolean }) {
	const hostRef = useRef<HTMLDivElement | null>(null)
	const rendererRef = useRef<MinecraftModelRenderer | null>(null)
	const hoveredRef = useRef(hovered)
	const [visible, setVisible] = useState(false)
	const [failed, setFailed] = useState(false)

	useEffect(() => {
		const host = hostRef.current
		if (!host) return

		const observer = new IntersectionObserver(([entry]) => {
			setVisible(Boolean(entry?.isIntersecting && entry.intersectionRatio > 0))
		}, {
			threshold: 0,
		})
		observer.observe(host)

		return () => observer.disconnect()
	}, [])

	useEffect(() => {
		hoveredRef.current = hovered
		rendererRef.current?.setAutoRotate(hovered)
	}, [hovered])

	useEffect(() => {
		const host = hostRef.current
		if (!host || !visible || !item.modelUrl || !item.textureUrl) {
			rendererRef.current?.destroy()
			rendererRef.current = null
			return
		}
		const hostElement = host

		let cancelled = false
		setFailed(false)

		async function loadPreview() {
			try {
				const response = await fetch(item.modelUrl!, { cache: 'force-cache' })
				if (!response.ok) throw new Error('Model failed to load')
				const model = await response.json()
				if (cancelled || !hostElement.isConnected) return

				rendererRef.current?.destroy()
				const renderer = new MinecraftModelRenderer(hostElement, {
					autoRotate: hoveredRef.current,
					dyeable: item.dyeable,
					frameDelayMs: item.animation?.frameDelayMs,
					frameSequence: item.animation?.frames ?? null,
					textureSource: item.textureUrl!,
					view: item.type === 'cosmetic' ? 'cosmetic' : 'basic3d',
				})
				rendererRef.current = renderer
				await renderer.loadModel(model)
			} catch {
				if (!cancelled) {
					setFailed(true)
				}
			}
		}

		void loadPreview()

		return () => {
			cancelled = true
			rendererRef.current?.destroy()
			rendererRef.current = null
		}
	}, [
		item.animation?.frameDelayMs,
		item.animation?.frames,
		item.dyeable,
		item.modelUrl,
		item.textureUrl,
		item.type,
		visible,
	])

	if (failed && item.iconUrl) {
		return <img src={item.iconUrl} alt="" className="shopItemIcon" />
	}

	return <div ref={hostRef} className="shopModelHost" />
}

function ShopMetaIcons({ item }: { item: ShopItem }) {
	if (!item.animated && !item.dyeable) {
		return null
	}

	return (
		<div className="shopMetaIcons">
			{item.animated && (
				<span className="shopMetaIcon shopMetaIcon-animated" title="Animated texture" aria-label="Animated texture" />
			)}
			{item.dyeable && (
				<span className="shopMetaIcon shopMetaIcon-dyeable" title="Dyeable" aria-label="Dyeable" />
			)}
		</div>
	)
}

function formatOption(value: string) {
	return value
		.split(/[-_ ]+/)
		.filter(Boolean)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ')
}

function seededRank(value: string) {
	let hash = 2166136261
	for (let index = 0; index < value.length; index++) {
		hash ^= value.charCodeAt(index)
		hash = Math.imul(hash, 16777619)
	}

	return hash >>> 0
}
