import sharp from 'sharp';
import { copyFileWithDirectory, pathExists, readJsonFile } from '../utils/fs';

interface Element {
	light_emission?: number;
	from: number[];
	to: number[];
	faces?: Record<string, { uv?: number[]; texture?: string }>;
}

function defaultUv(face: string, from: number[], to: number[]): number[] {
	const [x, y, z] = from;
	const [X, Y, Z] = to;
	switch (face) {
		case 'down':
			return [x, 16 - Z, X, 16 - z];
		case 'up':
			return [x, z, X, Z];
		case 'north':
			return [16 - X, 16 - Y, 16 - x, 16 - y];
		case 'south':
			return [x, 16 - Y, X, 16 - y];
		case 'west':
			return [z, 16 - Y, Z, 16 - y];
		case 'east':
			return [16 - Z, 16 - Y, 16 - z, 16 - y];
		default:
			throw new Error(`Unknown model face: ${face}`);
	}
}

/** Writes only beside the output texture. Authored overlays always take precedence. */
export async function generateEmissiveTexture(
	source: string,
	destination: string,
	model?: Record<string, unknown>,
): Promise<number> {
	const authored = source.replace(/\.png$/i, '_e.png');
	const output = destination.replace(/\.png$/i, '_e.png');
	let metadata: string | undefined;
	if (await pathExists(authored)) {
		await copyFileWithDirectory(authored, output);
		metadata = (await pathExists(`${authored}.mcmeta`))
			? `${authored}.mcmeta`
			: `${source}.mcmeta`;
	} else {
		const elements = (model?.elements ?? []) as Element[];
		const rectangles = elements
			.filter((element) => Number(element.light_emission) > 0)
			.flatMap((element) =>
				Object.entries(element.faces ?? {})
					.filter(([, face]) => face.texture)
					.map(([name, face]) => ({
						uv: face.uv ?? defaultUv(name, element.from, element.to),
						level: Math.min(15, Math.max(0, Number(element.light_emission))),
					})),
			);
		if (!rectangles.length) return 0;
		const { data, info } = await sharp(source)
			.ensureAlpha()
			.raw()
			.toBuffer({ resolveWithObject: true });
		metadata = `${source}.mcmeta`;
		const animation = (await pathExists(metadata))
			? (await readJsonFile<{ animation?: { width?: number; height?: number } }>(metadata))
					.animation
			: undefined;
		const frameWidth =
			animation?.width ??
			(animation?.height
				? info.width
				: animation
					? Math.min(info.width, info.height)
					: info.width);
		const frameHeight =
			animation?.height ??
			(animation?.width
				? info.height
				: animation
					? Math.min(info.width, info.height)
					: info.height);
		if (
			frameWidth <= 0 ||
			frameHeight <= 0 ||
			info.width % frameWidth ||
			info.height % frameHeight
		)
			throw new Error(`Invalid animation frame dimensions: ${source}`);
		const mask = new Uint8Array(frameWidth * frameHeight);
		for (const { uv, level } of rectangles) {
			if (uv.length !== 4 || !uv.every(Number.isFinite))
				throw new Error(`Invalid emissive UV: ${source}`);
			// Face rotation changes orientation, not the covered rectangle. Reversed UVs are valid.
			const left = Math.max(0, Math.floor((Math.min(uv[0], uv[2]) * frameWidth) / 16));
			const right = Math.min(
				frameWidth,
				Math.ceil((Math.max(uv[0], uv[2]) * frameWidth) / 16),
			);
			const top = Math.max(0, Math.floor((Math.min(uv[1], uv[3]) * frameHeight) / 16));
			const bottom = Math.min(
				frameHeight,
				Math.ceil((Math.max(uv[1], uv[3]) * frameHeight) / 16),
			);
			for (let y = top; y < bottom; y++)
				for (let x = left; x < right; x++)
					mask[y * frameWidth + x] = Math.max(mask[y * frameWidth + x], level);
		}
		for (let y = 0; y < info.height; y++)
			for (let x = 0; x < info.width; x++) {
				const level = mask[(y % frameHeight) * frameWidth + (x % frameWidth)];
				if (!level) data.fill(0, (y * info.width + x) * 4, (y * info.width + x + 1) * 4);
				else {
					// OptiFine overlays render fullbright; encode lower emission in their RGB.
					const normalized = level / 15;
					const brightness = 1 - (1 - normalized / (4 - 3 * normalized)) ** 4;
					for (let channel = 0; channel < 3; channel++)
						data[(y * info.width + x) * 4 + channel] = Math.round(
							data[(y * info.width + x) * 4 + channel] * brightness,
						);
				}
			}
		await sharp(data, { raw: { width: info.width, height: info.height, channels: 4 } })
			.png()
			.toFile(output);
	}
	if (metadata && (await pathExists(metadata))) {
		await copyFileWithDirectory(metadata, `${output}.mcmeta`);
		return 2;
	}
	return 1;
}
