import path from 'path';
import type {
  Basic3dItemDefinition,
  BasicItemDefinition,
  CharmItemDefinition,
  CosmeticItemDefinition,
  DiscoveredItem,
  EquipmentSlot,
  ItemRarity,
} from '../types';
import { pathExists, readJsonFile } from '../utils/fs';
import {
  assertValidResourceIdentifier,
  assertValidResourcePath,
  toPosixPath,
} from '../utils/paths';

const ROOT_ALLOWED_KEYS = [
  'title',
  'id',
  'modelType',
  'rarity',
  'maxStackSize',
  'tooltips',
  'charm',
  'consumable',
  'dyeable',
  'equippableCharm',
  'equippableCosmetic',
] as const;

const RARITIES: readonly ItemRarity[] = ['common', 'uncommon', 'rare', 'epic'] as const;
const EQUIPMENT_SLOTS: readonly EquipmentSlot[] = ['chest', 'legs', 'feet'] as const;

interface ParsedBaseItem {
  readonly title: string;
  readonly id: string;
  readonly modelType: 'basic' | 'basic-3d' | 'charm' | 'cosmetic';
  readonly rarity: ItemRarity;
  readonly maxStackSize: number;
  readonly tooltips: readonly string[];
}

interface ParsedDyeableComponent {
  readonly isDyeable: true;
}

interface ParsedEquippableCharmComponent {
  readonly equipmentSlot: EquipmentSlot;
  readonly equippableAssetId: string;
}

function assertObjectRecord(value: unknown, label: string): asserts value is Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be a JSON object.`);
  }
}

function assertAllowedKeys(
  value: Record<string, unknown>,
  allowedKeys: readonly string[],
  label: string,
): void {
  const unknownKeys = Object.keys(value).filter((key) => !allowedKeys.includes(key));
  if (unknownKeys.length > 0) {
    throw new Error(`${label} contains unsupported keys: ${unknownKeys.join(', ')}`);
  }
}

function assertString(value: unknown, label: string): asserts value is string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${label} must be a non-empty string.`);
  }
}

function assertIntegerInRange(
  value: unknown,
  minimum: number,
  maximum: number,
  label: string,
): asserts value is number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${label} must be an integer between ${minimum} and ${maximum}.`);
  }
}

function assertStringArray(value: unknown, label: string): asserts value is string[] {
  if (!Array.isArray(value) || value.some((entry) => typeof entry !== 'string')) {
    throw new Error(`${label} must be an array of strings.`);
  }
}

function assertOneOf<T extends string>(
  value: unknown,
  allowedValues: readonly T[],
  label: string,
): asserts value is T {
  if (typeof value !== 'string' || !allowedValues.includes(value as T)) {
    throw new Error(`${label} must be one of: ${allowedValues.join(', ')}.`);
  }
}

function assertHexColour(value: unknown, label: string): asserts value is string {
  if (typeof value !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(value)) {
    throw new Error(`${label} must be a hex colour in the form #RRGGBB.`);
  }
}

async function requireFile(filePath: string): Promise<void> {
  if (!(await pathExists(filePath))) {
    throw new Error(`Required file is missing: ${filePath}`);
  }
}

async function maybeMcmetaFor(pngPath: string): Promise<string | undefined> {
  const mcmetaPath = `${pngPath}.mcmeta`;
  return (await pathExists(mcmetaPath)) ? mcmetaPath : undefined;
}

async function hasFile(filePath: string): Promise<boolean> {
  return pathExists(filePath);
}

async function assertAbsent(filePath: string, message: string): Promise<void> {
  if (await hasFile(filePath)) {
    throw new Error(message);
  }
}

function parseBaseItem(
  rawJson: Record<string, unknown>,
  itemJsonPath: string,
  relativeDirectory: string,
): ParsedBaseItem {
  assertAllowedKeys(rawJson, ROOT_ALLOWED_KEYS, itemJsonPath);

  assertString(rawJson.title, `${relativeDirectory} title`);
  assertString(rawJson.id, `${relativeDirectory} id`);
  assertValidResourceIdentifier(rawJson.id, `${relativeDirectory} id`);
  assertOneOf(rawJson.modelType, ['basic', 'basic-3d', 'charm', 'cosmetic'], `${relativeDirectory} modelType`);
  assertOneOf(rawJson.rarity, RARITIES, `${relativeDirectory} rarity`);
  assertIntegerInRange(rawJson.maxStackSize, 1, 99, `${relativeDirectory} maxStackSize`);
  assertStringArray(rawJson.tooltips, `${relativeDirectory} tooltips`);

  return {
    title: rawJson.title,
    id: rawJson.id,
    modelType: rawJson.modelType,
    rarity: rawJson.rarity,
    maxStackSize: rawJson.maxStackSize,
    tooltips: rawJson.tooltips,
  };
}

function parseDyeableComponent(
  value: unknown,
  relativeDirectory: string,
): ParsedDyeableComponent | undefined {
  if (value === undefined) {
    return undefined;
  }

  assertObjectRecord(value, `${relativeDirectory} dyeable`);
  assertAllowedKeys(value, ['tintColor'], `${relativeDirectory} dyeable`);
  assertHexColour(value.tintColor, `${relativeDirectory} dyeable.tintColor`);

  return {
    isDyeable: true,
  };
}

function parseEquippableCharmComponent(
  value: unknown,
  relativeDirectory: string,
): ParsedEquippableCharmComponent {
  assertObjectRecord(value, `${relativeDirectory} equippableCharm`);
  assertAllowedKeys(
    value,
    ['equipmentSlot', 'equippableAssetId'],
    `${relativeDirectory} equippableCharm`,
  );
  assertOneOf(
    value.equipmentSlot,
    EQUIPMENT_SLOTS,
    `${relativeDirectory} equippableCharm.equipmentSlot`,
  );
  assertString(
    value.equippableAssetId,
    `${relativeDirectory} equippableCharm.equippableAssetId`,
  );
  assertValidResourceIdentifier(
    value.equippableAssetId,
    `${relativeDirectory} equippableCharm.equippableAssetId`,
  );

  if (!value.equippableAssetId.endsWith('__charm')) {
    throw new Error(
      `${relativeDirectory} equippableCharm.equippableAssetId must end with "__charm".`,
    );
  }

  return {
    equipmentSlot: value.equipmentSlot,
    equippableAssetId: value.equippableAssetId,
  };
}

function assertEquippableCosmeticComponent(value: unknown, relativeDirectory: string): void {
  assertObjectRecord(value, `${relativeDirectory} equippableCosmetic`);
  assertAllowedKeys(value, [], `${relativeDirectory} equippableCosmetic`);
}

export async function parseItemDefinition(
  sourceRoot: string,
  leafDirectory: string,
): Promise<DiscoveredItem> {
  const relativeDirectory = toPosixPath(path.relative(sourceRoot, leafDirectory));
  assertValidResourcePath(relativeDirectory, 'Leaf item directory');

  const itemJsonPath = path.join(leafDirectory, 'item.json');
  await requireFile(itemJsonPath);

  const rawJson = await readJsonFile<unknown>(itemJsonPath);
  assertObjectRecord(rawJson, `item.json in ${relativeDirectory}`);

  const baseItem = parseBaseItem(rawJson, itemJsonPath, relativeDirectory);
  const dyeable = parseDyeableComponent(rawJson.dyeable, relativeDirectory);

  const resourcePath = relativeDirectory;
  const baseName = path.posix.basename(relativeDirectory);
  const texturePngPath = path.join(leafDirectory, 'texture.png');
  const modelJsonPath = path.join(leafDirectory, 'model.json');
  const modelTexturePngPath = path.join(leafDirectory, 'model.png');

  switch (baseItem.modelType) {
    case 'basic': {
      await requireFile(texturePngPath);
      await assertAbsent(
        modelJsonPath,
        `${relativeDirectory} is modelType "basic" and must not include model.json. Use modelType "basic-3d" for custom item models.`,
      );
      await assertAbsent(
        modelTexturePngPath,
        `${relativeDirectory} is modelType "basic" and must not include model.png. Use modelType "basic-3d" for custom item models.`,
      );

      const item: BasicItemDefinition = {
        type: 'basic',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        title: baseItem.title,
        id: baseItem.id,
        rarity: baseItem.rarity,
        maxStackSize: baseItem.maxStackSize,
        tooltips: baseItem.tooltips,
        resourcePath,
        baseName,
        texturePngPath,
        textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
      };
      return item;
    }
    case 'basic-3d': {
      if (!(await hasFile(modelJsonPath)) || !(await hasFile(modelTexturePngPath))) {
        throw new Error(
          `${relativeDirectory} is modelType "basic-3d" and requires both model.json and model.png. Use modelType "basic" for flat texture items.`,
        );
      }

      const item: Basic3dItemDefinition = {
        type: 'basic-3d',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        title: baseItem.title,
        id: baseItem.id,
        rarity: baseItem.rarity,
        maxStackSize: baseItem.maxStackSize,
        tooltips: baseItem.tooltips,
        resourcePath,
        baseName,
        modelJsonPath,
        modelTexturePngPath,
        modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
      };
      return item;
    }
    case 'cosmetic': {
      assertEquippableCosmeticComponent(rawJson.equippableCosmetic, relativeDirectory);
      await Promise.all([requireFile(modelJsonPath), requireFile(modelTexturePngPath)]);

      const item: CosmeticItemDefinition = {
        type: 'cosmetic',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        title: baseItem.title,
        id: baseItem.id,
        rarity: baseItem.rarity,
        maxStackSize: baseItem.maxStackSize,
        tooltips: baseItem.tooltips,
        resourcePath,
        baseName,
        isDyeable: dyeable?.isDyeable === true,
        modelJsonPath,
        modelTexturePngPath,
        modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
      };
      return item;
    }
    case 'charm': {
      const equippableCharm = parseEquippableCharmComponent(rawJson.equippableCharm, relativeDirectory);
      const equippablePngPath = path.join(leafDirectory, 'equippable.png');
      await Promise.all([requireFile(texturePngPath), requireFile(equippablePngPath)]);

      const item: CharmItemDefinition = {
        type: 'charm',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        title: baseItem.title,
        id: baseItem.id,
        rarity: baseItem.rarity,
        maxStackSize: baseItem.maxStackSize,
        tooltips: baseItem.tooltips,
        resourcePath,
        baseName,
        equipmentSlot: equippableCharm.equipmentSlot,
        equippableAssetId: equippableCharm.equippableAssetId,
        texturePngPath,
        textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
        equippablePngPath,
      };
      return item;
    }
    default:
      throw new Error(`Unsupported modelType in ${itemJsonPath}: ${String(baseItem.modelType)}`);
  }
}
