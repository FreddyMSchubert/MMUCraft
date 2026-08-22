import { randomInt } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import type { ItemRenderAsset, TextureAnimationDefinition } from './shop-item-catalog.types';

const ASSET_REVISION = `${Date.now().toString(36)}-${randomInt(0x100000000).toString(36)}`;

export function findItemDefinitionFiles(directory: string): string[] {
	return readdirSync(directory, { withFileTypes: true }).flatMap((child) => {
		const path = join(directory, child.name);
		return child.isDirectory()
			? findItemDefinitionFiles(path)
			: child.name === 'item.json'
				? [path]
				: [];
	});
}

export function itemRenderAsset(
	itemId: string,
	modelFilePath: string | null,
	textureFilePath: string | null,
): ItemRenderAsset {
	return {
		animation: textureFilePath ? readTextureAnimation(textureFilePath) : null,
		modelFilePath,
		modelUrl: modelFilePath ? shopAssetUrl('model', itemId) : null,
		textureFilePath,
		textureUrl: textureFilePath ? shopAssetUrl('texture', itemId) : null,
	};
}

export function flatItemTextureFile(
	itemId: string,
	directory: string,
	root: string,
): string | null {
	for (const name of ['texture.png', 'model.png']) {
		const path = join(directory, name);
		if (existsSync(path)) return path;
	}
	if (itemId === 'charm-wallet') {
		for (const path of [
			join(root, 'wallets', 'wallet-0', 'texture.png'),
			join(
				root,
				'..',
				'..',
				'..',
				'respack',
				'packs',
				'general-pack',
				'assets',
				'general-pack',
				'textures',
				'item',
				'wallet',
				'wallet-0.png',
			),
		])
			if (existsSync(path)) return path;
	}
	return null;
}

export function modelTextureFile(directory: string): string | null {
	for (const name of ['model.png', 'texture.png']) {
		const path = join(directory, name);
		if (existsSync(path)) return path;
	}
	return null;
}

export function modelDefinitionFile(directory: string): string | null {
	const path = join(directory, 'model.json');
	return existsSync(path) ? path : null;
}

export function readTextureAnimation(textureFilePath: string): TextureAnimationDefinition | null {
	const metadataPath = animationMetadataFile(textureFilePath);
	if (!metadataPath) return null;
	try {
		const parsed = JSON.parse(readFileSync(metadataPath, 'utf8')) as {
			animation?: { frametime?: unknown; frames?: unknown };
		};
		const frameTimeTicks =
			typeof parsed.animation?.frametime === 'number'
				? Math.max(1, parsed.animation.frametime)
				: 1;
		const frames = Array.isArray(parsed.animation?.frames)
			? parsed.animation.frames
					.map((frame) => {
						if (typeof frame === 'number') return frame;
						if (frame && typeof frame === 'object' && 'index' in frame)
							return frame.index;
						return null;
					})
					.filter(
						(frame): frame is number =>
							typeof frame === 'number' && Number.isInteger(frame) && frame >= 0,
					)
			: null;
		return { frameDelayMs: frameTimeTicks * 50, frames: frames?.length ? frames : null };
	} catch {
		return null;
	}
}

export function shopAssetUrl(kind: 'model' | 'texture', itemId: string): string {
	return `/api/shop/${kind}/${encodeURIComponent(itemId)}?v=${ASSET_REVISION}`;
}

function animationMetadataFile(textureFilePath: string): string | null {
	const direct = `${textureFilePath}.mcmeta`;
	if (existsSync(direct)) return direct;
	const directory = dirname(textureFilePath);
	const file = readdirSync(directory, { withFileTypes: true }).find(
		(child) => child.isFile() && child.name.endsWith('.mcmeta'),
	);
	return file ? join(directory, file.name) : null;
}
