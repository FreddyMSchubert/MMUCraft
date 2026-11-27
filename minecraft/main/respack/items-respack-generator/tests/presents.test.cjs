/* global __dirname */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
	createRepeatingCommandBlockItemDefinition,
} = require('../dist/generation/selectorDefinitions');

test('each wrapping paper has one present and three models', () => {
	const items = path.resolve(__dirname, '../../../data/data/items');
	const colors = [
		'white',
		'orange',
		'magenta',
		'light-blue',
		'yellow',
		'lime',
		'pink',
		'gray',
		'light-gray',
		'cyan',
		'purple',
		'blue',
		'brown',
		'green',
		'red',
		'black',
	];
	const cases = [];
	for (const color of colors) {
		const paper = JSON.parse(
			fs.readFileSync(path.join(items, 'basic/wrapping-paper', color, 'item.json')),
		);
		const closed = JSON.parse(
			fs.readFileSync(path.join(items, 'decoblocks/_presents/_closed', color, 'item.json')),
		);
		assert.equal(paper.id, `wrapping-paper-${color}`);
		assert.deepEqual(paper.craftable.recipes[0].ingredients, [
			'minecraft:paper',
			`minecraft:${color.replace('-', '_')}_dye`,
		]);
		assert.equal(paper.craftable.recipes[0].result.stack, `mainmod:wrapping-paper-${color}`);
		assert.equal(closed.id, `present-${color}`);
		assert.equal(closed.baseItemOverride, 'minecraft:repeating_command_block');
		for (const state of ['_closed', '_opening', '_open']) {
			const directory = path.join(items, 'decoblocks/_presents', state, color);
			assert.ok(fs.existsSync(path.join(directory, 'model.json')));
			assert.ok(fs.existsSync(path.join(directory, 'model.png')));
			if (state !== '_closed')
				assert.equal(fs.existsSync(path.join(directory, 'item.json')), false);
		}
		cases.push({
			when: closed.id,
			modelId: `mmu_pack:item/decoblocks/_presents/_closed/${color}`,
		});
	}
	const definition = createRepeatingCommandBlockItemDefinition(cases);
	assert.equal(definition.model.cases.length, 16);
	for (const item of definition.model.cases) {
		assert.deepEqual(
			item.model.entries.map((entry) => entry.threshold),
			[1, 2],
		);
		assert.match(item.model.fallback.model, /\/_closed\//);
		assert.match(item.model.entries[0].model.model, /\/_opening\//);
		assert.match(item.model.entries[1].model.model, /\/_open\//);
	}
});
