'use client'

import { CSSProperties, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ASSETS } from '@/lib/assets'
import { MinecraftModelRenderer, type MinecraftModelPreviewState } from '@/lib/minecraft-model-renderer'
import { useSiteSettings } from '@/lib/site-settings'

type ShopItemType = 'charm' | 'cosmetic' | 'generic'
type ShopOrder = 'random' | 'alphabetical' | 'alphabetical-desc' | 'rarity' | 'rarity-desc' | 'price-desc' | 'price-asc'
type ShopTagFilter = 'all' | 'dyeable' | 'animated' | 'discounted' | 'sold-out'

interface CharmLevel {
	level: number
	abilityStatusCurrent: string
	upgradeIngredients: string[]
}

interface ShopItem {
	id: string
	title: string
	type: ShopItemType
	modelType: string
	rarity: string
	priceDabloons: number
	originalPriceDabloons: number
	discountedPriceDabloons: number
	isDailyDeal: boolean
	discountPercent: number
	dealMessage: string | null
	description: string
	tooltips: string[]
	unlockMessage: string | null
	unlockWeight: number
	iconUrl: string | null
	renderMode: 'texture' | 'model'
	modelUrl: string | null
	textureUrl: string | null
	animated: boolean
	dyeable: boolean
	animation: { frameDelayMs: number; frames: number[] | null } | null
	charmDetails: { minLevel: number; maxLevel: number; levels: CharmLevel[] } | null
	unlocked: boolean
	available: boolean
}

interface ShopResponse {
	dealDate: string
	shoppingSunday: boolean
	availability: { knowledge: boolean; charms: boolean; cosmetics: boolean }
	items: ShopItem[]
}

const TYPE_OPTIONS: Array<{ value: 'all' | ShopItemType; label: string }> = [
	{ value: 'all', label: 'All' },
	{ value: 'charm', label: 'Charms' },
	{ value: 'cosmetic', label: 'Cosmetics' },
	{ value: 'generic', label: 'Items' },
]

const RARITY_OPTIONS = ['all', 'common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical'] as const
const TAG_OPTIONS: Array<{ value: ShopTagFilter; label: string }> = [
	{ value: 'all', label: 'All' },
	{ value: 'dyeable', label: 'Dyeable' },
	{ value: 'animated', label: 'Animated' },
	{ value: 'discounted', label: 'Discounted' },
	{ value: 'sold-out', label: 'Sold out' },
]
const RARITY_RANK = new Map(RARITY_OPTIONS.map((rarity, index) => [rarity, index]))
const ORDER_OPTIONS: Array<{ value: ShopOrder; label: string }> = [
	{ value: 'random', label: 'Random' },
	{ value: 'alphabetical', label: 'Alphabetical: A–Z' },
	{ value: 'alphabetical-desc', label: 'Alphabetical: Z–A' },
	{ value: 'rarity-desc', label: 'Rarity: highest first' },
	{ value: 'rarity', label: 'Rarity: lowest first' },
	{ value: 'price-desc', label: 'Price: high to low' },
	{ value: 'price-asc', label: 'Price: low to high' },
]

const modelCache = new Map<string, Promise<unknown>>()
const modelPreviewStateCache = new Map<string, MinecraftModelPreviewState>()
let staticRenderQueue = Promise.resolve()

function queueStaticRender(render: () => Promise<void>) {
	const queued = staticRenderQueue.then(render, render)
	staticRenderQueue = queued.catch(() => undefined)
	return queued
}

export function ShopTab({ itemId, onSelectItem }: {
	itemId?: string
	onSelectItem: (itemId: string | null, replace?: boolean) => void
}) {
	const { settings } = useSiteSettings()
	const [data, setData] = useState<ShopResponse | null>(null)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [typeFilter, setTypeFilter] = useState<'all' | ShopItemType>('all')
	const [rarityFilter, setRarityFilter] = useState<(typeof RARITY_OPTIONS)[number]>('all')
	const [tagFilter, setTagFilter] = useState<ShopTagFilter>('all')
	const [order, setOrder] = useState<ShopOrder>('random')
	const [randomSeed, setRandomSeed] = useState(() => Math.random().toString(36))
	const [buyingItemId, setBuyingItemId] = useState<string | null>(null)
	const [hoveredItemId, setHoveredItemId] = useState<string | null>(null)
	const [featuredIndex, setFeaturedIndex] = useState(0)
	const [featuredHovered, setFeaturedHovered] = useState(false)

	const load = useCallback(async () => {
		const response = await fetch('/api/shop', { cache: 'no-store' })
		const body = await response.json().catch(() => null)
		if (!response.ok) throw new Error(body?.message ?? 'Failed to load shop')
		setData(body as ShopResponse)
	}, [])

	useEffect(() => {
		let cancelled = false
		async function loadInitial() {
			try {
				await load()
			} catch (caught) {
				if (!cancelled) setError(caught instanceof Error ? caught.message : 'Failed to load shop')
			}
		}
		void loadInitial()
		return () => { cancelled = true }
	}, [load])

	const dailyDeals = useMemo(() => data?.items.filter((item) => item.isDailyDeal && item.available) ?? [], [data?.items])
	const selectedItem = useMemo(() => itemId ? data?.items.find((item) => item.id === itemId) ?? null : null, [data?.items, itemId])

	useEffect(() => {
		if (data && itemId && !selectedItem) onSelectItem(null, true)
	}, [data, itemId, onSelectItem, selectedItem])

	useEffect(() => {
		if (dailyDeals.length < 2 || featuredHovered) return
		const timer = window.setInterval(() => setFeaturedIndex((current) => (current + 1) % dailyDeals.length), 6500)
		return () => window.clearInterval(timer)
	}, [dailyDeals.length, featuredHovered])

	const visibleItems = useMemo(() => {
		const filtered = (data?.items ?? []).filter((item) => {
			if (typeFilter !== 'all' && item.type !== typeFilter) return false
			if (rarityFilter !== 'all' && item.rarity !== rarityFilter) return false
			if (tagFilter === 'dyeable' && !item.dyeable) return false
			if (tagFilter === 'animated' && !item.animated) return false
			if (tagFilter === 'discounted' && !item.isDailyDeal) return false
			if (tagFilter === 'sold-out' && !isSoldOut(item)) return false
			return true
		})

		return [...filtered].sort((left, right) => {
			if (order === 'price-desc') return effectivePrice(right) - effectivePrice(left) || compareTitles(left, right)
			if (order === 'price-asc') return effectivePrice(left) - effectivePrice(right) || compareTitles(left, right)
			if (order === 'alphabetical') return compareTitles(left, right)
			if (order === 'alphabetical-desc') return compareTitles(right, left)
			if (order === 'rarity') return rarityRank(left) - rarityRank(right) || effectivePrice(left) - effectivePrice(right) || compareTitles(left, right)
			if (order === 'rarity-desc') return rarityRank(right) - rarityRank(left) || effectivePrice(right) - effectivePrice(left) || compareTitles(left, right)
			return seededRank(`${randomSeed}:${left.id}`) - seededRank(`${randomSeed}:${right.id}`)
		})
	}, [data?.items, order, randomSeed, rarityFilter, tagFilter, typeFilter])

	async function buy(item: ShopItem) {
		if (!item.available || buyingItemId) return
		const price = effectivePrice(item)
		if (!window.confirm(`Buy ${item.title} for ${formatDabloons(price)} dabloons?`)) return
		setBuyingItemId(item.id)
		setError('')
		setMessage('')
		try {
			const response = await fetch('/api/shop/purchase', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ itemId: item.id }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(body?.message ?? 'Purchase failed.')
			const text = body?.message ?? `${item.title} purchased.`
			setMessage(text)
			onSelectItem(null, true)
			await load()
		} catch (caught) {
			const text = caught instanceof Error ? caught.message : 'Purchase failed'
			setError(text)
			window.alert(text)
		} finally {
			setBuyingItemId(null)
		}
	}

	if (error && !data) return <p className="authError">{error}</p>
	if (!data) return <p>Loading shop...</p>

	const safeFeaturedIndex = dailyDeals.length ? featuredIndex % dailyDeals.length : 0
	const featured = dailyDeals[safeFeaturedIndex]
	return (
		<div className="shopPanel">
			<div className="shopTop">
				<div><p className="shopEyebrow">The Dabloon Exchange</p><h3>Shop</h3></div>
			</div>

			{featured && <section className="shopDeals" aria-label="Today's deals" onPointerEnter={() => setFeaturedHovered(true)} onPointerLeave={() => setFeaturedHovered(false)}>
				{dailyDeals.length > 1 && <button type="button" className="shopDealArrow previous" aria-label="Previous daily deal" onClick={() => setFeaturedIndex((current) => (current - 1 + dailyDeals.length) % dailyDeals.length)}>‹</button>}
				<div className="shopDealCopy">
					<div className="shopDealHeading"><span className="shopDealSpark">✦</span><div><p>{featured.dealMessage ?? 'Today’s find'}</p><h4>{featured.title}</h4></div><strong>−{featured.discountPercent}%</strong></div>
					<p>{featured.description}</p>
					<div className="shopDealActions"><span><del>{formatDabloons(featured.originalPriceDabloons)}</del> {formatDabloons(featured.discountedPriceDabloons)} dabloons</span><button type="button" onClick={() => onSelectItem(featured.id)}>See details</button></div>
				</div>
				<div className="shopDealPreview" aria-hidden="true"><ShopPreview item={featured} hovered={featuredHovered} hidden={shouldHidePreview(featured, settings.arachnophobiaMode)} allow3d={!settings.reduce3dRendering} /></div>
				{data.shoppingSunday && <span className="shoppingSundayBadge">Shopping Sunday · tons of huge discounts</span>}
				{dailyDeals.length > 1 && <button type="button" className="shopDealArrow next" aria-label="Next daily deal" onClick={() => setFeaturedIndex((current) => (current + 1) % dailyDeals.length)}>›</button>}
				{dailyDeals.length > 1 && <div className="shopDealDots" aria-label="Choose daily deal">{dailyDeals.map((item, index) => <button key={item.id} type="button" className={index === safeFeaturedIndex ? 'active' : ''} aria-label={`Show ${item.title}`} onClick={() => setFeaturedIndex(index)} />)}</div>}
			</section>}

			<div className="shopCatalogToolbar">
				<span>{visibleItems.length} {visibleItems.length === 1 ? 'item' : 'items'}</span>
				<label className="shopOrderControl"><span>Order</span><select value={order} onChange={(event) => {
					const nextOrder = event.target.value as ShopOrder
					setOrder(nextOrder)
					if (nextOrder === 'random') setRandomSeed(Math.random().toString(36))
				}}>{ORDER_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
			</div>

			<div className="shopFilterStack" aria-label="Shop filters">
				<FilterRow label="Type" options={TYPE_OPTIONS} selected={typeFilter} onSelect={(value) => setTypeFilter(value as 'all' | ShopItemType)} />
				<FilterRow label="Rarity" options={RARITY_OPTIONS.map((value) => ({ value, label: formatOption(value) }))} selected={rarityFilter} onSelect={(value) => setRarityFilter(value as (typeof RARITY_OPTIONS)[number])} />
				<FilterRow label="Tags" options={TAG_OPTIONS} selected={tagFilter} onSelect={(value) => setTagFilter(value as ShopTagFilter)} />
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
			<div className="shopGrid">
				{visibleItems.map((item) => <ShopCard key={item.id} item={item} hovered={hoveredItemId === item.id} hidePreview={shouldHidePreview(item, settings.arachnophobiaMode)} allow3d={!settings.reduce3dRendering} onHover={setHoveredItemId} onOpen={(selected) => onSelectItem(selected.id)} />)}
			</div>
			{visibleItems.length === 0 && <p className="shopEmptyState">No items match those filters.</p>}

			{selectedItem && <ShopDetails item={selectedItem} buying={buyingItemId === selectedItem.id} hidePreview={shouldHidePreview(selectedItem, settings.arachnophobiaMode)} onClose={() => onSelectItem(null, true)} onBuy={buy} />}
		</div>
	)
}

function FilterRow({ label, options, selected, onSelect }: { label: string; options: Array<{ value: string; label: string }>; selected: string; onSelect: (value: string) => void }) {
	return <div className="shopFilterRow"><span>{label}</span><div role="group" aria-label={`${label} filter`}>{options.map((option) => <button type="button" key={option.value} className={`${selected === option.value ? 'active' : ''} filter-${option.value}`} aria-pressed={selected === option.value} onClick={() => onSelect(option.value)}>{option.value === 'animated' ? <AnimatedLabel /> : option.label}</button>)}</div></div>
}

function ShopCard({ item, hovered, hidePreview, allow3d, onHover, onOpen }: { item: ShopItem; hovered: boolean; hidePreview: boolean; allow3d: boolean; onHover: (id: string | null) => void; onOpen: (item: ShopItem) => void }) {
	return <article className={`shopCard shopCard-${item.type} rarity-${item.rarity} ${!item.available ? 'unavailable' : ''}`} tabIndex={0} role="button" aria-label={`View ${item.title}`} onClick={() => onOpen(item)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onOpen(item) } }} onFocus={() => onHover(item.id)} onBlur={() => onHover(null)} onPointerEnter={() => onHover(item.id)} onPointerLeave={() => onHover(null)}>
		<div className="shopImageFrame" aria-hidden="true"><ShopPreview item={item} hovered={hovered} hidden={hidePreview} allow3d={allow3d} /><ShopMetaIcons item={item} />{item.isDailyDeal && <span className="shopDealBadge">−{item.discountPercent}% today</span>}</div>
		<div className="shopCardBody"><ItemBadges item={item} /><h4>{item.title}</h4><Price item={item} /></div>
		<button type="button" className="shopCardFoot" disabled={isSoldOut(item)} onClick={(event) => { event.stopPropagation(); onOpen(item) }}>{isSoldOut(item) ? 'Sold out' : 'Buy now'}</button>
	</article>
}

function ShopDetails({ item, buying, hidePreview, onClose, onBuy }: { item: ShopItem; buying: boolean; hidePreview: boolean; onClose: () => void; onBuy: (item: ShopItem) => Promise<void> }) {
	useEffect(() => {
		const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
		window.addEventListener('keydown', close)
		document.body.classList.add('shopModalOpen')
		return () => { window.removeEventListener('keydown', close); document.body.classList.remove('shopModalOpen') }
	}, [onClose])

	return createPortal(<div className="shopDetailsBackdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
		<section className={`shopDetails shopCard-${item.type} rarity-${item.rarity}`} role="dialog" aria-modal="true" aria-labelledby="shop-detail-title">
			<button type="button" className="shopDetailsClose" aria-label="Close details" onClick={onClose}>×</button>
			<div className="shopDetailsHero">
				<div className="shopDetailsPreview"><ShopPreview item={item} hovered={false} interactive hidden={hidePreview} />{item.renderMode === 'model' && !hidePreview && <span>Hover to pause · drag to rotate</span>}</div>
				<div className="shopDetailsSummary"><ItemBadges item={item} /><h2 id="shop-detail-title">{item.title}</h2>{item.description && <div className="shopItemEffect"><strong>Effect</strong><p>{item.description}</p></div>}{item.tooltips.length > 0 && <div className="shopItemTooltips">{item.tooltips.map((tooltip) => <p key={tooltip}>{tooltip}</p>)}</div>}<Price item={item} /><button type="button" className="shopDetailsBuy" disabled={!item.available || buying} onClick={() => void onBuy(item)}>{item.available ? buying ? 'Buying…' : `Buy for ${formatDabloons(effectivePrice(item))} dabloons` : 'Sold out'}</button></div>
			</div>
			{item.type === 'charm' && item.charmDetails && <CharmProgression details={item.charmDetails} />}
		</section>
	</div>, document.body)
}

function CharmProgression({ details }: { details: NonNullable<ShopItem['charmDetails']> }) {
	const receivedBroken = details.minLevel === 0
	return <section className="charmProgression"><div className="charmProgressionHeading"><div><p>Charm progression</p><h3>Levels & upgrade costs</h3></div><div className={`charmReceiveLevel ${receivedBroken ? 'broken' : ''}`}><span>You receive</span><strong>{receivedBroken ? 'Broken' : `Level ${details.minLevel}`}</strong><small>{receivedBroken ? `Repair it by upgrading to level 1 · Maximum level ${details.maxLevel}` : `Maximum level ${details.maxLevel}`}</small></div></div>
		<div className="charmTableWrap"><table><thead><tr><th>State</th><th>Effect</th><th>Cost to reach state</th></tr></thead><tbody>{receivedBroken && <tr className="received broken"><th>Broken</th><td>No effect — upgrade it to level 1 to repair and activate it.</td><td>Received in this state</td></tr>}{details.levels.map((level) => <tr key={level.level} className={level.level === details.minLevel ? 'received' : ''}><th>Lv. {level.level}</th><td>{level.abilityStatusCurrent || '—'}</td><td><IngredientList ingredients={level.upgradeIngredients} /></td></tr>)}</tbody></table></div>
	</section>
}

function ItemBadges({ item }: { item: ShopItem }) {
	const tags = [
		item.dyeable ? <span key="dyeable" className="shopTag dyeable">Dyeable</span> : null,
		item.animated ? <span key="animated" className="shopTag animated"><AnimatedLabel /></span> : null,
		item.isDailyDeal ? <span key="discounted" className="shopTag discounted">Discounted −{item.discountPercent}%</span> : null,
		isSoldOut(item) ? <span key="sold-out" className="shopTag soldOut">Sold out</span> : null,
	].filter(Boolean)
	return <div className="shopBadgeLines"><div className="shopBadges"><span>{formatOption(item.type)}</span><span>{formatOption(item.rarity)}</span></div>{tags.length > 0 && <div className="shopTagBadges">{tags}</div>}</div>
}

function AnimatedLabel() {
	return <span className="animatedWave" aria-label="Animated">{'Animated'.split('').map((letter, index) => <span key={`${letter}-${index}`} aria-hidden="true" style={{ '--wave-index': index } as CSSProperties}>{letter}</span>)}</span>
}

function IngredientList({ ingredients }: { ingredients: string[] }) {
	const counts = new Map<string, number>()
	for (const ingredient of ingredients) counts.set(ingredient, (counts.get(ingredient) ?? 0) + 1)
	if (!counts.size) return <>—</>
	return <div className="charmIngredients">{[...counts].map(([ingredient, count]) => <span key={ingredient}>{count > 1 && <strong>{count}×</strong>} {formatIngredient(ingredient)}</span>)}</div>
}

function Price({ item }: { item: ShopItem }) {
	return item.isDailyDeal ? <p className="shopPrice deal"><del>{formatDabloons(item.originalPriceDabloons)}</del><strong>{formatDabloons(item.discountedPriceDabloons)}</strong> dabloons</p> : <p className="shopPrice"><strong>{formatDabloons(item.priceDabloons)}</strong> dabloons</p>
}

function ShopPreview({ item, hovered, interactive = false, hidden = false, allow3d = true }: { item: ShopItem; hovered: boolean; interactive?: boolean; hidden?: boolean; allow3d?: boolean }) {
	if (hidden) return <div className="shopHiddenPreview" />
	if (!allow3d && item.renderMode === 'model') return <div className="shopReduced3dPlaceholder" />
	if (item.renderMode === 'model' && item.modelUrl && item.textureUrl) return <ShopModelPreview item={item} hovered={hovered} interactive={interactive} />
	if (item.iconUrl && item.animation) return <AnimatedTexturePreview url={item.iconUrl} animation={item.animation} />
	if (item.iconUrl) return <img src={item.iconUrl} alt="" className="shopItemIcon" />
	return <div className="shopItemEmpty" />
}

function AnimatedTexturePreview({ url, animation }: { url: string; animation: NonNullable<ShopItem['animation']> }) {
	const canvasRef = useRef<HTMLCanvasElement | null>(null)

	useEffect(() => {
		const canvas = canvasRef.current
		if (!canvas) return
		let timer: number | undefined
		let cancelled = false
		const image = new Image()
		image.onload = () => {
			if (cancelled) return
			const frameSize = image.naturalWidth
			const frameCount = Math.max(1, Math.floor(image.naturalHeight / frameSize))
			const frames = (animation.frames ?? Array.from({ length: frameCount }, (_, index) => index))
				.filter((frame) => frame < frameCount)
			const context = canvas.getContext('2d')
			if (!context || !frames.length) return
			canvas.width = frameSize
			canvas.height = frameSize
			let frameIndex = 0
			const draw = () => {
				context.clearRect(0, 0, frameSize, frameSize)
				context.drawImage(image, 0, frames[frameIndex]! * frameSize, frameSize, frameSize, 0, 0, frameSize, frameSize)
			}
			draw()
			if (frames.length > 1) timer = window.setInterval(() => {
				frameIndex = (frameIndex + 1) % frames.length
				draw()
			}, animation.frameDelayMs)
		}
		image.src = url
		return () => {
			cancelled = true
			window.clearInterval(timer)
		}
	}, [animation.frameDelayMs, animation.frames, url])

	return <canvas ref={canvasRef} className="shopItemIcon shopAnimatedTexture" />
}

function ShopModelPreview({ item, hovered, interactive }: { item: ShopItem; hovered: boolean; interactive: boolean }) {
	const hostRef = useRef<HTMLDivElement | null>(null)
	const canvasRef = useRef<HTMLCanvasElement | null>(null)
	const rendererRef = useRef<MinecraftModelRenderer | null>(null)
	const [interactiveHover, setInteractiveHover] = useState(false)
	const [ready, setReady] = useState(false)
	const [liveReady, setLiveReady] = useState(false)
	const [failed, setFailed] = useState(false)

	useEffect(() => {
		const host = hostRef.current
		const canvas = canvasRef.current
		if (interactive || item.animated || !host || !canvas || !item.modelUrl || !item.textureUrl) return
		let cancelled = false
		let renderer: MinecraftModelRenderer | null = null
		setFailed(false)
		const modelPromise = modelCache.get(item.modelUrl) ?? fetch(item.modelUrl).then((response) => { if (!response.ok) throw new Error('Model failed to load'); return response.json() })
		modelCache.set(item.modelUrl, modelPromise)
		void queueStaticRender(async () => {
			const model = await modelPromise
			if (cancelled || !host.isConnected) return
			renderer = new MinecraftModelRenderer(host, { assetRoot: ASSETS.minecraft.root, animateDye: false, autoRotate: false, animateTextures: false, dyeable: item.dyeable, frameDelayMs: item.animation?.frameDelayMs, frameSequence: item.animation?.frames ?? null, textureSource: item.textureUrl!, view: item.type === 'cosmetic' ? 'cosmetic' : 'basic3d' })
			await renderer.loadModel(model as never)
			const savedState = modelPreviewStateCache.get(item.id)
			if (savedState) renderer.setPreviewState(savedState)
			if (!cancelled && renderer.copyFrameTo(canvas)) {
				modelPreviewStateCache.set(item.id, renderer.getPreviewState())
				setReady(true)
			}
			renderer.destroy()
			renderer = null
		}).catch(() => { if (!cancelled) setFailed(true) })
		return () => { cancelled = true; renderer?.destroy(); renderer = null }
	}, [interactive, item.animated, item.animation?.frameDelayMs, item.animation?.frames, item.dyeable, item.id, item.modelUrl, item.textureUrl, item.type])

	const shouldAutoRotate = interactive ? !interactiveHover : hovered
	const shouldAnimateDye = interactive ? interactiveHover : hovered
	const rendererActive = interactive || hovered || item.animated
	const autoRotateRef = useRef(shouldAutoRotate)
	const animateDyeRef = useRef(shouldAnimateDye)
	useEffect(() => { autoRotateRef.current = shouldAutoRotate; rendererRef.current?.setAutoRotate(shouldAutoRotate) }, [shouldAutoRotate])
	useEffect(() => { animateDyeRef.current = shouldAnimateDye; rendererRef.current?.setDyeAnimation(shouldAnimateDye) }, [shouldAnimateDye])

	useEffect(() => {
		const host = hostRef.current
		const canvas = canvasRef.current
		if (!rendererActive || !host || !item.modelUrl || !item.textureUrl) {
			setLiveReady(false)
			return
		}
		let cancelled = false
		let renderer: MinecraftModelRenderer | null = null
		setLiveReady(false)
		setFailed(false)
		const modelPromise = modelCache.get(item.modelUrl) ?? fetch(item.modelUrl).then((response) => { if (!response.ok) throw new Error('Model failed to load'); return response.json() })
		modelCache.set(item.modelUrl, modelPromise)
		void modelPromise.then(async (model) => {
			if (cancelled || !host.isConnected) return
			renderer = new MinecraftModelRenderer(host, { assetRoot: ASSETS.minecraft.root, animateDye: animateDyeRef.current, autoRotate: autoRotateRef.current, dyeable: item.dyeable, enableDrag: interactive, frameDelayMs: item.animation?.frameDelayMs, frameSequence: item.animation?.frames ?? null, textureSource: item.textureUrl!, view: item.type === 'cosmetic' ? 'cosmetic' : 'basic3d' })
			rendererRef.current = renderer
			await renderer.loadModel(model as never)
			const savedState = modelPreviewStateCache.get(item.id)
			if (savedState) renderer.setPreviewState(savedState)
			if (!cancelled) {
				renderer.setAutoRotate(autoRotateRef.current)
				renderer.setDyeAnimation(animateDyeRef.current)
				setReady(true)
				setLiveReady(true)
			}
		}).catch(() => { if (!cancelled) setFailed(true) })
		return () => {
			cancelled = true
			if (renderer) modelPreviewStateCache.set(item.id, renderer.getPreviewState())
			if (!interactive && !item.animated && canvas) renderer?.copyFrameTo(canvas)
			renderer?.destroy()
			if (rendererRef.current === renderer) rendererRef.current = null
			renderer = null
		}
	}, [interactive, item.animated, item.animation?.frameDelayMs, item.animation?.frames, item.dyeable, item.id, item.modelUrl, item.textureUrl, item.type, rendererActive])

	if (failed && item.iconUrl) return <img src={item.iconUrl} alt="" className="shopItemIcon" />
	return <div ref={hostRef} className={`shopModelHost ${ready ? 'ready' : 'loading'} ${liveReady ? 'live' : ''} ${interactive ? 'interactive' : ''}`} onPointerEnter={() => interactive && setInteractiveHover(true)} onPointerLeave={() => interactive && setInteractiveHover(false)}><canvas ref={canvasRef} className="shopModelSnapshot" /></div>
}

function ShopMetaIcons({ item }: { item: ShopItem }) {
	if (!item.animated && !item.dyeable) return null
	return <div className="shopMetaIcons">{item.animated && <span className="shopMetaIcon shopMetaIcon-animated" title="Animated texture" />}{item.dyeable && <span className="shopMetaIcon shopMetaIcon-dyeable" title="Dyeable" />}</div>
}

function effectivePrice(item: ShopItem) { return item.isDailyDeal ? item.discountedPriceDabloons : item.priceDabloons }
function formatDabloons(value: number) { return value.toLocaleString('en-US') }
function shouldHidePreview(item: ShopItem, arachnophobiaMode: boolean) { return arachnophobiaMode && /(spider|arach)/i.test(item.title) }
function isSoldOut(item: ShopItem) { return item.type === 'generic' && !item.available }
function compareTitles(left: ShopItem, right: ShopItem) { return left.title.localeCompare(right.title, 'en') }
function rarityRank(item: ShopItem) { return RARITY_RANK.get(item.rarity as (typeof RARITY_OPTIONS)[number]) ?? 0 }
function formatOption(value: string) { return value.split(/[-_ ]+/).filter(Boolean).map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ') }
function formatIngredient(value: string) {
	const customLabel = value.includes('=') ? value.slice(value.lastIndexOf('=') + 1) : ''
	if (customLabel) return customLabel
	const id = value.split('[', 1)[0]!.replace(/^minecraft:/, '').replace(/^.*:/, '')
	return formatOption(id)
}
function seededRank(value: string) { let hash = 2166136261; for (let index = 0; index < value.length; index++) { hash ^= value.charCodeAt(index); hash = Math.imul(hash, 16777619) } return hash >>> 0 }
