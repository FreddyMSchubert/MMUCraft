import * as THREE from 'three';
import {
	applyRotationSpec,
	clamp,
	FACE_ORDER,
	FACE_TINT_DEFAULT,
	getCollapsedAxis,
	getFaceVertices,
	getUvCorners,
	inferDefaultUv,
	modelSpaceToWorld,
	normalizeMinMax,
	normalizeVector3,
	quadHasArea,
} from './minecraft-model-geometry';
import {
	createFaceMaterial,
	resolveItemSource,
	resolveModelTexture,
} from './minecraft-item-source';
import {
	hasMaterialColor,
	hasMaterialEmissive,
	parseColorValue,
	rgbToThreeColor,
	TextureRegistry,
} from './minecraft-texture-registry';
import type { ManagedTexture } from './minecraft-texture-registry';
import type {
	MinecraftElement,
	MinecraftItemSource,
	MinecraftModel,
	RgbColor,
} from './minecraft-model.types';

export class MinecraftModelObject {
	readonly group = new THREE.Group();
	private readonly textureRegistry: TextureRegistry;
	private readonly tintPalette = new Map<number, RgbColor>();
	private readonly meshes: THREE.Mesh[] = [];
	private resolvedModel: MinecraftModel | null = null;
	private fallbackTexture: string | null = null;

	constructor(
		frameSequence: number[] | null = null,
		defaultTint: RgbColor = { r: 255, g: 0, b: 0 },
		private readonly unlit = false,
	) {
		this.textureRegistry = new TextureRegistry(frameSequence);
		this.tintPalette.set(0, defaultTint);
	}

	get model() {
		return this.resolvedModel;
	}

	async load(source: MinecraftItemSource) {
		this.disposeMeshes();
		const resolved = await resolveItemSource(source);
		this.resolvedModel = resolved.model;
		this.fallbackTexture = resolved.fallbackTexture;

		if (this.resolvedModel.elements?.length) {
			for (const element of this.resolvedModel.elements) {
				await this.buildElement(element, this.resolvedModel.textures ?? {});
			}
		} else {
			await this.buildGeneratedItem(this.resolvedModel.textures ?? {});
		}
		if (!this.meshes.length)
			throw new Error('Minecraft model did not produce renderable geometry.');
		return this.resolvedModel;
	}

	update(deltaMs: number, frameDelayMs: number) {
		this.textureRegistry.update(deltaMs, frameDelayMs);
	}

	isAnimated() {
		return this.textureRegistry.isAnimated();
	}

	hasFailedTextures() {
		return this.textureRegistry.hasFailed();
	}

	animationKey() {
		return this.textureRegistry.animationKey();
	}

	setTint(index: number, colorValue: RgbColor) {
		this.tintPalette.set(index, parseColorValue(colorValue));
		for (const mesh of this.meshes) {
			const tintIndex = mesh.userData.tintIndex as number;
			const nextColor =
				tintIndex > FACE_TINT_DEFAULT
					? rgbToThreeColor(
							this.tintPalette.get(tintIndex) ??
								this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 },
						)
					: new THREE.Color(1, 1, 1);
			const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
			for (const material of materials) {
				if (hasMaterialColor(material)) material.color.copy(nextColor);
				if (hasMaterialEmissive(material)) material.emissive.copy(nextColor);
			}
		}
	}

	dispose() {
		this.disposeMeshes();
		this.textureRegistry.dispose();
	}

	private disposeMeshes() {
		for (const mesh of this.meshes) {
			mesh.geometry.dispose();
			const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
			for (const material of materials) material.dispose();
			this.group.remove(mesh);
		}
		this.meshes.length = 0;
	}

	private addMesh(mesh: THREE.Mesh, metadata: Record<string, unknown>) {
		mesh.userData = metadata;
		this.meshes.push(mesh);
		this.group.add(mesh);
	}

	private async buildElement(element: MinecraftElement, textures: Record<string, string>) {
		const fromVector = normalizeVector3(element.from);
		const toVector = normalizeVector3(element.to);
		const { from, to } = normalizeMinMax(fromVector, toVector);
		const collapsedAxis = getCollapsedAxis(from, to);
		const shade = element.shade !== false;
		const lightEmission = clamp(Number(element.light_emission) || 0, 0, 15);

		for (const faceName of FACE_ORDER) {
			const face = element.faces?.[faceName];
			if (!face) continue;
			const vertices = getFaceVertices(faceName, from, to, collapsedAxis);
			const rotatedVertices = vertices.map((vertex) =>
				applyRotationSpec(vertex, element.rotation),
			);
			if (!quadHasArea(rotatedVertices)) continue;

			const textureSource = resolveModelTexture(face.texture, textures, this.fallbackTexture);
			const texture = await this.textureRegistry.get(textureSource);
			const uvRect = Array.isArray(face.uv) ? face.uv : inferDefaultUv(faceName, from, to);
			const uvCorners = getUvCorners(faceName, uvRect, face.rotation ?? 0);
			const worldVertices = rotatedVertices.map(modelSpaceToWorld);
			const geometry = new THREE.BufferGeometry();
			geometry.setAttribute(
				'position',
				new THREE.BufferAttribute(
					new Float32Array(
						worldVertices.flatMap((vertex) => [vertex.x, vertex.y, vertex.z]),
					),
					3,
				),
			);
			geometry.setAttribute(
				'uv',
				new THREE.BufferAttribute(
					new Float32Array(uvCorners.flatMap((uv) => [uv.x, uv.y])),
					2,
				),
			);
			geometry.setIndex([0, 1, 2, 0, 2, 3]);
			geometry.computeVertexNormals();

			const tintIndex =
				typeof face.tintindex === 'number' && Number.isInteger(face.tintindex)
					? face.tintindex
					: FACE_TINT_DEFAULT;
			const tintColor =
				tintIndex > FACE_TINT_DEFAULT
					? rgbToThreeColor(
							this.tintPalette.get(tintIndex) ??
								this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 },
						)
					: new THREE.Color(1, 1, 1);
			this.addMesh(
				new THREE.Mesh(
					geometry,
					createFaceMaterial(texture, tintColor, shade, lightEmission, this.unlit),
				),
				{ tintIndex, faceName, shade, lightEmission },
			);
		}
	}

	private async buildGeneratedItem(textures: Record<string, string>) {
		const layers = Object.entries(textures)
			.filter(([key]) => /^layer\d+$/.test(key))
			.sort(([left], [right]) => Number(left.slice(5)) - Number(right.slice(5)));
		if (!layers.length && this.fallbackTexture) layers.push(['layer0', this.fallbackTexture]);

		for (let layerIndex = 0; layerIndex < layers.length; layerIndex += 1) {
			const [, textureRef] = layers[layerIndex];
			const source = resolveModelTexture(textureRef, textures, this.fallbackTexture);
			const handle = await this.textureRegistry.getHandle(source);
			this.addGeneratedLayer(handle, layerIndex);
		}
	}

	private addGeneratedLayer(handle: ManagedTexture, layerIndex: number) {
		const width = handle.canvas.width;
		const height = handle.canvas.height;
		const alpha = handle.context.getImageData(0, 0, width, height).data;
		const positions: number[] = [];
		const uvs: number[] = [];
		const indices: number[] = [];
		const depth = 1 / 32 + layerIndex / 1024;
		const opaque = (x: number, y: number) =>
			x >= 0 && y >= 0 && x < width && y < height && alpha[(y * width + x) * 4 + 3] > 0;
		const addQuad = (vertices: number[][], textureUvs: number[][]) => {
			const start = positions.length / 3;
			for (const vertex of vertices) positions.push(vertex[0], vertex[1], vertex[2]);
			for (const uv of textureUvs) uvs.push(uv[0], uv[1]);
			indices.push(start, start + 1, start + 2, start, start + 2, start + 3);
		};

		addQuad(
			[
				[-0.5, 0.5, depth],
				[0.5, 0.5, depth],
				[0.5, -0.5, depth],
				[-0.5, -0.5, depth],
			],
			[
				[0, 0],
				[1, 0],
				[1, 1],
				[0, 1],
			],
		);
		addQuad(
			[
				[0.5, 0.5, -depth],
				[-0.5, 0.5, -depth],
				[-0.5, -0.5, -depth],
				[0.5, -0.5, -depth],
			],
			[
				[1, 0],
				[0, 0],
				[0, 1],
				[1, 1],
			],
		);

		for (let y = 0; y < height; y += 1) {
			for (let x = 0; x < width; x += 1) {
				if (!opaque(x, y)) continue;
				const left = x / width - 0.5;
				const right = (x + 1) / width - 0.5;
				const top = 0.5 - y / height;
				const bottom = 0.5 - (y + 1) / height;
				const pixelUv = Array.from({ length: 4 }, () => [
					(x + 0.5) / width,
					(y + 0.5) / height,
				]);
				if (!opaque(x - 1, y))
					addQuad(
						[
							[left, top, -depth],
							[left, top, depth],
							[left, bottom, depth],
							[left, bottom, -depth],
						],
						pixelUv,
					);
				if (!opaque(x + 1, y))
					addQuad(
						[
							[right, top, depth],
							[right, top, -depth],
							[right, bottom, -depth],
							[right, bottom, depth],
						],
						pixelUv,
					);
				if (!opaque(x, y - 1))
					addQuad(
						[
							[left, top, depth],
							[right, top, depth],
							[right, top, -depth],
							[left, top, -depth],
						],
						pixelUv,
					);
				if (!opaque(x, y + 1))
					addQuad(
						[
							[left, bottom, -depth],
							[right, bottom, -depth],
							[right, bottom, depth],
							[left, bottom, depth],
						],
						pixelUv,
					);
			}
		}

		const geometry = new THREE.BufferGeometry();
		geometry.setAttribute(
			'position',
			new THREE.BufferAttribute(new Float32Array(positions), 3),
		);
		geometry.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(uvs), 2));
		geometry.setIndex(indices);
		geometry.computeVertexNormals();
		this.addMesh(
			new THREE.Mesh(
				geometry,
				createFaceMaterial(handle.texture, new THREE.Color(1, 1, 1), true, 0, this.unlit),
			),
			{ tintIndex: FACE_TINT_DEFAULT, generatedLayer: layerIndex },
		);
	}
}
