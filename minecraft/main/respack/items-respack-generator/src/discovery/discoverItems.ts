import type { DiscoveredItem } from '../types';
import { pathExists, listSubdirectories } from '../utils/fs';
import { isRecoverableItemAssetError, parseItemDefinition } from './parseItemDefinition';

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

function errorMessage(error: unknown): string {
	return error instanceof Error ? error.message : String(error);
}

function logRecoverableSkip(leafDirectory: string, error: unknown): void {
	console.error('');
	console.error('!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	console.error('SKIPPING ITEM DUE TO RECOVERABLE ASSET ERROR');
	console.error(`Leaf directory: ${leafDirectory}`);
	console.error(errorMessage(error));
	console.error('!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	console.error('');
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
	const items: DiscoveredItem[] = [];

	for (const leafDirectory of leafDirectories.sort((left, right) => left.localeCompare(right))) {
		try {
			items.push(await parseItemDefinition(sourceRoot, leafDirectory));
		} catch (error) {
			if (isRecoverableItemAssetError(error)) {
				logRecoverableSkip(leafDirectory, error);
				continue;
			}

			throw error;
		}
	}

	ensureUnique(items, (item) => item.relativeDirectory, 'Leaf item directories');
	ensureUnique(items, (item) => item.id, 'Item id values');
	ensureUnique(
		items.filter(
			(item): item is Extract<DiscoveredItem, { type: 'charm' }> => item.type === 'charm',
		),
		(item) => item.equippableAssetId,
		'Charm equippableAssetId values',
	);

	return items.sort((left, right) =>
		left.relativeDirectory.localeCompare(right.relativeDirectory),
	);
}
