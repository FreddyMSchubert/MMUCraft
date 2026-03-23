import path from 'path';
import { CHARM_ARMOR_MATERIALS, LEATHER_UNDYED_COLOR, PACK_MCMETA } from '../config';
import type {
  Basic3dItemDefinition,
  BasicItemDefinition,
  CharmItemDefinition,
  CosmeticItemDefinition,
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

function vanillaArmorTexturePath(
  vanillaArmorAssetsDir: string,
  layerType: EquipmentLayerType,
  assetName: string,
): string {
  return path.join(vanillaArmorAssetsDir, layerType, `${assetName}.png`);
}

async function requireVanillaArmorTexture(
  vanillaArmorAssetsDir: string,
  layerType: EquipmentLayerType,
  assetName: string,
): Promise<string> {
  const texturePath = vanillaArmorTexturePath(vanillaArmorAssetsDir, layerType, assetName);
  if (!(await pathExists(texturePath))) {
    throw new Error(`Missing vanilla armor texture: ${texturePath}`);
  }

  return texturePath;
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
  const { outputDir, namespace, vanillaArmorAssetsDir } = context.options;
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
      const leatherBasePath = await requireVanillaArmorTexture(
        vanillaArmorAssetsDir,
        layerType,
        'leather',
      );
      const leatherOverlayPath = await requireVanillaArmorTexture(
        vanillaArmorAssetsDir,
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

    const vanillaBasePath = await requireVanillaArmorTexture(
      vanillaArmorAssetsDir,
      layerType,
      material,
    );
    const generatedTexturePath = equipmentTexturePngPath(
      outputDir,
      namespace,
      layerType,
      textureId,
    );

    await ensureDirectory(path.dirname(generatedTexturePath));
    await writeCompositedArmorTexture({
      basePngPath: vanillaBasePath,
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
    charms: items.filter((item) => item.type === 'charm').length,
    commandBlockCases: commandBlockCases.length,
    carvedPumpkinCases: carvedPumpkinCases.length,
    generatedFiles: context.generatedFiles,
    skippedItems,
  };
}
