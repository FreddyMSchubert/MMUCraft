import path from 'path';
import type { DiscoveredItem } from '../types';
import { pathExists, listSubdirectories } from '../utils/fs';
import { parseItemDefinition } from './parseItemDefinition';

async function collectLeafDirectories(directory: string): Promise<string[]> {
  const subdirectories = await listSubdirectories(directory);
  if (subdirectories.length === 0) {
    return [directory];
  }

  const leaves = await Promise.all(subdirectories.map((child) => collectLeafDirectories(child)));
  return leaves.flat();
}

function ensureUnique<T>(
  values: readonly T[],
  keySelector: (value: T) => string,
  label: string,
): void {
  const seen = new Map<string, number>();

  for (const value of values) {
    const key = keySelector(value);
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }

  const duplicates = [...seen.entries()].filter(([, count]) => count > 1).map(([key]) => key);
  if (duplicates.length > 0) {
    throw new Error(`${label} must be unique. Duplicates: ${duplicates.join(', ')}`);
  }
}

export async function discoverItems(sourceRoot: string): Promise<DiscoveredItem[]> {
  if (!(await pathExists(sourceRoot))) {
    throw new Error(`Source items directory does not exist: ${sourceRoot}`);
  }

  const sourceStat = await import('fs/promises').then((fs) => fs.stat(sourceRoot));
  if (!sourceStat.isDirectory()) {
    throw new Error(`Source items path is not a directory: ${sourceRoot}`);
  }

  const leafDirectories = await collectLeafDirectories(sourceRoot);
  const items = await Promise.all(
    leafDirectories
      .sort((left, right) => left.localeCompare(right))
      .map((leafDirectory) => parseItemDefinition(sourceRoot, leafDirectory)),
  );

  ensureUnique(items, (item) => item.relativeDirectory, 'Leaf item directories');
  ensureUnique(items, (item) => item.id, 'Item id values');
  ensureUnique(
    items.filter((item): item is Extract<DiscoveredItem, { type: 'charm' }> => item.type === 'charm'),
    (item) => item.equippableAssetId,
    'Charm equippableAssetId values',
  );

  return items.sort((left, right) => left.relativeDirectory.localeCompare(right.relativeDirectory));
}
