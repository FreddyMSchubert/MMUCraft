import path from 'path';
import type {
	Basic3dItemDefinition,
	BasicItemDefinition,
	CharmItemDefinition,
	CosmeticItemDefinition,
	DiscoveredItem,
	EquipmentSlot,
	ItemRarity,
	ShopPurchasableDefinition,
} from '../types';
import { pathExists, readJsonFile } from '../utils/fs';
import { assertValidResourcePath, toPosixPath } from '../utils/paths';

// Structural validation belongs to data/validation; the generator consumes that validated shape.
interface ValidatedBaseItemJson {
	readonly title: string;
	readonly id: string;
	readonly rarity: ItemRarity;
	readonly maxStackSize: number;
	readonly tooltips: readonly string[];
	readonly shopPurchasable?: Omit<ShopPurchasableDefinition, 'description'> & {
		readonly description?: string;
	};
	readonly dyeable?: unknown;
}

type ValidatedItemJson = ValidatedBaseItemJson &
	(
		| { readonly modelType: 'basic' | 'basic-3d' | 'cosmetic' }
		| {
				readonly modelType: 'charm';
				readonly equippableCharm: {
					readonly equipmentSlot: EquipmentSlot;
					readonly equippableAssetId: string;
				};
		  }
	);

export class RecoverableItemAssetError extends Error {
	constructor(message: string) {
		super(message);
		this.name = 'RecoverableItemAssetError';
	}
}

export function isRecoverableItemAssetError(error: unknown): error is RecoverableItemAssetError {
	return error instanceof RecoverableItemAssetError;
}

async function requireRecoverableFile(filePath: string): Promise<void> {
	if (!(await pathExists(filePath))) {
		throw new RecoverableItemAssetError(`Required file is missing: ${filePath}`);
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

function parseShopPurchasableComponent(
	value: ValidatedItemJson['shopPurchasable'],
): ShopPurchasableDefinition | undefined {
	if (value === undefined) {
		return undefined;
	}

	return {
		priceDabloons: value.priceDabloons,
		description: value.description ?? '',
		unlockMessage: value.unlockMessage,
		unlockWeight: value.unlockWeight,
	};
}

export async function parseItemDefinition(
	sourceRoot: string,
	leafDirectory: string,
): Promise<DiscoveredItem> {
	const relativeDirectory = toPosixPath(path.relative(sourceRoot, leafDirectory));
	assertValidResourcePath(relativeDirectory, 'Leaf item directory');

	const itemJsonPath = path.join(leafDirectory, 'item.json');
	const item = await readJsonFile<ValidatedItemJson>(itemJsonPath);
	const shopPurchasable = parseShopPurchasableComponent(item.shopPurchasable);

	const resourcePath = relativeDirectory;
	const baseName = path.posix.basename(relativeDirectory);
	const texturePngPath = path.join(leafDirectory, 'texture.png');
	const modelJsonPath = path.join(leafDirectory, 'model.json');
	const modelTexturePngPath = path.join(leafDirectory, 'model.png');

	switch (item.modelType) {
		case 'basic': {
			await requireRecoverableFile(texturePngPath);
			await assertAbsent(
				modelJsonPath,
				`${relativeDirectory} is modelType "basic" and must not include model.json. Use modelType "basic-3d" for custom item models.`,
			);
			await assertAbsent(
				modelTexturePngPath,
				`${relativeDirectory} is modelType "basic" and must not include model.png. Use modelType "basic-3d" for custom item models.`,
			);

			const definition: BasicItemDefinition = {
				type: 'basic',
				sourceDirectory: leafDirectory,
				relativeDirectory,
				title: item.title,
				id: item.id,
				rarity: item.rarity,
				maxStackSize: item.maxStackSize,
				tooltips: item.tooltips,
				shopPurchasable,
				resourcePath,
				baseName,
				texturePngPath,
				textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
			};
			return definition;
		}

		case 'basic-3d': {
			const hasModelJson = await hasFile(modelJsonPath);
			const hasModelPng = await hasFile(modelTexturePngPath);

			if (!hasModelJson || !hasModelPng) {
				throw new RecoverableItemAssetError(
					`${relativeDirectory} is modelType "basic-3d" and requires both model.json and model.png. Use modelType "basic" for flat texture items.`,
				);
			}

			const definition: Basic3dItemDefinition = {
				type: 'basic-3d',
				sourceDirectory: leafDirectory,
				relativeDirectory,
				title: item.title,
				id: item.id,
				rarity: item.rarity,
				maxStackSize: item.maxStackSize,
				tooltips: item.tooltips,
				shopPurchasable,
				resourcePath,
				baseName,
				modelJsonPath,
				modelTexturePngPath,
				modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
			};
			return definition;
		}

		case 'cosmetic': {
			await Promise.all([
				requireRecoverableFile(modelJsonPath),
				requireRecoverableFile(modelTexturePngPath),
			]);

			const definition: CosmeticItemDefinition = {
				type: 'cosmetic',
				sourceDirectory: leafDirectory,
				relativeDirectory,
				title: item.title,
				id: item.id,
				rarity: item.rarity,
				maxStackSize: item.maxStackSize,
				tooltips: item.tooltips,
				shopPurchasable,
				resourcePath,
				baseName,
				isDyeable: item.dyeable !== undefined,
				modelJsonPath,
				modelTexturePngPath,
				modelTextureMcmetaPath: await maybeMcmetaFor(modelTexturePngPath),
			};
			return definition;
		}

		case 'charm': {
			const equippableCharm = item.equippableCharm;
			const equippablePngPath = path.join(leafDirectory, 'equippable.png');

			await Promise.all([
				requireRecoverableFile(texturePngPath),
				requireRecoverableFile(equippablePngPath),
			]);

			const definition: CharmItemDefinition = {
				type: 'charm',
				sourceDirectory: leafDirectory,
				relativeDirectory,
				title: item.title,
				id: item.id,
				rarity: item.rarity,
				maxStackSize: item.maxStackSize,
				tooltips: item.tooltips,
				shopPurchasable,
				resourcePath,
				baseName,
				equipmentSlot: equippableCharm.equipmentSlot,
				equippableAssetId: equippableCharm.equippableAssetId,
				texturePngPath,
				textureMcmetaPath: await maybeMcmetaFor(texturePngPath),
				equippablePngPath,
			};
			return definition;
		}
	}
}
