import assert from 'node:assert/strict'
import test from 'node:test'
import { parseKnowledgeRecipe } from './knowledge-recipe.ts'

test('parses valid recipe markers and rejects conflicting links', () => {
	const recipe = parseKnowledgeRecipe(JSON.stringify({
		type: 'shaped',
		inputs: [{ pos: 'M', name: 'Chest', asset: 'minecraft:chest' }],
		output: { name: 'Backpack', asset: [{ src: '/backpack.png', title: 'Leather Backpack' }], count: 2 },
	}))
	assert.equal(recipe.inputs[0]?.pos, 'M')
	assert.equal(recipe.output.count, 2)
	assert.throws(() => parseKnowledgeRecipe(JSON.stringify({
		type: 'shapeless', inputs: [{ pos: 'M', name: 'Stone', asset: 'minecraft:stone' }], output: {
			name: 'Invalid', asset: 'minecraft:barrier', wikiUrl: 'https://example.com', knowledgeUrl: '/play/knowledge/example',
		},
	})), /both link types/)
})
