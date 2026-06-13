import path from 'path';
import { CHARM_ARMOR_MATERIALS, LEATHER_UNDYED_COLOR, PACK_MCMETA } from '../config';
import type {
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
  ensureDirectory,
  pathExists,
  resetDirectory,
  writeJsonFile,
} from '../utils/fs';
import { writeCompositedArmorTexture, writeUpscaledCopy } from '../utils/image';
import { replaceTrailingVariant } from '../utils/paths';
import {
  createCarvedPumpkinItemDefinition,
  createCommandBlockItemDefinition,
} from './selectorDefinitions';
import { buildGeneratedSingleTextureModel } from './singleTextureModel';

interface GenerationContext {
  readonly options: GeneratorOptions;
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
  textureId: string,
): Record<string, unknown> {
  const layerType = getCharmLayerType(item);

  if (textureId.startsWith('leather__')) {
    return {
      layers: {
        [layerType]: [
          {
            dyeable: {
              color_when_undyed: LEATHER_UNDYED_COLOR,
            },
            texture: `${namespace}:${textureId}`,
          },
          {
            texture: `${namespace}:leather_overlay__${item.baseName}`,
          },
        ],
      },
    };
  }

  return {
    layers: {
      [layerType]: [
        {
          texture: `${namespace}:${textureId}`,
        },
      ],
    },
  };
}

function armorTexturePath(
  options: GeneratorOptions,
  layerType: EquipmentLayerType,
  assetName: string,
): string {
  if (assetName === 'enderite') {
    return path.join(
      path.dirname(options.vanillaArmorAssetsDir),
      '..',
      'packs',
      'general-pack',
      'assets',
      options.namespace,
      'textures',
      'entity',
      'equipment',
      layerType,
      'enderite.png',
    );
  }

  return path.join(options.vanillaArmorAssetsDir, layerType, `${assetName}.png`);
}

async function requireArmorTexture(
  options: GeneratorOptions,
  layerType: EquipmentLayerType,
  assetName: string,
): Promise<string> {
  const texturePath = armorTexturePath(options, layerType, assetName);
  if (!(await pathExists(texturePath))) {
    throw new Error(`Missing armor texture: ${texturePath}`);
  }

  return texturePath;
}

async function findArmorTexture(
  options: GeneratorOptions,
  layerType: EquipmentLayerType,
  assetName: string,
): Promise<string | undefined> {
  const texturePath = armorTexturePath(options, layerType, assetName);
  return (await pathExists(texturePath)) ? texturePath : undefined;
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

  return {
    when: item.id,
    modelId,
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
  const charmTextureId = charmAssetId;

  await writeJson(
    equipmentJsonPath(outputDir, namespace, charmAssetId),
    createCharmEquipmentDefinition(namespace, item, charmTextureId),
    context,
  );
  await copyFile(
    item.equippablePngPath,
    equipmentTexturePngPath(outputDir, namespace, layerType, charmTextureId),
    context,
  );

  for (const material of CHARM_ARMOR_MATERIALS) {
    const assetId = replaceTrailingVariant(item.equippableAssetId, material);
    const textureId = `${material}__${item.baseName}`;
    const equipmentDefinition = createCharmEquipmentDefinition(namespace, item, textureId);

    await writeJson(equipmentJsonPath(outputDir, namespace, assetId), equipmentDefinition, context);

    if (material === 'leather') {
      const leatherBasePath = await requireArmorTexture(
        context.options,
        layerType,
        'leather',
      );
      const leatherOverlayPath = await requireArmorTexture(
        context.options,
        layerType,
        'leather_overlay',
      );

      const generatedLeatherBasePath = equipmentTexturePngPath(
        outputDir,
        namespace,
        layerType,
        textureId,
      );
      const generatedLeatherOverlayPath = equipmentTexturePngPath(
        outputDir,
        namespace,
        layerType,
        `leather_overlay__${item.baseName}`,
      );

      await ensureDirectory(path.dirname(generatedLeatherBasePath));
      await writeUpscaledCopy({
        sourcePngPath: leatherBasePath,
        referencePngPath: item.equippablePngPath,
        destinationPngPath: generatedLeatherBasePath,
      });
      context.generatedFiles += 1;

      await writeCompositedArmorTexture({
        basePngPath: leatherOverlayPath,
        overlayPngPath: item.equippablePngPath,
        destinationPngPath: generatedLeatherOverlayPath,
      });
      context.generatedFiles += 1;
      continue;
    }

    const armorBasePath = material === 'enderite'
      ? await findArmorTexture(context.options, layerType, material)
      : await requireArmorTexture(context.options, layerType, material);
    if (!armorBasePath) {
      continue;
    }

    const generatedTexturePath = equipmentTexturePngPath(
      outputDir,
      namespace,
      layerType,
      textureId,
    );

    await ensureDirectory(path.dirname(generatedTexturePath));
    await writeCompositedArmorTexture({
      basePngPath: armorBasePath,
      overlayPngPath: item.equippablePngPath,
      destinationPngPath: generatedTexturePath,
    });
    context.generatedFiles += 1;
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
  const context: GenerationContext = {
    options,
    generatedFiles: 0,
  };

  let skippedItems = 0;

  await resetDirectory(options.outputDir);

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
    minecraftItemDefinitionPath(options.outputDir, 'carved_pumpkin'),
    createCarvedPumpkinItemDefinition(carvedPumpkinCases),
    context,
  );

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
