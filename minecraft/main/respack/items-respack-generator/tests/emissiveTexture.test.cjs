const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const sharp = require('sharp');
const { generateEmissiveTexture } = require('../dist/generation/emissiveTexture');
const { generateResourcePack } = require('../dist/generation/resourcePackGenerator');

async function fixture(t, height = 16) {
	const root = await fs.mkdtemp(path.join(os.tmpdir(), 'emissive-test-'));
	t.after(() => fs.rm(root, { recursive: true, force: true }));
	const source = path.join(root, 'source.png');
	const destination = path.join(root, 'result.png');
	await sharp({
		create: {
			width: 16,
			height,
			channels: 4,
			background: { r: 200, g: 100, b: 50, alpha: 0.5 },
		},
	})
		.png()
		.toFile(source);
	return { source, destination, output: path.join(root, 'result_e.png'), root };
}

test('UV mask respects light emission, reversed and rotated UVs, alpha, and never modifies inputs', async (t) => {
	const { source, destination, output } = await fixture(t);
	const before = await fs.readFile(source);
	const model = {
		elements: [
			{
				light_emission: 15,
				faces: { north: { texture: '#0', uv: [8, 8, 0, 0], rotation: 90 } },
			},
			{ faces: { south: { texture: '#0', uv: [0, 0, 16, 16] } } },
		],
	};
	const modelBefore = JSON.parse(JSON.stringify(model));
	assert.equal(await generateEmissiveTexture(source, destination, model), 1);
	const pixels = await sharp(output).raw().toBuffer();
	for (let y = 0; y < 16; y++)
		for (let x = 0; x < 16; x++)
			assert.equal(pixels[(y * 16 + x) * 4 + 3], x < 8 && y < 8 ? 128 : 0);
	assert.deepEqual(await fs.readFile(source), before);
	assert.deepEqual(model, modelBefore);
});

test('implicit UVs repeat in every animation frame and preserve metadata', async (t) => {
	const { source, destination, output } = await fixture(t, 32);
	const metadata = JSON.stringify({
		animation: { frametime: 3, frames: [1, 0], interpolate: true },
	});
	await fs.writeFile(`${source}.mcmeta`, metadata);
	assert.equal(
		await generateEmissiveTexture(source, destination, {
			elements: [
				{
					light_emission: 15,
					from: [0, 0, 0],
					to: [8, 16, 8],
					faces: { up: { texture: '#alias' } },
				},
			],
		}),
		2,
	);
	const pixels = await sharp(output).raw().toBuffer();
	for (let y = 0; y < 32; y++)
		for (let x = 0; x < 16; x++)
			assert.equal(pixels[(y * 16 + x) * 4 + 3], x < 8 && y % 16 < 8 ? 128 : 0);
	assert.equal(await fs.readFile(`${output}.mcmeta`, 'utf8'), metadata);
});

test('authored overlays and their metadata win without inspecting model UVs', async (t) => {
	const { source, destination, output, root } = await fixture(t);
	const authored = path.join(root, 'source_e.png');
	await fs.copyFile(source, authored);
	await fs.writeFile(`${authored}.mcmeta`, '{"animation":{"frametime":7}}');
	assert.equal(
		await generateEmissiveTexture(source, destination, { elements: 'intentionally invalid' }),
		2,
	);
	assert.deepEqual(await fs.readFile(output), await fs.readFile(authored));
	assert.deepEqual(
		await fs.readFile(`${output}.mcmeta`),
		await fs.readFile(`${authored}.mcmeta`),
	);
});

test('ordinary models produce no overlay and source/output overlap is rejected before deletion', async (t) => {
	const { source, destination, output, root } = await fixture(t);
	assert.equal(
		await generateEmissiveTexture(source, destination, { elements: [{ shade: false }] }),
		0,
	);
	await assert.rejects(fs.access(output));
	for (const outputDir of [root, path.join(root, 'output'), path.dirname(root)])
		await assert.rejects(
			generateResourcePack([], { sourceDir: root, outputDir }),
			/separate from source/,
		);
	await fs.access(source);
});

test('intermediate emission scales RGB and overlapping regions retain the highest level', async (t) => {
	const { source, destination, output } = await fixture(t);
	await generateEmissiveTexture(source, destination, {
		elements: [
			{ light_emission: 8, faces: { north: { texture: '#0', uv: [0, 0, 16, 16] } } },
			{ light_emission: 15, faces: { south: { texture: '#0', uv: [8, 0, 16, 16] } } },
		],
	});
	const pixels = await sharp(output).raw().toBuffer();
	assert.ok(pixels[0] > 0 && pixels[0] < pixels[8 * 4]);
	assert.equal(pixels[8 * 4], 200);
	assert.equal(pixels[3], 128);
});
