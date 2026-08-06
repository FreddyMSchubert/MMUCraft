'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { CharmForgeRenderer } from '@/lib/charm-forge-renderer'

interface CharmIngredient {
	raw: string
	displayName: string
	requiredCount: number
	inventoryCount: number
	iconUrl: string | null
}

interface HeldCharm {
	itemId: string
	title: string
	currentLevel: number
	maxLevel: number
	targetLevel: number
	priceDabloons: number
	currentAbility: string
	nextAbility: string
	textureUrl: string | null
	ingredients: CharmIngredient[]
}

interface CharmInventory {
	online: boolean
	balanceDabloons: number
	message: string
	charms: HeldCharm[]
}

async function fetchCharmInventory() {
	const response = await fetch('/api/shop/charms', { cache: 'no-store' })
	const data = await response.json().catch(() => null) as CharmInventory | { message?: string } | null
	if (!response.ok) throw new Error(data?.message || 'Could not open the charm forge.')
	return data as CharmInventory
}

export function CharmsTab() {
	const [inventory, setInventory] = useState<CharmInventory | null>(null)
	const [loading, setLoading] = useState(true)
	const [enchanting, setEnchanting] = useState(false)
	const [message, setMessage] = useState('')
	const forgeHost = useRef<HTMLDivElement>(null)
	const forge = useRef<CharmForgeRenderer | null>(null)
	const charm = inventory?.charms[0] ?? null

	const refresh = useCallback(async () => {
		try {
			setInventory(await fetchCharmInventory())
		} catch (error) {
			setInventory(null)
			setMessage(error instanceof Error ? error.message : 'Could not open the charm forge.')
		} finally {
			setLoading(false)
		}
	}, [])

	useEffect(() => {
		let cancelled = false
		void fetchCharmInventory()
			.then((data) => {
				if (!cancelled) setInventory(data)
			})
			.catch((error: unknown) => {
				if (!cancelled) setMessage(error instanceof Error ? error.message : 'Could not open the charm forge.')
			})
			.finally(() => {
				if (!cancelled) setLoading(false)
			})
		return () => { cancelled = true }
	}, [])

	useEffect(() => {
		forge.current?.destroy()
		forge.current = null
		if (!forgeHost.current || !charm?.textureUrl) return

		const renderer = new CharmForgeRenderer(forgeHost.current, {
			charmTexture: charm.textureUrl,
			ingredientTextures: charm.ingredients.map((ingredient) => ingredient.iconUrl),
		})
		forge.current = renderer
		return () => {
			renderer.destroy()
			if (forge.current === renderer) forge.current = null
		}
	}, [charm])

	async function enchant() {
		if (!charm || enchanting || charm.currentLevel >= charm.maxLevel) return
		setEnchanting(true)
		setMessage('The forge is reading your main hand...')

		try {
			const response = await fetch('/api/shop/charms/upgrade', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ itemId: charm.itemId, expectedLevel: charm.currentLevel }),
			})
			const result = await response.json().catch(() => null) as { message?: string } | null
			if (!response.ok) throw new Error(result?.message || 'The enchantment failed.')

			setMessage('Enchantment accepted. Stand back!')
			await forge.current?.playEnchantment()
			await refresh()
			setMessage(result?.message || 'The charm grew stronger.')
		} catch (error) {
			setMessage(error instanceof Error ? error.message : 'The enchantment failed.')
		} finally {
			setEnchanting(false)
		}
	}

	return (
		<div className="charmForge">
			<header className="charmForgeHeader">
				<div>
					<span className="charmEyebrow">Arcane workbench</span>
					<h3>Charm Forge</h3>
					<p>Hold one charm in your main hand, then refresh this page.</p>
				</div>
				<div className="charmForgeControls">
					<span className="charmBalance" title="Current dabloon balance">
						<i aria-hidden="true">D</i>
						{inventory?.balanceDabloons ?? '—'} dabloons
					</span>
					<button type="button" className="charmRefresh" onClick={() => {
						setLoading(true)
						setMessage('')
						void refresh()
					}} disabled={loading || enchanting}>
						<span aria-hidden="true">↻</span> Refresh main hand
					</button>
				</div>
			</header>

			{loading && (
				<div className="charmForgeEmpty loading" role="status">
					<div className="charmRune" aria-hidden="true">✦</div>
					<strong>Reading the forge...</strong>
				</div>
			)}

			{!loading && !charm && (
				<div className="charmForgeEmpty">
					<div className="charmRune" aria-hidden="true">✧</div>
					<strong>No charm found in your main hand</strong>
					<p>{inventory?.message || message || 'Equip a charm in your hotbar, select it, and press Refresh main hand.'}</p>
				</div>
			)}

			{!loading && charm && (
				<>
					<section className={`charmForgeStage ${enchanting ? 'enchanting' : ''}`} aria-label={`${charm.title} enchantment preview`}>
						<div className="charmForgeAura" aria-hidden="true" />
						<div ref={forgeHost} className="charmForgeScene" />
						<div className="charmIdentity">
							<h4>{charm.title}</h4>
							<strong>Level {charm.currentLevel}</strong>
							{charm.currentLevel < charm.maxLevel && <span>→ Level {charm.targetLevel}</span>}
						</div>
					</section>

					<section className="charmAbility">
						<div>
							<span>Current magic</span>
							<p>{charm.currentAbility}</p>
						</div>
						{charm.currentLevel < charm.maxLevel && (
							<div>
								<span>Next level</span>
								<p>{charm.nextAbility}</p>
							</div>
						)}
					</section>

					{charm.currentLevel < charm.maxLevel ? (
						<section className="charmIngredientSection">
							<div className="charmSectionHeading">
								<div>
									<span>Reagents</span>
									<h4>Required ingredients</h4>
								</div>
								<small>Counts update when you refresh or enchant.</small>
							</div>
							<ul className="charmIngredientGrid">
								{charm.ingredients.map((ingredient) => (
									<li key={ingredient.raw}>
										<div className="charmIngredientIcon">
											{ingredient.iconUrl
												? <Image src={ingredient.iconUrl} alt="" width={42} height={42} unoptimized />
												: <span aria-hidden="true">?</span>}
										</div>
										<strong>{ingredient.displayName}</strong>
										<span className={ingredient.inventoryCount < ingredient.requiredCount ? 'short' : ''}>
											{ingredient.inventoryCount} / {ingredient.requiredCount}
										</span>
									</li>
								))}
							</ul>
						</section>
					) : (
						<div className="charmMastered"><span aria-hidden="true">✦</span> Maximum enchantment reached</div>
					)}

					<div className="charmEnchantBar">
						<div className="charmForgeMessage" role="status">{message || 'The server will verify your held charm, reagents, and balance.'}</div>
						<button
							type="button"
							className="charmEnchantButton"
							onClick={() => void enchant()}
							disabled={enchanting || charm.currentLevel >= charm.maxLevel}
						>
							<span>{enchanting ? 'Enchanting...' : charm.currentLevel >= charm.maxLevel ? 'Charm mastered' : 'Enchant charm'}</span>
							{charm.currentLevel < charm.maxLevel && (
								<strong>{charm.priceDabloons === 0 ? 'Free' : `${charm.priceDabloons} D`}</strong>
							)}
						</button>
					</div>
				</>
			)}

			{!charm && message && <p className="charmForgeMessage" role="alert">{message}</p>}
		</div>
	)
}
