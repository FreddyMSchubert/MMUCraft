import path from 'path';
import { existsSync } from 'node:fs';
import {
	CHARM_ARMOR_MATERIALS,
	FISHING_SHADOW_MODEL_OVERRIDES,
	FISHING_SHADOW_TEXTURE_OVERRIDES,
	FISHING_SHADOW_VANILLA_ITEMS,
	LEATHER_UNDYED_COLOR,
	PACK_MCMETA,
} from '../config';
import type {
	ArmorMaterial,
	Basic3dItemDefinition,
	BasicItemDefinition,
	CharmItemDefinition,
	CosmeticItemDefinition,
	CosmeticWeightEntry,
	DiscoveredItem,
	EquipmentLayerType,
	GenerationSummary,
	GeneratorOptions,
	SelectorCase,
} from '../types';
import {
	copyFileWithDirectory,
	readJsonFile,
	resetDirectory,
	writeJsonFile,
	writeTextFile,
} from '../utils/fs';
import { generateEmissiveTexture } from './emissiveTexture';
import { replaceTrailingVariant } from '../utils/paths';
import {
	createCarvedPumpkinItemDefinition,
	createCommandBlockItemDefinition,
	createHeartOfTheSeaItemDefinition,
} from './selectorDefinitions';
import { buildGeneratedSingleTextureModel } from './singleTextureModel';

interface GenerationContext {
	readonly options: GeneratorOptions;
	minifiedArmorAssetsAvailable: boolean;
	generatedFiles: number;
}

function itemModelId(namespace: string, resourcePath: string): string {
	return `${namespace}:item/${resourcePath}`;
}

function itemModelJsonPath(outputDir: string, namespace: string, resourcePath: string): string {
	return path.join(outputDir, 'assets', namespace, 'models', 'item', `${resourcePath}.json`);
}

function itemTexturePngPath(outputDir: string, namespace: string, resourcePath: string): string {
	return path.join(outputDir, 'assets', namespace, 'textures', 'item', `${resourcePath}.png`);
}

function itemTextureMcmetaPath(outputDir: string, namespace: string, resourcePath: string): string {
	return `${itemTexturePngPath(outputDir, namespace, resourcePath)}.mcmeta`;
}

function equipmentJsonPath(outputDir: string, namespace: string, assetId: string): string {
	return path.join(outputDir, 'assets', namespace, 'equipment', `${assetId}.json`);
}

function equipmentTexturePngPath(
	outputDir: string,
	namespace: string,
	layerType: EquipmentLayerType,
	textureId: string,
): string {
	return path.join(
		outputDir,
		'assets',
		namespace,
		'textures',
		'entity',
		'equipment',
		layerType,
		`${textureId}.png`,
	);
}

function minecraftItemDefinitionPath(outputDir: string, itemId: string): string {
	return path.join(outputDir, 'assets', 'minecraft', 'items', `${itemId}.json`);
}

function fishingShadowItemDefinitionPath(outputDir: string, itemId: string): string {
	return path.join(outputDir, 'assets', 'mainmod', 'items', 'fishing_shadow', `${itemId}.json`);
}

function createFishingShadowItemDefinition(itemId: string): Record<string, unknown> {
	const modelId = FISHING_SHADOW_TEXTURE_OVERRIDES[itemId]
		? `mainmod:item/fishing_shadow/${itemId}`
		: (FISHING_SHADOW_MODEL_OVERRIDES[itemId] ?? `minecraft:item/${itemId}`);
	return {
		model: {
			type: 'minecraft:model',
			model: modelId,
			tints: Array.from({ length: 5 }, () => ({
				type: 'minecraft:constant',
				value: 0,
			})),
		},
	};
}

function errorMessage(error: unknown): string {
	return error instanceof Error ? error.message : String(error);
}

function logRecoverableGenerationSkip(item: DiscoveredItem, error: unknown): void {
	console.error('');
	console.error('!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	console.error('SKIPPING ITEM DURING RESOURCE PACK GENERATION');
	console.error(`Item: ${item.relativeDirectory}`);
	console.error(`Type: ${item.type}`);
	console.error(errorMessage(error));
	console.error('!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	console.error('');
}

function cosmeticWeightValue(item: CosmeticWeightEntry): number {
	return item.unlockWeight ?? Number.NEGATIVE_INFINITY;
}

function buildCosmeticWeightOrder(items: readonly DiscoveredItem[]): CosmeticWeightEntry[] {
	return items
		.filter((item): item is CosmeticItemDefinition => item.type === 'cosmetic')
		.map((item) => ({
			id: item.id,
			title: item.title,
			relativeDirectory: item.relativeDirectory,
			unlockWeight: item.shopPurchasable?.unlockWeight ?? null,
		}))
		.sort((left, right) => {
			const weightCompare = cosmeticWeightValue(right) - cosmeticWeightValue(left);
			if (weightCompare !== 0) {
				return weightCompare;
			}

			const titleCompare = left.title.localeCompare(right.title);
			if (titleCompare !== 0) {
				return titleCompare;
			}

			return left.id.localeCompare(right.id);
		});
}

async function copyOptionalMcmeta(
	sourceMcmetaPath: string | undefined,
	destinationMcmetaPath: string,
	context: GenerationContext,
): Promise<void> {
	if (!sourceMcmetaPath) {
		return;
	}

	await copyFileWithDirectory(sourceMcmetaPath, destinationMcmetaPath);
	context.generatedFiles += 1;
}

async function writeJson(
	destinationPath: string,
	value: unknown,
	context: GenerationContext,
): Promise<void> {
	await writeJsonFile(destinationPath, value);
	context.generatedFiles += 1;
}

async function copyFile(
	sourcePath: string,
	destinationPath: string,
	context: GenerationContext,
): Promise<void> {
	await copyFileWithDirectory(sourcePath, destinationPath);
	context.generatedFiles += 1;
}

function createGeneratedItemModel(textureModelId: string): Record<string, unknown> {
	return {
		parent: 'minecraft:item/generated',
		textures: {
			layer0: textureModelId,
		},
	};
}

function getCharmLayerType(item: CharmItemDefinition): EquipmentLayerType {
	return item.equipmentSlot === 'legs' ? 'humanoid_leggings' : 'humanoid';
}

function createCharmEquipmentDefinition(
	namespace: string,
	item: CharmItemDefinition,
	material?: ArmorMaterial,
	minified = false,
): Record<string, unknown> {
	const layerType = getCharmLayerType(item);
	const charmLayer = { texture: `${namespace}:${item.equippableAssetId}` };
	const armorLayers = material
		? [
				{
					...(material === 'leather'
						? { dyeable: { color_when_undyed: LEATHER_UNDYED_COLOR } }
						: {}),
					texture: minified
						? `${namespace}:armor_minified/${material}`
						: `${material === 'enderite' ? namespace : 'minecraft'}:${material}`,
				},
				...(minified && material === 'leather'
					? [{ texture: `${namespace}:armor_minified/leather_overlay` }]
					: []),
			]
		: [];

	return {
		layers: {
			[layerType]: [...armorLayers, charmLayer],
		},
	};
}

async function generateMinifiedArmorAssets(context: GenerationContext): Promise<boolean> {
	const { outputDir, namespace, armorAssetsDir } = context.options;
	const required = ['humanoid', 'humanoid_leggings'].flatMap((layerType) =>
		CHARM_ARMOR_MATERIALS.flatMap((material) =>
			[material, ...(material === 'leather' ? ['leather_overlay'] : [])].map((name) =>
				path.join(armorAssetsDir, 'minified_armor_assets', layerType, `${name}.png`),
			),
		),
	);
	required.push(
		path.join(armorAssetsDir, 'minified_armor_assets', 'humanoid', 'turtle_scute.png'),
	);
	const missing = required.filter((file) => !existsSync(file));
	if (missing.length) {
		console.warn(
			`Skipping minified armor assets: ${missing.length} of ${required.length} textures missing from ${path.join(armorAssetsDir, 'minified_armor_assets')}`,
		);
		return false;
	}
	for (const layerType of ['humanoid', 'humanoid_leggings'] as const) {
		for (const material of CHARM_ARMOR_MATERIALS) {
			for (const name of [material, ...(material === 'leather' ? ['leather_overlay'] : [])]) {
				await copyFile(
					path.join(armorAssetsDir, 'minified_armor_assets', layerType, `${name}.png`),
					equipmentTexturePngPath(
						outputDir,
						namespace,
						layerType,
						`armor_minified/${name}`,
					),
					context,
				);
			}
		}
	}
	await copyFile(
		path.join(armorAssetsDir, 'minified_armor_assets', 'humanoid', 'turtle_scute.png'),
		equipmentTexturePngPath(outputDir, namespace, 'humanoid', 'armor_minified/turtle_scute'),
		context,
	);
	for (const material of CHARM_ARMOR_MATERIALS) {
		const layers: Record<string, unknown> = {};
		for (const layerType of ['humanoid', 'humanoid_leggings'] as const) {
			const textures = [material, ...(material === 'leather' ? ['leather_overlay'] : [])];
			layers[layerType] = textures.map((name) => ({
				...(name === 'leather'
					? { dyeable: { color_when_undyed: LEATHER_UNDYED_COLOR } }
					: {}),
				texture: `${namespace}:armor_minified/${name}`,
			}));
		}
		await writeJson(
			equipmentJsonPath(outputDir, namespace, `armor_minified__${material}`),
			{ layers },
			context,
		);
	}
	await writeJson(
		equipmentJsonPath(outputDir, namespace, 'armor_minified__turtle_scute'),
		{
			layers: {
				humanoid: [{ texture: `${namespace}:armor_minified/turtle_scute` }],
				humanoid_baby: [{ texture: `${namespace}:armor_minified/turtle_scute` }],
			},
		},
		context,
	);
	return true;
}

async function copyEnderiteArmorAssets(context: GenerationContext): Promise<void> {
	const { outputDir, namespace, armorAssetsDir } = context.options;
	for (const layerType of ['humanoid', 'humanoid_leggings'] as const) {
		await copyFile(
			path.join(armorAssetsDir, 'vanilla_armor_assets', layerType, 'enderite.png'),
			equipmentTexturePngPath(outputDir, namespace, layerType, 'enderite'),
			context,
		);
	}
}

async function generateBasicItem(
	item: BasicItemDefinition,
	context: GenerationContext,
): Promise<SelectorCase> {
	const { outputDir, namespace } = context.options;
	const modelId = itemModelId(namespace, item.resourcePath);

	await copyFile(
		item.texturePngPath,
		itemTexturePngPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await copyOptionalMcmeta(
		item.textureMcmetaPath,
		itemTextureMcmetaPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await writeJson(
		itemModelJsonPath(outputDir, namespace, item.resourcePath),
		createGeneratedItemModel(modelId),
		context,
	);
	if (item.id === 'charm-redstone-remote') {
		for (let frequency = 1; frequency <= 16; frequency++) {
			for (const lamp of ['off', 'on']) {
				if (frequency === 1 && lamp === 'off') continue;
				const name = `texture-${frequency}-${lamp}.png`;
				const variantPath = `${item.resourcePath}-${frequency}-${lamp}`;
				await copyFile(
					path.join(item.sourceDirectory, name),
					itemTexturePngPath(outputDir, namespace, variantPath),
					context,
				);
				await writeJson(
					itemModelJsonPath(outputDir, namespace, variantPath),
					createGeneratedItemModel(itemModelId(namespace, variantPath)),
					context,
				);
			}
		}
	}

	return {
		when: item.id,
		modelId,
		shadowModelId: modelId,
	};
}

async function generateBasic3dItem(
	item: Basic3dItemDefinition,
	context: GenerationContext,
): Promise<SelectorCase> {
	const { outputDir, namespace } = context.options;
	const modelId = itemModelId(namespace, item.resourcePath);

	await copyFile(
		item.modelTexturePngPath,
		itemTexturePngPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await copyOptionalMcmeta(
		item.modelTextureMcmetaPath,
		itemTextureMcmetaPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await writeJson(
		itemModelJsonPath(outputDir, namespace, item.resourcePath),
		await buildGeneratedSingleTextureModel(item, namespace),
		context,
	);

	return {
		when: item.id,
		modelId,
	};
}

async function generateCosmetic(
	item: CosmeticItemDefinition,
	context: GenerationContext,
): Promise<SelectorCase> {
	const { outputDir, namespace } = context.options;
	const modelId = itemModelId(namespace, item.resourcePath);

	await copyFile(
		item.modelTexturePngPath,
		itemTexturePngPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await copyOptionalMcmeta(
		item.modelTextureMcmetaPath,
		itemTextureMcmetaPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await writeJson(
		itemModelJsonPath(outputDir, namespace, item.resourcePath),
		await buildGeneratedSingleTextureModel(item, namespace),
		context,
	);

	return {
		when: item.id,
		modelId,
		isTinted: item.isDyeable,
	};
}

async function generateCharmEquipmentVariants(
	item: CharmItemDefinition,
	context: GenerationContext,
): Promise<void> {
	const { outputDir, namespace } = context.options;
	const layerType = getCharmLayerType(item);

	const charmAssetId = item.equippableAssetId;

	await writeJson(
		equipmentJsonPath(outputDir, namespace, charmAssetId),
		createCharmEquipmentDefinition(namespace, item),
		context,
	);
	await copyFile(
		item.equippablePngPath,
		equipmentTexturePngPath(outputDir, namespace, layerType, charmAssetId),
		context,
	);

	for (const material of CHARM_ARMOR_MATERIALS) {
		const assetId = replaceTrailingVariant(item.equippableAssetId, material);
		await writeJson(
			equipmentJsonPath(outputDir, namespace, assetId),
			createCharmEquipmentDefinition(namespace, item, material),
			context,
		);
		if (context.minifiedArmorAssetsAvailable) {
			await writeJson(
				equipmentJsonPath(outputDir, namespace, `${assetId}__minified`),
				createCharmEquipmentDefinition(namespace, item, material, true),
				context,
			);
		}
	}
}

async function generateCharm(
	item: CharmItemDefinition,
	context: GenerationContext,
): Promise<SelectorCase> {
	const { outputDir, namespace } = context.options;
	const modelId = itemModelId(namespace, item.resourcePath);

	await copyFile(
		item.texturePngPath,
		itemTexturePngPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await copyOptionalMcmeta(
		item.textureMcmetaPath,
		itemTextureMcmetaPath(outputDir, namespace, item.resourcePath),
		context,
	);
	await writeJson(
		itemModelJsonPath(outputDir, namespace, item.resourcePath),
		createGeneratedItemModel(modelId),
		context,
	);
	await generateCharmEquipmentVariants(item, context);

	return {
		when: item.id,
		modelId,
	};
}

export async function generateResourcePack(
	items: readonly DiscoveredItem[],
	options: GeneratorOptions,
): Promise<GenerationSummary> {
	const sourceRoot = path.resolve(options.sourceDir);
	const outputRoot = path.resolve(options.outputDir);
	const overlaps = (parent: string, child: string) => {
		const relative = path.relative(parent, child);
		return (
			relative === '' ||
			(!relative.startsWith(`..${path.sep}`) &&
				relative !== '..' &&
				!path.isAbsolute(relative))
		);
	};
	if (overlaps(sourceRoot, outputRoot) || overlaps(outputRoot, sourceRoot))
		throw new Error('Resource pack output must be separate from source data.');
	const context: GenerationContext = {
		options,
		minifiedArmorAssetsAvailable: false,
		generatedFiles: 0,
	};

	let skippedItems = 0;

	await resetDirectory(options.outputDir);
	await writeTextFile(
		path.join(options.outputDir, 'assets/minecraft/optifine/emissive.properties'),
		'suffix.emissive=_e\n',
	);
	context.generatedFiles += 1;

	await writeJson(
		path.join(options.outputDir, 'pack.mcmeta'),
		{
			...PACK_MCMETA,
			pack: {
				...PACK_MCMETA.pack,
				description: options.packDescription,
			},
		},
		context,
	);
	await copyEnderiteArmorAssets(context);
	context.minifiedArmorAssetsAvailable = await generateMinifiedArmorAssets(context);

	const commandBlockCases: SelectorCase[] = [];
	const carvedPumpkinCases: SelectorCase[] = [];

	for (const item of items) {
		try {
			switch (item.type) {
				case 'basic':
					commandBlockCases.push(await generateBasicItem(item, context));
					break;
				case 'basic-3d':
					commandBlockCases.push(await generateBasic3dItem(item, context));
					break;
				case 'cosmetic':
					carvedPumpkinCases.push(await generateCosmetic(item, context));
					break;
				case 'charm':
					commandBlockCases.push(await generateCharm(item, context));
					break;
			}
			const isModel = item.type === 'basic-3d' || item.type === 'cosmetic';
			context.generatedFiles += await generateEmissiveTexture(
				isModel ? item.modelTexturePngPath : item.texturePngPath,
				itemTexturePngPath(options.outputDir, options.namespace, item.resourcePath),
				isModel
					? await readJsonFile<Record<string, unknown>>(item.modelJsonPath)
					: undefined,
			);
			if (item.type === 'charm')
				context.generatedFiles += await generateEmissiveTexture(
					item.equippablePngPath,
					equipmentTexturePngPath(
						options.outputDir,
						options.namespace,
						getCharmLayerType(item),
						item.equippableAssetId,
					),
				);
		} catch (error) {
			skippedItems += 1;
			logRecoverableGenerationSkip(item, error);
		}
	}

	commandBlockCases.sort((left, right) => left.when.localeCompare(right.when));
	carvedPumpkinCases.sort((left, right) => left.when.localeCompare(right.when));

	await writeJson(
		minecraftItemDefinitionPath(options.outputDir, 'command_block'),
		createCommandBlockItemDefinition(commandBlockCases),
		context,
	);
	await writeJson(
		minecraftItemDefinitionPath(options.outputDir, 'heart_of_the_sea'),
		createHeartOfTheSeaItemDefinition(commandBlockCases),
		context,
	);
	await writeJson(
		minecraftItemDefinitionPath(options.outputDir, 'carved_pumpkin'),
		createCarvedPumpkinItemDefinition(carvedPumpkinCases),
		context,
	);
	for (const itemId of FISHING_SHADOW_VANILLA_ITEMS) {
		const textureOverride = FISHING_SHADOW_TEXTURE_OVERRIDES[itemId];
		if (textureOverride) {
			await writeJson(
				itemModelJsonPath(options.outputDir, 'mainmod', `fishing_shadow/${itemId}`),
				createGeneratedItemModel(textureOverride),
				context,
			);
		}
		await writeJson(
			fishingShadowItemDefinitionPath(options.outputDir, itemId),
			createFishingShadowItemDefinition(itemId),
			context,
		);
	}

	return {
		discoveredItems: items.length,
		basicItems: items.filter((item) => item.type === 'basic').length,
		basic3dItems: items.filter((item) => item.type === 'basic-3d').length,
		cosmetics: items.filter((item) => item.type === 'cosmetic').length,
		cosmeticWeightOrder: buildCosmeticWeightOrder(items),
		charms: items.filter((item) => item.type === 'charm').length,
		commandBlockCases: commandBlockCases.length,
		carvedPumpkinCases: carvedPumpkinCases.length,
		generatedFiles: context.generatedFiles,
		skippedItems,
	};
}
