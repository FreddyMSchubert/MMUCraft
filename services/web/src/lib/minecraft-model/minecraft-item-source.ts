import * as THREE from 'three';
import { AssetResponseError, loadAssetJson } from '@/lib/asset-fetch-cache';
import { deepClone, resolveTextureReference } from './minecraft-model-geometry';
import type {
	MinecraftItemDefinition,
	MinecraftItemModelNode,
	MinecraftItemSource,
	MinecraftModel,
} from './minecraft-model.types';

export interface ResolvedItemSource {
	fallbackTexture: string | null;
	model: MinecraftModel;
}

export const modelSourceCache = new Map<string, Promise<MinecraftModel | null>>();
export const itemDefinitionCache = new Map<string, Promise<MinecraftItemDefinition | null>>();

export function parseResourceId(value: string, fallbackNamespace = 'minecraft') {
	const [namespace, path] = value.includes(':')
		? value.split(':', 2)
		: [fallbackNamespace, value];
	return { namespace: namespace || fallbackNamespace, path: path || '' };
}

export function qualifyModelTextures(model: MinecraftModel, namespace: string, assetRoot: string) {
	const textures = Object.fromEntries(
		Object.entries(model.textures ?? {}).map(([key, value]) => {
			if (value.startsWith('#') || /^(?:https?:|\/)/.test(value)) return [key, value];
			const texture = parseResourceId(value, namespace);
			if (texture.namespace !== 'minecraft') return [key, ''];
			return [key, `${assetRoot}/${texture.namespace}/textures/${texture.path}.png`];
		}),
	);
	return { ...model, textures };
}

export function mergeModels(parent: MinecraftModel, child: MinecraftModel): MinecraftModel {
	return {
		...parent,
		...child,
		textures: { ...parent.textures, ...child.textures },
		display: { ...parent.display, ...child.display },
		elements: child.elements ?? parent.elements,
	};
}

export async function fetchModel(url: string) {
	const cached = modelSourceCache.get(url);
	if (cached) return cached;
	const loading = loadAssetJson<MinecraftModel>(url).catch((error: unknown) => {
		if (error instanceof AssetResponseError && error.status === 404) return null;
		modelSourceCache.delete(url);
		throw error;
	});
	modelSourceCache.set(url, loading);
	return loading;
}

export async function fetchItemDefinition(url: string) {
	const cached = itemDefinitionCache.get(url);
	if (cached) return cached;
	const loading = loadAssetJson<MinecraftItemDefinition>(url).catch((error: unknown) => {
		if (error instanceof AssetResponseError && error.status === 404) return null;
		itemDefinitionCache.delete(url);
		throw error;
	});
	itemDefinitionCache.set(url, loading);
	return loading;
}

export function itemDefinitionModel(definition: MinecraftItemDefinition | null) {
	const findModel = (node: MinecraftItemModelNode | undefined): string | null => {
		if (!node) return null;
		if (node.type === 'minecraft:model' && typeof node.model === 'string') return node.model;
		if (node.type === 'minecraft:special' && node.base) return node.base;
		for (const candidate of [node.on_true, node.on_false, node.fallback]) {
			const found = findModel(candidate);
			if (found) return found;
		}
		for (const candidate of [
			...(node.models ?? []),
			...(node.entries ?? []).map((entry) => entry.model),
			...(node.cases ?? []).map((entry) => entry.model),
		]) {
			const found = findModel(candidate);
			if (found) return found;
		}
		return null;
	};
	return findModel(definition?.model);
}

export async function resolveModelParents(
	model: MinecraftModel,
	namespace: string,
	assetRoot: string | undefined,
	seen = new Set<string>(),
): Promise<MinecraftModel> {
	const child = assetRoot ? qualifyModelTextures(model, namespace, assetRoot) : deepClone(model);
	if (!assetRoot || !model.parent || model.parent.startsWith('builtin/')) return child;

	const parentId = parseResourceId(model.parent, namespace);
	if (parentId.namespace !== 'minecraft') return child;
	const parentPath = parentId.path.includes('/') ? parentId.path : `item/${parentId.path}`;
	const key = `${parentId.namespace}:${parentPath}`;
	if (seen.has(key)) return child;
	seen.add(key);

	const parent = await fetchModel(`${assetRoot}/${parentId.namespace}/models/${parentPath}.json`);
	if (!parent) return child;
	return mergeModels(
		await resolveModelParents(parent, parentId.namespace, assetRoot, seen),
		child,
	);
}

export async function resolveItemSource(source: MinecraftItemSource): Promise<ResolvedItemSource> {
	const assetRoot = source.assetRoot?.replace(/\/$/, '');
	const itemId = parseResourceId(source.itemId ?? 'minecraft:air');
	if (!source.model && !source.modelUrl && source.textureUrl) {
		return {
			fallbackTexture: source.textureUrl,
			model: { parent: 'builtin/generated', textures: { layer0: source.textureUrl } },
		};
	}

	let model = source.model ?? (source.modelUrl ? await fetchModel(source.modelUrl) : null);
	let modelNamespace = itemId.namespace;

	if (!model && assetRoot && source.itemId && itemId.namespace === 'minecraft') {
		const definition = await fetchItemDefinition(
			`${assetRoot}/${itemId.namespace}/items/${itemId.path}.json`,
		);
		const modelIdValue = itemDefinitionModel(definition);
		if (modelIdValue) {
			const modelId = parseResourceId(modelIdValue, itemId.namespace);
			modelNamespace = modelId.namespace;
			model = await fetchModel(
				`${assetRoot}/${modelId.namespace}/models/${modelId.path}.json`,
			);
		} else if (!definition) {
			model =
				(await fetchModel(
					`${assetRoot}/${itemId.namespace}/models/block/${itemId.path}.json`,
				)) ??
				(await fetchModel(
					`${assetRoot}/${itemId.namespace}/models/item/${itemId.path}.json`,
				));
		}
	}

	if (model) {
		return {
			fallbackTexture: source.textureUrl ?? null,
			model: await resolveModelParents(model, modelNamespace, assetRoot),
		};
	}

	const fallbackTexture = source.textureUrl ?? null;
	return {
		fallbackTexture,
		model: {
			parent: 'builtin/generated',
			textures: fallbackTexture ? { layer0: fallbackTexture } : {},
		},
	};
}

export function resolveModelTexture(
	textureRef: string | undefined,
	textures: Record<string, string>,
	fallback: string | null,
) {
	if (fallback) return fallback;
	return resolveTextureReference(textureRef, textures);
}

export function createFaceMaterial(
	texture: THREE.Texture,
	tintColor: THREE.Color,
	shade: boolean,
	lightEmission: number,
	unlit: boolean,
) {
	const options = {
		map: texture,
		color: tintColor,
		transparent: true,
		alphaTest: 0.05,
		side: THREE.DoubleSide,
		toneMapped: false,
	};
	return shade && !unlit
		? new THREE.MeshStandardMaterial({
				...options,
				roughness: 1,
				metalness: 0,
				emissive: tintColor.clone(),
				emissiveMap: texture,
				emissiveIntensity: lightEmission / 15,
			})
		: new THREE.MeshBasicMaterial(options);
}
