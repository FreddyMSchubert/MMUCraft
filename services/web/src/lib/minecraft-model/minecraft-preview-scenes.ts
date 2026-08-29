import type { MinecraftElement, MinecraftFace, MinecraftModel } from './minecraft-model.types';
import { loadImageResource, type MinecraftSkinModel } from './minecraft-texture-registry';

const SKIN_UV_SCALE = 4;

export async function loadPlayerModel(skinSource: string) {
	const { minecraftSkinModel } = await loadImageResource(skinSource);
	return createPlayerModel(skinSource, minecraftSkinModel);
}

export function createGrassFloorModel(assetRoot: string): MinecraftModel {
	const root = assetRoot.replace(/\/$/, '');
	return {
		textures: {
			bottom: `${root}/minecraft/textures/block/dirt.png`,
			side: `${root}/minecraft/textures/block/grass_block_side.png`,
			top: `${root}/minecraft/textures/block/grass_block_top.png`,
		},
		elements: [-16, 0, 16].map((x) => ({
			from: [x, -16, 0],
			to: [x + 16, 0, 16],
			faces: {
				north: { texture: '#side', uv: [0, 0, 16, 16] },
				east: { texture: '#side', uv: [0, 0, 16, 16] },
				south: { texture: '#side', uv: [0, 0, 16, 16] },
				west: { texture: '#side', uv: [0, 0, 16, 16] },
				up: { texture: '#top', tintindex: 0, uv: [0, 0, 16, 16] },
				down: { texture: '#bottom', uv: [0, 0, 16, 16] },
			},
		})),
	};
}

function createPlayerModel(skinSource: string, skinModel: MinecraftSkinModel): MinecraftModel {
	const slim = skinModel === 'slim';
	const legacy = skinModel === 'legacy';
	const armWidth = slim ? 3 : 4;
	const rightArmFrom = slim ? 1 : 0;
	const leftArmTo = slim ? 15 : 16;
	const elements = [
		skinCube([4, 4, 4], [12, 12, 12], 0, 0, 8, 8, 8),
		skinCube([4, -8, 6], [12, 4, 10], 16, 16, 8, 12, 4),
		skinCube([rightArmFrom, -8, 6], [4, 4, 10], 40, 16, armWidth, 12, 4),
		skinCube(
			[12, -8, 6],
			[leftArmTo, 4, 10],
			legacy ? 40 : 32,
			legacy ? 16 : 48,
			armWidth,
			12,
			4,
		),
		skinCube([4, -20, 6], [8, -8, 10], 0, 16, 4, 12, 4),
		skinCube([8, -20, 6], [12, -8, 10], legacy ? 0 : 16, legacy ? 16 : 48, 4, 12, 4),
	];

	if (!legacy) {
		elements.push(
			skinCube([3.5, 3.5, 3.5], [12.5, 12.5, 12.5], 32, 0, 8, 8, 8),
			skinCube([3.75, -8.25, 5.75], [12.25, 4.25, 10.25], 16, 32, 8, 12, 4),
			skinCube(
				[rightArmFrom - 0.25, -8.25, 5.75],
				[4.25, 4.25, 10.25],
				40,
				32,
				armWidth,
				12,
				4,
			),
			skinCube(
				[11.75, -8.25, 5.75],
				[leftArmTo + 0.25, 4.25, 10.25],
				48,
				48,
				armWidth,
				12,
				4,
			),
			skinCube([3.75, -20.25, 5.75], [8.25, -7.75, 10.25], 0, 32, 4, 12, 4),
			skinCube([7.75, -20.25, 5.75], [12.25, -7.75, 10.25], 0, 48, 4, 12, 4),
		);
	}

	return { texture_size: [64, 64], textures: { skin: skinSource }, elements };
}

function skinCube(
	from: number[],
	to: number[],
	u: number,
	v: number,
	width: number,
	height: number,
	depth: number,
): MinecraftElement {
	const face = (left: number, top: number, right: number, bottom: number): MinecraftFace => ({
		texture: '#skin',
		uv: [left, top, right, bottom].map((value) => value / SKIN_UV_SCALE),
	});

	return {
		from,
		to,
		faces: {
			north: face(u + depth, v + depth, u + depth + width, v + depth + height),
			east: face(u, v + depth, u + depth, v + depth + height),
			south: face(
				u + depth * 2 + width,
				v + depth,
				u + depth * 2 + width * 2,
				v + depth + height,
			),
			west: face(u + depth + width, v + depth, u + depth * 2 + width, v + depth + height),
			up: face(u + depth, v, u + depth + width, v + depth),
			down: face(u + depth + width, v, u + depth + width * 2, v + depth),
		},
	};
}
