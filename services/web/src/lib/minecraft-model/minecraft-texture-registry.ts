import * as THREE from 'three';
import { loadAssetResponse } from '@/lib/asset-fetch-cache';
import { clamp, MISSING_TEXTURE_SIZE, TICK_MS } from './minecraft-model-geometry';
import type { RgbColor } from './minecraft-model.types';

export type MinecraftSkinModel = 'classic' | 'legacy' | 'slim';

export interface ImageResource {
	image: HTMLImageElement;
	minecraftSkinModel: MinecraftSkinModel;
}

const imageSourceCache = new Map<string, Promise<ImageResource>>();

export function hsvToRgb(hueInput: number, saturation = 1, value = 1): RgbColor {
	const hue = ((hueInput % 360) + 360) % 360;
	const c = value * saturation;
	const x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
	const m = value - c;
	let r = 0;
	let g = 0;
	let b = 0;

	if (hue < 60) {
		r = c;
		g = x;
	} else if (hue < 120) {
		r = x;
		g = c;
	} else if (hue < 180) {
		g = c;
		b = x;
	} else if (hue < 240) {
		g = x;
		b = c;
	} else if (hue < 300) {
		r = x;
		b = c;
	} else {
		r = c;
		b = x;
	}

	return {
		r: Math.round((r + m) * 255),
		g: Math.round((g + m) * 255),
		b: Math.round((b + m) * 255),
	};
}

export function rgbToThreeColor(rgb: RgbColor) {
	return new THREE.Color(rgb.r / 255, rgb.g / 255, rgb.b / 255);
}

export function parseColorValue(
	value: unknown,
	fallback: RgbColor = { r: 255, g: 255, b: 255 },
): RgbColor {
	if (value && typeof value === 'object' && !Array.isArray(value)) {
		const candidate = value as Partial<RgbColor>;
		return {
			r: clamp(Math.round(Number(candidate.r) || fallback.r), 0, 255),
			g: clamp(Math.round(Number(candidate.g) || fallback.g), 0, 255),
			b: clamp(Math.round(Number(candidate.b) || fallback.b), 0, 255),
		};
	}

	return fallback;
}

export function hasMaterialColor(
	material: THREE.Material,
): material is THREE.Material & { color: THREE.Color } {
	return 'color' in material && material.color instanceof THREE.Color;
}

export function hasMaterialEmissive(
	material: THREE.Material,
): material is THREE.Material & { emissive: THREE.Color } {
	return 'emissive' in material && material.emissive instanceof THREE.Color;
}

export function createCanvas(width: number, height: number) {
	const canvas = document.createElement('canvas');
	canvas.width = width;
	canvas.height = height;
	return canvas;
}

export function getCanvasContext(canvas: HTMLCanvasElement) {
	const context = canvas.getContext('2d');
	if (!context) throw new Error('The browser does not support 2D canvas rendering.');
	return context;
}

export function configureThreeTexture(texture: THREE.CanvasTexture) {
	texture.colorSpace = THREE.SRGBColorSpace;
	texture.magFilter = THREE.NearestFilter;
	texture.minFilter = THREE.NearestFilter;
	texture.generateMipmaps = false;
	texture.anisotropy = 1;
	texture.wrapS = THREE.ClampToEdgeWrapping;
	texture.wrapT = THREE.ClampToEdgeWrapping;
	texture.flipY = false;
	texture.needsUpdate = true;
	return texture;
}

export function drawMissingTexture(ctx: CanvasRenderingContext2D, width: number, height: number) {
	ctx.clearRect(0, 0, width, height);
	const block = Math.max(2, Math.floor(width / 4));
	for (let y = 0; y < height; y += block) {
		for (let x = 0; x < width; x += block) {
			ctx.fillStyle = (x / block + y / block) % 2 === 0 ? '#111111' : '#ff00ff';
			ctx.fillRect(x, y, block, block);
		}
	}
}

export function loadImageResource(url: string) {
	const cached = imageSourceCache.get(url);
	if (cached) return cached;

	const loading = (async () => {
		const response = await loadAssetResponse(url);
		const model = response.headers.get('X-Minecraft-Skin-Model');
		const minecraftSkinModel: MinecraftSkinModel =
			model === 'legacy' || model === 'slim' ? model : 'classic';
		const objectUrl = URL.createObjectURL(await response.blob());
		return await new Promise<HTMLImageElement>((resolve, reject) => {
			const image = new Image();
			image.crossOrigin = 'anonymous';
			image.onload = () => {
				URL.revokeObjectURL(objectUrl);
				resolve(image);
			};
			image.onerror = () => {
				URL.revokeObjectURL(objectUrl);
				reject(new Error(`Could not load texture source: ${url}`));
			};
			image.src = objectUrl;
		}).then((image) => ({ image, minecraftSkinModel }));
	})();
	imageSourceCache.set(url, loading);
	void loading.catch(() => imageSourceCache.delete(url));
	return loading;
}

export async function loadImageFromSource(url: string) {
	return (await loadImageResource(url)).image;
}

export class ManagedTexture {
	canvas = createCanvas(MISSING_TEXTURE_SIZE, MISSING_TEXTURE_SIZE);
	context = getCanvasContext(this.canvas);
	texture = configureThreeTexture(new THREE.CanvasTexture(this.canvas));
	sourceImage: HTMLImageElement | null = null;
	frameWidth = MISSING_TEXTURE_SIZE;
	frameHeight = MISSING_TEXTURE_SIZE;
	frameCount = 1;
	currentFrame = 0;
	frameElapsedMs = 0;
	frameSequenceIndex = 0;
	frameSequence: number[] | null;
	failed = false;

	constructor(frameSequence: number[] | null) {
		this.frameSequence = frameSequence;
		this.drawMissing();
	}

	drawMissing() {
		this.frameWidth = this.canvas.width;
		this.frameHeight = this.canvas.height;
		this.frameCount = 1;
		this.currentFrame = 0;
		drawMissingTexture(this.context, this.canvas.width, this.canvas.height);
		this.texture.needsUpdate = true;
	}

	setImage(image: HTMLImageElement) {
		this.failed = false;
		const sourceWidth = image.naturalWidth || image.width || MISSING_TEXTURE_SIZE;
		const sourceHeight = image.naturalHeight || image.height || sourceWidth;
		const isAnimatedVerticalStrip = sourceHeight > sourceWidth;

		this.sourceImage = image;
		this.frameWidth = sourceWidth;
		this.frameHeight = isAnimatedVerticalStrip ? sourceWidth : sourceHeight;
		this.frameCount = isAnimatedVerticalStrip
			? Math.max(1, Math.floor(sourceHeight / sourceWidth))
			: 1;
		this.currentFrame = 0;
		this.frameElapsedMs = 0;
		this.frameSequenceIndex = 0;

		if (this.canvas.width !== this.frameWidth || this.canvas.height !== this.frameHeight) {
			this.canvas.width = this.frameWidth;
			this.canvas.height = this.frameHeight;
			this.context = getCanvasContext(this.canvas);
		}

		const initialFrame = this.frameSequence?.[0] ?? 0;
		this.drawFrame(initialFrame);
	}

	drawFrame(frameIndex: number) {
		if (!this.sourceImage) {
			this.drawMissing();
			return;
		}

		const frame =
			this.frameCount > 0
				? ((frameIndex % this.frameCount) + this.frameCount) % this.frameCount
				: 0;
		const sourceY = frame * this.frameHeight;

		this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
		this.context.drawImage(
			this.sourceImage,
			0,
			sourceY,
			this.frameWidth,
			this.frameHeight,
			0,
			0,
			this.canvas.width,
			this.canvas.height,
		);

		this.currentFrame = frame;
		this.texture.needsUpdate = true;
	}

	update(deltaMs: number, frameDelayMs: number) {
		if (this.frameCount <= 1) return;
		const safeDelay = Math.max(TICK_MS, frameDelayMs);
		this.frameElapsedMs += deltaMs;

		while (this.frameElapsedMs >= safeDelay) {
			this.frameElapsedMs -= safeDelay;
			if (this.frameSequence?.length) {
				this.frameSequenceIndex = (this.frameSequenceIndex + 1) % this.frameSequence.length;
				this.drawFrame(this.frameSequence[this.frameSequenceIndex]);
			} else {
				this.drawFrame(this.currentFrame + 1);
			}
		}
	}

	dispose() {
		this.texture.dispose();
		this.canvas.width = 0;
		this.canvas.height = 0;
		this.sourceImage = null;
	}
}

export class TextureRegistry {
	private readonly handles = new Map<string, ManagedTexture>();

	constructor(private readonly frameSequence: number[] | null) {}

	async get(source: string | null) {
		return (await this.getHandle(source)).texture;
	}

	async getHandle(source: string | null) {
		const key = source ?? '';
		const existing = this.handles.get(key);
		if (existing) return existing;

		const handle = new ManagedTexture(this.frameSequence);
		if (source) {
			try {
				handle.setImage(await loadImageFromSource(source));
			} catch {
				handle.failed = true;
				handle.drawMissing();
			}
		}
		this.handles.set(key, handle);
		return handle;
	}

	update(deltaMs: number, frameDelayMs: number) {
		for (const handle of this.handles.values()) handle.update(deltaMs, frameDelayMs);
	}

	isAnimated() {
		return [...this.handles.values()].some((handle) => handle.frameCount > 1);
	}

	hasFailed() {
		return [...this.handles.values()].some((handle) => handle.failed);
	}

	animationKey() {
		return [...this.handles.values()]
			.map((handle) => `${handle.currentFrame}:${handle.frameSequenceIndex}`)
			.join('|');
	}

	dispose() {
		for (const handle of this.handles.values()) handle.dispose();
		this.handles.clear();
	}
}
