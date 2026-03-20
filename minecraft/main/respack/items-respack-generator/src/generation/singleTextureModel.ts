import type { Basic3dItemDefinition, CosmeticItemDefinition } from '../types';
import { readJsonFile } from '../utils/fs';

function assertObjectRecord(value: unknown, label: string): asserts value is Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be a JSON object.`);
  }
}

export async function buildGeneratedSingleTextureModel(
  item: Pick<Basic3dItemDefinition | CosmeticItemDefinition, 'modelJsonPath' | 'resourcePath'>,
  namespace: string,
): Promise<Record<string, unknown>> {
  const rawModel = await readJsonFile<unknown>(item.modelJsonPath);
  assertObjectRecord(rawModel, `${item.modelJsonPath}`);

  const clonedModel = structuredClone(rawModel) as Record<string, unknown>;
  const currentTextures = clonedModel.textures;
  const targetTexture = `${namespace}:item/${item.resourcePath}`;

  if (currentTextures && typeof currentTextures === 'object' && !Array.isArray(currentTextures)) {
    const textureRecord = currentTextures as Record<string, unknown>;
    const rewrittenTextures: Record<string, string> = {};

    for (const key of Object.keys(textureRecord)) {
      rewrittenTextures[key] = targetTexture;
    }

    rewrittenTextures.particle = targetTexture;
    clonedModel.textures = rewrittenTextures;
  } else {
    clonedModel.textures = {
      particle: targetTexture,
      0: targetTexture,
    };
  }

  return clonedModel;
}
