export type FaceName = 'north' | 'east' | 'south' | 'west' | 'up' | 'down';
export type PreviewView = 'basic3d' | 'cosmetic' | 'icon' | 'item-frame' | 'player';

export interface TextureAnimationOptions {
	frameDelayMs: number;
	frames: number[] | null;
}

export interface MinecraftModelRendererOptions {
	assetRoot?: string;
	antialias?: boolean;
	animateDye?: boolean;
	animateTextures?: boolean;
	autoRotate?: boolean;
	background?: string | null;
	canvasClassName?: string;
	defaultTint?: RgbColor;
	dyeable?: boolean;
	enableDrag?: boolean;
	frameDelayMs?: number;
	frameSequence?: number[] | null;
	pixelRatio?: number;
	preserveDrawingBuffer?: boolean;
	rotationSpeed?: number;
	skinSource?: string;
	textureSource?: string;
	view?: PreviewView;
}

export interface MinecraftModelPreviewState {
	rotationX: number;
	rotationY: number;
	tintHue: number;
}

export interface MinecraftItemSource {
	assetRoot?: string;
	itemId?: string;
	model?: MinecraftModel;
	modelUrl?: string | null;
	textureUrl?: string | null;
}

export interface RgbColor {
	r: number;
	g: number;
	b: number;
}

export interface MinecraftModel {
	parent?: string;
	format_version?: string;
	credit?: string;
	ambientocclusion?: boolean;
	gui_light?: string;
	texture_size?: [number, number];
	textures?: Record<string, string>;
	display?: Record<string, MinecraftDisplayTransform>;
	elements?: MinecraftElement[];
	groups?: { name?: string; origin?: number[]; children?: (number | string)[] }[];
}

export interface MinecraftItemDefinition {
	model?: MinecraftItemModelNode;
}

export interface MinecraftItemModelNode {
	base?: string;
	cases?: { model?: MinecraftItemModelNode }[];
	entries?: { model?: MinecraftItemModelNode }[];
	fallback?: MinecraftItemModelNode;
	model?: string;
	models?: MinecraftItemModelNode[];
	on_false?: MinecraftItemModelNode;
	on_true?: MinecraftItemModelNode;
	type?: string;
}

export interface MinecraftDisplayTransform {
	rotation?: number[];
	translation?: number[];
	scale?: number[];
}

export interface MinecraftElement {
	from?: number[];
	to?: number[];
	rotation?: MinecraftElementRotation;
	shade?: boolean;
	light_emission?: number;
	faces?: Partial<Record<FaceName, MinecraftFace>>;
}

export interface MinecraftElementRotation {
	angle?: number;
	axis?: 'x' | 'y' | 'z';
	origin?: number[];
	rescale?: boolean;
	x?: number;
	y?: number;
	z?: number;
}

export interface MinecraftFace {
	texture?: string;
	uv?: number[];
	rotation?: number;
	tintindex?: number;
}
