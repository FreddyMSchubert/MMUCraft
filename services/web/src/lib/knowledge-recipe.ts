export const RECIPE_POSITIONS = ['TL', 'TM', 'TR', 'ML', 'M', 'MR', 'BL', 'BM', 'BR'] as const

export type RecipePosition = typeof RECIPE_POSITIONS[number]

export interface RecipeAssetFrame {
	src: string
	title?: string
}

export interface RecipeItem {
	pos?: RecipePosition
	name: string
	tooltip?: string
	asset: string | RecipeAssetFrame[]
	wikiUrl?: string
	knowledgeUrl?: string
	count?: number
}

export interface KnowledgeRecipe {
	type: 'shaped' | 'shapeless'
	inputs: RecipeItem[]
	output: RecipeItem
}

const POSITION_SET = new Set<string>(RECIPE_POSITIONS)

export function parseKnowledgeRecipe(source: string): KnowledgeRecipe {
	const value: unknown = JSON.parse(source)
	if (!isRecord(value) || (value.type !== 'shaped' && value.type !== 'shapeless')) {
		throw new Error('Recipe type must be "shaped" or "shapeless".')
	}
	if (!Array.isArray(value.inputs) || !isRecord(value.output)) {
		throw new Error('Recipe inputs and output are required.')
	}
	if (value.inputs.length < 1 || value.inputs.length > 9) throw new Error('A recipe needs 1 to 9 inputs.')

	const inputs = value.inputs.map((item, index) => parseItem(item, `Input ${index + 1}`, true))
	const occupied = new Set(inputs.map((item) => item.pos))
	if (occupied.size !== inputs.length) throw new Error('Each recipe position can be used only once.')

	return {
		type: value.type,
		inputs,
		output: parseItem(value.output, 'Output', false),
	}
}

function parseItem(value: unknown, label: string, needsPosition: boolean): RecipeItem {
	if (!isRecord(value) || typeof value.name !== 'string' || !value.name.trim()) {
		throw new Error(`${label} needs a name.`)
	}
	if (needsPosition && (typeof value.pos !== 'string' || !POSITION_SET.has(value.pos))) {
		throw new Error(`${label} needs a valid grid position.`)
	}
	if (needsPosition && value.count !== undefined) throw new Error(`${label} cannot have a count.`)
	if (value.wikiUrl && value.knowledgeUrl) throw new Error(`${label} cannot have both link types.`)
	if (value.wikiUrl !== undefined && (typeof value.wikiUrl !== 'string' || !/^https?:\/\//.test(value.wikiUrl))) {
		throw new Error(`${label} has an invalid wiki URL.`)
	}
	if (value.knowledgeUrl !== undefined && (typeof value.knowledgeUrl !== 'string' || !/^\/play\/knowledge(?:\/|$)/.test(value.knowledgeUrl))) {
		throw new Error(`${label} has an invalid knowledge URL.`)
	}
	if (value.count !== undefined && (!Number.isInteger(value.count) || Number(value.count) < 1)) {
		throw new Error(`${label} count must be a positive integer.`)
	}

	return {
		...(needsPosition ? { pos: value.pos as RecipePosition } : {}),
		name: value.name.trim(),
		...(typeof value.tooltip === 'string' && value.tooltip ? { tooltip: value.tooltip } : {}),
		asset: parseAsset(value.asset, label),
		...(typeof value.wikiUrl === 'string' && value.wikiUrl ? { wikiUrl: value.wikiUrl } : {}),
		...(typeof value.knowledgeUrl === 'string' && value.knowledgeUrl ? { knowledgeUrl: value.knowledgeUrl } : {}),
		...(value.count !== undefined ? { count: Number(value.count) } : {}),
	}
}

function parseAsset(value: unknown, label: string): RecipeItem['asset'] {
	if (typeof value === 'string' && value.trim()) return value.trim()
	if (Array.isArray(value) && value.length > 0) {
		return value.map((frame) => {
			if (!isRecord(frame) || typeof frame.src !== 'string' || !frame.src.trim()) {
				throw new Error(`${label} has an invalid asset frame.`)
			}
			return {
				src: frame.src.trim(),
				...(typeof frame.title === 'string' && frame.title ? { title: frame.title } : {}),
			}
		})
	}
	throw new Error(`${label} needs an asset.`)
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value)
}
