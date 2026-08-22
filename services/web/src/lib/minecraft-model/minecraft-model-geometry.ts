import * as THREE from 'three';
import type { FaceName, MinecraftElementRotation } from './minecraft-model.types';

export const FACE_ORDER: FaceName[] = ['north', 'east', 'south', 'west', 'up', 'down'];
export const FACE_AXIS: Record<FaceName, 'x' | 'y' | 'z'> = {
	north: 'z',
	south: 'z',
	east: 'x',
	west: 'x',
	up: 'y',
	down: 'y',
};
export const FACE_SIGN: Record<FaceName, number> = {
	north: -1,
	south: 1,
	east: 1,
	west: -1,
	up: 1,
	down: -1,
};
export const FACE_UV_VERTEX_ORDER: Record<FaceName, number[]> = {
	north: [1, 0, 3, 2],
	south: [1, 0, 3, 2],
	east: [1, 0, 3, 2],
	west: [1, 0, 3, 2],
	up: [3, 2, 1, 0],
	down: [3, 2, 1, 0],
};

export const DEGENERATE_OFFSET = 0.001;
export const FACE_TINT_DEFAULT = -1;
export const MODEL_UV_UNITS = 16;
export const TICK_MS = 50;
export const DYE_CYCLE_MS = 4200;
export const MISSING_TEXTURE_SIZE = 16;

export function clamp(value: number, min: number, max: number) {
	return Math.max(min, Math.min(max, value));
}

export function toRadians(degrees = 0) {
	return THREE.MathUtils.degToRad(degrees);
}

export function normalizeVector3(values: unknown, fallback = [0, 0, 0]) {
	const source = Array.isArray(values) && values.length >= 3 ? values : fallback;
	return new THREE.Vector3(
		Number(source[0]) || 0,
		Number(source[1]) || 0,
		Number(source[2]) || 0,
	);
}

export function normalizeMinMax(from: THREE.Vector3, to: THREE.Vector3) {
	return {
		from: new THREE.Vector3(
			Math.min(from.x, to.x),
			Math.min(from.y, to.y),
			Math.min(from.z, to.z),
		),
		to: new THREE.Vector3(
			Math.max(from.x, to.x),
			Math.max(from.y, to.y),
			Math.max(from.z, to.z),
		),
	};
}

export function deepClone<T>(value: T): T {
	return value == null ? value : (JSON.parse(JSON.stringify(value)) as T);
}

export function resolveTextureReference(
	textureRef: string | undefined,
	textures: Record<string, string> = {},
) {
	if (!textureRef) return null;

	let current = textureRef;
	const visited = new Set<string>();
	while (current.startsWith('#')) {
		const key = current.slice(1);
		if (visited.has(key)) return null;
		visited.add(key);
		current = textures[key] ?? '';
		if (!current) return null;
	}

	return current;
}

export function inferDefaultUv(faceName: FaceName, from: THREE.Vector3, to: THREE.Vector3) {
	switch (faceName) {
		case 'down':
			return [from.x, 16 - to.z, to.x, 16 - from.z];
		case 'up':
			return [from.x, from.z, to.x, to.z];
		case 'north':
			return [16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y];
		case 'south':
			return [from.x, 16 - to.y, to.x, 16 - from.y];
		case 'west':
			return [from.z, 16 - to.y, to.z, 16 - from.y];
		case 'east':
			return [16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y];
	}
}

export function getFaceVertices(
	faceName: FaceName,
	from: THREE.Vector3,
	to: THREE.Vector3,
	collapsedAxis: 'x' | 'y' | 'z' | null,
) {
	const offset =
		collapsedAxis === FACE_AXIS[faceName] ? DEGENERATE_OFFSET * FACE_SIGN[faceName] : 0;

	switch (faceName) {
		case 'north':
			return [
				new THREE.Vector3(from.x, to.y, from.z + offset),
				new THREE.Vector3(to.x, to.y, from.z + offset),
				new THREE.Vector3(to.x, from.y, from.z + offset),
				new THREE.Vector3(from.x, from.y, from.z + offset),
			];
		case 'south':
			return [
				new THREE.Vector3(to.x, to.y, to.z + offset),
				new THREE.Vector3(from.x, to.y, to.z + offset),
				new THREE.Vector3(from.x, from.y, to.z + offset),
				new THREE.Vector3(to.x, from.y, to.z + offset),
			];
		case 'east':
			return [
				new THREE.Vector3(to.x + offset, to.y, from.z),
				new THREE.Vector3(to.x + offset, to.y, to.z),
				new THREE.Vector3(to.x + offset, from.y, to.z),
				new THREE.Vector3(to.x + offset, from.y, from.z),
			];
		case 'west':
			return [
				new THREE.Vector3(from.x + offset, to.y, to.z),
				new THREE.Vector3(from.x + offset, to.y, from.z),
				new THREE.Vector3(from.x + offset, from.y, from.z),
				new THREE.Vector3(from.x + offset, from.y, to.z),
			];
		case 'up':
			return [
				new THREE.Vector3(from.x, to.y + offset, to.z),
				new THREE.Vector3(to.x, to.y + offset, to.z),
				new THREE.Vector3(to.x, to.y + offset, from.z),
				new THREE.Vector3(from.x, to.y + offset, from.z),
			];
		case 'down':
			return [
				new THREE.Vector3(from.x, from.y + offset, from.z),
				new THREE.Vector3(to.x, from.y + offset, from.z),
				new THREE.Vector3(to.x, from.y + offset, to.z),
				new THREE.Vector3(from.x, from.y + offset, to.z),
			];
	}
}

export function getCollapsedAxis(from: THREE.Vector3, to: THREE.Vector3): 'x' | 'y' | 'z' | null {
	if (Math.abs(to.x - from.x) < 1e-7) return 'x';
	if (Math.abs(to.y - from.y) < 1e-7) return 'y';
	if (Math.abs(to.z - from.z) < 1e-7) return 'z';
	return null;
}

export function quadHasArea(vertices: THREE.Vector3[]) {
	if (vertices.length !== 4) return false;
	const edgeA = new THREE.Vector3().subVectors(vertices[1], vertices[0]);
	const edgeB = new THREE.Vector3().subVectors(vertices[3], vertices[0]);
	return new THREE.Vector3().crossVectors(edgeA, edgeB).lengthSq() > 1e-12;
}

export function rotateQuadUvs(uvs: THREE.Vector2[], steps: number) {
	const normalizedSteps = ((steps % 4) + 4) % 4;
	let current = [...uvs];
	for (let index = 0; index < normalizedSteps; index += 1) {
		current = [current[3], current[0], current[1], current[2]];
	}
	return current;
}

export function getRotationSteps(rotationDegrees = 0) {
	const snapped = Math.round(rotationDegrees / 90);
	return ((snapped % 4) + 4) % 4;
}

export function getUvCorners(faceName: FaceName, uvRect: number[], rotationDegrees = 0) {
	const [u1, v1, u2, v2] = uvRect;
	const faceSpaceCorners = [
		new THREE.Vector2(u1 / MODEL_UV_UNITS, v1 / MODEL_UV_UNITS),
		new THREE.Vector2(u2 / MODEL_UV_UNITS, v1 / MODEL_UV_UNITS),
		new THREE.Vector2(u2 / MODEL_UV_UNITS, v2 / MODEL_UV_UNITS),
		new THREE.Vector2(u1 / MODEL_UV_UNITS, v2 / MODEL_UV_UNITS),
	];
	const rotated = rotateQuadUvs(faceSpaceCorners, getRotationSteps(rotationDegrees));
	return FACE_UV_VERTEX_ORDER[faceName].map((index) => rotated[index]);
}

export function getRescaleMultiplier(angleDegrees: number) {
	const radians = Math.abs(toRadians(angleDegrees));
	const cosine = Math.abs(Math.cos(radians));
	return cosine < 1e-6 ? 1 : 1 / cosine;
}

export function applySingleAxisRotation(
	vertex: THREE.Vector3,
	origin: THREE.Vector3,
	axis: 'x' | 'y' | 'z',
	angle: number,
	rescale = false,
) {
	const moved = vertex.clone().sub(origin);
	if (rescale && angle) {
		const factor = getRescaleMultiplier(angle);
		if (axis === 'x') moved.multiply(new THREE.Vector3(1, factor, factor));
		if (axis === 'y') moved.multiply(new THREE.Vector3(factor, 1, factor));
		if (axis === 'z') moved.multiply(new THREE.Vector3(factor, factor, 1));
	}

	const axisVector =
		axis === 'x'
			? new THREE.Vector3(1, 0, 0)
			: axis === 'y'
				? new THREE.Vector3(0, 1, 0)
				: new THREE.Vector3(0, 0, 1);
	return moved.applyAxisAngle(axisVector, toRadians(angle)).add(origin);
}

export function applyRotationSpec(vertex: THREE.Vector3, rotationSpec?: MinecraftElementRotation) {
	if (!rotationSpec) return vertex;

	const origin = normalizeVector3(rotationSpec.origin);
	let result = vertex.clone();
	if (rotationSpec.axis && (rotationSpec.angle ?? 0) !== 0) {
		return applySingleAxisRotation(
			result,
			origin,
			rotationSpec.axis,
			rotationSpec.angle ?? 0,
			Boolean(rotationSpec.rescale),
		);
	}

	for (const axis of ['x', 'y', 'z'] as const) {
		const angle = rotationSpec[axis] ?? 0;
		if (!angle) continue;
		result = applySingleAxisRotation(
			result,
			origin,
			axis,
			angle,
			Boolean(rotationSpec.rescale),
		);
	}
	return result;
}

export function modelSpaceToWorld(vertex: THREE.Vector3) {
	return new THREE.Vector3((vertex.x - 8) / 16, (vertex.y - 8) / 16, (vertex.z - 8) / 16);
}
