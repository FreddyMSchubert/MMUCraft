'use client'

import { useEffect, useState } from 'react'
import { MinecraftItemIcon } from '@/components/minecraft-item-icon'
import { RECIPE_POSITIONS, type KnowledgeRecipe, type RecipeAssetFrame, type RecipeItem } from '@/lib/knowledge-recipe'

export function KnowledgeRecipeWidget({ recipe }: { recipe: KnowledgeRecipe }) {
	const items = new Map(recipe.inputs.map((item) => [item.pos, item]))
	return (
		<figure className="knowledgeRecipe" aria-label={`${recipe.type} crafting recipe for ${recipe.output.name}`}>
			<div className="knowledgeRecipeGrid">
				{RECIPE_POSITIONS.map((position) => (
					<div className="knowledgeRecipeSlot" key={position}>
						{items.get(position) && <RecipeItemView item={items.get(position)!} />}
					</div>
				))}
			</div>
			<div className="knowledgeRecipeArrow" aria-hidden="true" />
			<div className="knowledgeRecipeSlot knowledgeRecipeOutput">
				<RecipeItemView item={recipe.output} />
			</div>
			<figcaption>{recipe.type}</figcaption>
		</figure>
	)
}

function RecipeItemView({ item }: { item: RecipeItem }) {
	const frames: RecipeAssetFrame[] = typeof item.asset === 'string' ? [{ src: item.asset }] : item.asset
	const [frameIndex, setFrameIndex] = useState(0)

	useEffect(() => {
		if (frames.length < 2) return
		const interval = window.setInterval(() => setFrameIndex((current) => (current + 1) % frames.length), 1000)
		return () => window.clearInterval(interval)
	}, [frames.length])

	const frame = frames[frameIndex] ?? frames[0]!
	const name = frame.title ?? item.name
	const href = item.knowledgeUrl || item.wikiUrl
	const content = <>
		<RecipeAsset src={frame.src} />
		{item.count && <span className="knowledgeRecipeCount">{item.count}</span>}
		<span className="knowledgeRecipeTooltip" role="tooltip">
			<strong>{name}</strong>
			{item.tooltip && <span>{item.tooltip}</span>}
			{href && <em>Click to learn more</em>}
		</span>
	</>

	return href ? (
		<a className="knowledgeRecipeItem" href={href} target={item.wikiUrl ? '_blank' : undefined} rel={item.wikiUrl ? 'noopener noreferrer' : undefined} aria-label={name}>
			{content}
		</a>
	) : <span className="knowledgeRecipeItem" tabIndex={0} aria-label={name}>{content}</span>
}

function RecipeAsset({ src }: { src: string }) {
	if (!src.includes(':')) return <img src={src} alt="" />
	if (src.startsWith('minecraft:')) return <MinecraftItemIcon className="knowledgeRecipeIcon" itemId={src} />
	if (src.startsWith('mainmod:')) {
		const id = src.slice('mainmod:'.length)
		return <MinecraftItemIcon
			className="knowledgeRecipeIcon"
			itemId={src}
			modelUrl={`/api/shop/model/${encodeURIComponent(id)}`}
			textureUrl={`/api/shop/texture/${encodeURIComponent(id)}`}
		/>
	}
	return null
}
