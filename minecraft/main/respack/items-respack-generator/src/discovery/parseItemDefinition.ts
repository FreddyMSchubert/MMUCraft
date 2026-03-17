import path from 'path';
import type {
  Basic3dItemDefinition,
  BasicItemDefinition,
  CharmItemDefinition,
  DiscoveredItem,
  HatItemDefinition,
} from '../types';
import { pathExists, readJsonFile } from '../utils/fs';
import {
  assertValidResourceIdentifier,
  assertValidResourcePath,
  toPosixPath,
} from '../utils/paths';

interface RawBasicItemDefinition {
  readonly type: 'basic';
  readonly custom_model_data: string;
}

interface RawBasic3dItemDefinition {
  readonly type: 'basic-3d';
  readonly custom_model_data: string;
}

interface RawHatItemDefinition {
  readonly type: 'hat';
  readonly custom_model_data: string;
  readonly isTinted: boolean;
}

interface RawCharmItemDefinition {
  readonly type: 'charm';
  readonly custom_model_data: string;
  readonly isLeggings: boolean;
  readonly equippable_asset_id: string;
}

type RawItemDefinition =
  | RawBasicItemDefinition
  | RawBasic3dItemDefinition
  | RawHatItemDefinition
  | RawCharmItemDefinition;

function assertObjectRecord(value: unknown, label: string): asserts value is Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be a JSON object.`);
  }
}

function assertExactKeys(
  value: Record<string, unknown>,
  expectedKeys: readonly string[],
  label: string,
): void {
  const actualKeys = Object.keys(value).sort();
  const expected = [...expectedKeys].sort();

  if (actualKeys.length !== expected.length || actualKeys.some((key, index) => key !== expected[index])) {
    throw new Error(
      `${label} must contain exactly these keys: ${expected.join(', ')}. Found: ${actualKeys.join(', ')}`,
    );
  }
}

function assertString(value: unknown, label: string): asserts value is string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${label} must be a non-empty string.`);
  }
}

function assertBoolean(value: unknown, label: string): asserts value is boolean {
  if (typeof value !== 'boolean') {
    throw new Error(`${label} must be a boolean.`);
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
  assertString(rawJson.type, `type in ${relativeDirectory}/item.json`);

  const resourcePath = relativeDirectory;
  const baseName = path.posix.basename(relativeDirectory);
  const texturePngPath = path.join(leafDirectory, 'texture.png');
  const modelJsonPath = path.join(leafDirectory, 'model.json');
  const modelTexturePngPath = path.join(leafDirectory, 'model.png');

  switch (rawJson.type) {
    case 'basic': {
      assertExactKeys(rawJson, ['type', 'custom_model_data'], itemJsonPath);
      assertString(rawJson.custom_model_data, `${relativeDirectory} custom_model_data`);

      await requireFile(texturePngPath);
      await assertAbsent(
        modelJsonPath,
        `${relativeDirectory} is type "basic" and must not include model.json. Use type "basic-3d" for custom item models.`,
      );
      await assertAbsent(
        modelTexturePngPath,
        `${relativeDirectory} is type "basic" and must not include model.png. Use type "basic-3d" for custom item models.`,
      );

      const item: BasicItemDefinition = {
        type: 'basic',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        customModelData: rawJson.custom_model_data,
        resourcePath,
        baseName,
        texturePngPath,
        textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
      };
      return item;
    }
    case 'basic-3d': {
      assertExactKeys(rawJson, ['type', 'custom_model_data'], itemJsonPath);
      assertString(rawJson.custom_model_data, `${relativeDirectory} custom_model_data`);

      if (!(await hasFile(modelJsonPath)) || !(await hasFile(modelTexturePngPath))) {
        throw new Error(
          `${relativeDirectory} is type "basic-3d" and requires both model.json and model.png. Use type "basic" for flat texture items.`,
        );
      }

      const item: Basic3dItemDefinition = {
        type: 'basic-3d',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        customModelData: rawJson.custom_model_data,
        resourcePath,
        baseName,
        modelJsonPath,
        modelTexturePngPath,
        modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
      };
      return item;
    }
    case 'hat': {
      assertExactKeys(rawJson, ['type', 'custom_model_data', 'isTinted'], itemJsonPath);
      assertString(rawJson.custom_model_data, `${relativeDirectory} custom_model_data`);
      assertBoolean(rawJson.isTinted, `${relativeDirectory} isTinted`);

      await Promise.all([requireFile(modelJsonPath), requireFile(modelTexturePngPath)]);

      const item: HatItemDefinition = {
        type: 'hat',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        customModelData: rawJson.custom_model_data,
        resourcePath,
        baseName,
        isTinted: rawJson.isTinted,
        modelJsonPath,
        modelTexturePngPath,
        modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
      };
      return item;
    }
    case 'charm': {
      assertExactKeys(
        rawJson,
        ['type', 'custom_model_data', 'isLeggings', 'equippable_asset_id'],
        itemJsonPath,
      );
      assertString(rawJson.custom_model_data, `${relativeDirectory} custom_model_data`);
      assertBoolean(rawJson.isLeggings, `${relativeDirectory} isLeggings`);
      assertString(
        rawJson.equippable_asset_id,
        `${relativeDirectory} equippable_asset_id`,
      );
      assertValidResourceIdentifier(
        rawJson.equippable_asset_id,
        `${relativeDirectory} equippable_asset_id`,
      );

      const equippablePngPath = path.join(leafDirectory, 'equippable.png');
      await Promise.all([requireFile(texturePngPath), requireFile(equippablePngPath)]);

      const item: CharmItemDefinition = {
        type: 'charm',
        sourceDirectory: leafDirectory,
        relativeDirectory,
        customModelData: rawJson.custom_model_data,
        resourcePath,
        baseName,
        isLeggings: rawJson.isLeggings,
        equippableAssetId: rawJson.equippable_asset_id,
        texturePngPath,
        textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
        equippablePngPath,
      };
      return item;
    }
    default:
      throw new Error(
        `Unsupported item type in ${itemJsonPath}: ${String(rawJson.type)}`,
      );
  }
}
