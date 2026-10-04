const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { discoverItems } = require('../dist/discovery/discoverItems');
const { generateResourcePack } = require('../dist/generation/resourcePackGenerator');
const generatorDir = path.dirname(require.resolve('../package.json'));

test('authored minified textures are copied unchanged when the full set is present', async (t) => {
	const tempDir = await fs.realpath(os.tmpdir());
	const root = await fs.realpath(await fs.mkdtemp(path.join(tempDir, 'armor-appearance-')));
	assert.equal(path.dirname(root).toLowerCase(), tempDir.toLowerCase());
	t.after(() => fs.rm(root, { recursive: true, force: true }));
	const sourceDir = path.resolve(generatorDir, '../../data/data/items/charm/equipped');
	const items = await discoverItems(sourceDir);
	const vanillaArmorAssetsDir = path.join(root, 'vanilla_armor_assets');
	const outputDir = path.join(root, 'pack');
	const options = {
		sourceDir,
		vanillaArmorAssetsDir,
		outputDir,
		namespace: 'mmu_pack',
		packDescription: 'test',
	};
	const charm = items.find((item) => item.type === 'charm' && item.equipmentSlot === 'chest');
	assert.ok(charm);
	const normalAsset = charm.equippableAssetId.replace(/__charm$/, '__diamond');
	let summary = await generateResourcePack(items, options);
	assert.equal(summary.skippedItems, 0);
	await fs.access(path.join(outputDir, 'assets/mmu_pack/equipment', `${normalAsset}.json`));
	await assert.rejects(
		fs.access(path.join(outputDir, 'assets/mmu_pack/equipment/armor_minified__diamond.json')),
	);

	const authored = path.join(root, 'minified_armor_assets');
	const materials = [
		'chainmail',
		'leather',
		'iron',
		'gold',
		'diamond',
		'netherite',
		'enderite',
		'copper',
	];
	for (const layer of ['humanoid', 'humanoid_leggings']) {
		await fs.mkdir(path.join(authored, layer), { recursive: true });
		for (const name of [...materials, 'leather_overlay']) {
			const source =
				name === 'enderite'
					? path.resolve(
							generatorDir,
							`../packs/general-pack/assets/mmu_pack/textures/entity/equipment/${layer}/enderite.png`,
						)
					: path.resolve(generatorDir, `vanilla_armor_assets/${layer}/${name}.png`);
			await fs.copyFile(source, path.join(authored, layer, `${name}.png`));
		}
	}
	summary = await generateResourcePack(items, options);
	assert.equal(summary.skippedItems, 0);
	const equipment = path.join(outputDir, 'assets/mmu_pack/equipment');
	const textures = path.join(outputDir, 'assets/mmu_pack/textures/entity/equipment');
	for (const material of materials) {
		const bare = JSON.parse(
			await fs.readFile(path.join(equipment, `armor_minified__${material}.json`)),
		);
		for (const layer of ['humanoid', 'humanoid_leggings']) {
			assert.equal(bare.layers[layer][0].texture, `mmu_pack:armor_minified/${material}`);
			assert.deepEqual(
				await fs.readFile(path.join(textures, layer, `armor_minified/${material}.png`)),
				await fs.readFile(path.join(authored, layer, `${material}.png`)),
			);
		}
		const asset = charm.equippableAssetId.replace(/__charm$/, `__${material}__minified`);
		const charmed = JSON.parse(await fs.readFile(path.join(equipment, `${asset}.json`)));
		assert.equal(charmed.layers.humanoid[0].texture, `mmu_pack:armor_minified/${material}`);
		assert.equal(charmed.layers.humanoid.at(-1).texture, `mmu_pack:${charm.equippableAssetId}`);
	}
	const leather = JSON.parse(
		await fs.readFile(path.join(equipment, 'armor_minified__leather.json')),
	);
	assert.ok(leather.layers.humanoid[0].dyeable);
	assert.equal(leather.layers.humanoid[1].texture, 'mmu_pack:armor_minified/leather_overlay');
});
