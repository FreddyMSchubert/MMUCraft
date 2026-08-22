import * as THREE from 'three';
import {
	clamp,
	DYE_CYCLE_MS,
	normalizeVector3,
	TICK_MS,
	toRadians,
} from './minecraft-model-geometry';
import { MinecraftModelObject } from './minecraft-model-object';
import { hsvToRgb, parseColorValue } from './minecraft-texture-registry';
import type {
	MinecraftItemSource,
	MinecraftModel,
	MinecraftModelPreviewState,
	MinecraftModelRendererOptions,
	PreviewView,
} from './minecraft-model.types';

export class MinecraftModelRenderer {
	private readonly clock = new THREE.Clock();
	private readonly scene = new THREE.Scene();
	private readonly camera = new THREE.PerspectiveCamera(34, 1, 0.01, 100);
	private readonly renderer: THREE.WebGLRenderer;
	private readonly displayRoot = new THREE.Group();
	private readonly spinRoot = new THREE.Group();
	private readonly modelRoot = new THREE.Group();
	private readonly modelObject: MinecraftModelObject;
	private readonly assetRoot: string | undefined;
	private readonly fallbackTextureSource: string | undefined;
	private animationFrame: number | null = null;
	private currentDisplayMode = 'gui';
	private currentResolvedModel: MinecraftModel | null = null;
	private destroyed = false;
	private autoRotate: boolean;
	private frameDelayMs: number;
	private rotationSpeed: number;
	private dyeable: boolean;
	private view: PreviewView;
	private enableDrag: boolean;
	private dragging = false;
	private pointerX = 0;
	private pointerY = 0;
	private contextLost = false;
	private animateDye: boolean;
	private animateTextures: boolean;
	private tintHue = Math.random() * 360;

	constructor(
		private readonly container: HTMLElement,
		options: MinecraftModelRendererOptions,
	) {
		this.animateTextures = options.animateTextures ?? true;
		this.autoRotate = Boolean(options.autoRotate);
		this.frameDelayMs = Math.max(TICK_MS, options.frameDelayMs ?? TICK_MS);
		this.rotationSpeed = options.rotationSpeed ?? 0.85;
		this.dyeable = Boolean(options.dyeable);
		this.animateDye = this.dyeable && (options.animateDye ?? true);
		this.enableDrag = Boolean(options.enableDrag);
		this.view = options.view ?? 'basic3d';
		this.assetRoot = options.assetRoot;
		this.fallbackTextureSource = options.textureSource;
		if (this.view === 'cosmetic') this.spinRoot.rotation.y = Math.PI;
		this.modelObject = new MinecraftModelObject(
			options.frameSequence ?? null,
			parseColorValue(options.defaultTint, { r: 255, g: 0, b: 0 }),
			this.view === 'icon',
		);

		if (options.background) {
			this.scene.background = new THREE.Color(options.background);
		}

		this.renderer = new THREE.WebGLRenderer({
			antialias: options.antialias ?? true,
			alpha: !options.background,
			preserveDrawingBuffer: options.preserveDrawingBuffer,
		});
		this.renderer.setPixelRatio(options.pixelRatio ?? Math.min(window.devicePixelRatio, 2));
		this.renderer.outputColorSpace = THREE.SRGBColorSpace;
		this.renderer.domElement.classList.add(options.canvasClassName ?? 'shopModelCanvas');
		this.renderer.domElement.addEventListener('webglcontextlost', this.handleContextLost);
		this.renderer.domElement.addEventListener(
			'webglcontextrestored',
			this.handleContextRestored,
		);
		if (this.enableDrag) {
			this.renderer.domElement.style.touchAction = 'none';
			this.renderer.domElement.style.cursor = 'grab';
			this.renderer.domElement.addEventListener('pointerdown', this.handlePointerDown);
			this.renderer.domElement.addEventListener('pointermove', this.handlePointerMove);
			this.renderer.domElement.addEventListener('pointerup', this.handlePointerUp);
			this.renderer.domElement.addEventListener('pointercancel', this.handlePointerUp);
		}
		this.container.appendChild(this.renderer.domElement);

		this.scene.add(new THREE.AmbientLight(0xffffff, 1.4));

		const keyLight = new THREE.DirectionalLight(0xffffff, 1.25);
		keyLight.position.set(2.5, 3.5, 2.25);
		this.scene.add(keyLight);

		const fillLight = new THREE.DirectionalLight(0xffffff, 0.55);
		fillLight.position.set(-1.75, 1.8, -2.5);
		this.scene.add(fillLight);

		this.modelRoot.add(this.modelObject.group);
		this.spinRoot.add(this.modelRoot);
		this.displayRoot.add(this.spinRoot);
		this.scene.add(this.displayRoot);

		this.handleResize = this.handleResize.bind(this);
		this.animate = this.animate.bind(this);
		window.addEventListener('resize', this.handleResize);
		this.handleResize();
	}

	destroy() {
		if (this.destroyed) return;
		this.destroyed = true;
		this.contextLost = true;
		if (this.animationFrame !== null) {
			cancelAnimationFrame(this.animationFrame);
			this.animationFrame = null;
		}
		window.removeEventListener('resize', this.handleResize);
		this.renderer.domElement.removeEventListener('webglcontextlost', this.handleContextLost);
		this.renderer.domElement.removeEventListener(
			'webglcontextrestored',
			this.handleContextRestored,
		);
		this.renderer.domElement.removeEventListener('pointerdown', this.handlePointerDown);
		this.renderer.domElement.removeEventListener('pointermove', this.handlePointerMove);
		this.renderer.domElement.removeEventListener('pointerup', this.handlePointerUp);
		this.renderer.domElement.removeEventListener('pointercancel', this.handlePointerUp);
		this.modelObject.dispose();
		this.renderer.dispose();
		this.renderer.forceContextLoss();
		this.renderer.domElement.remove();
	}

	setAutoRotate(enabled: boolean) {
		this.autoRotate = enabled;
		this.ensureAnimating();
	}

	setDyeAnimation(enabled: boolean) {
		this.animateDye = this.dyeable && enabled;
		this.ensureAnimating();
	}

	getPreviewState(): MinecraftModelPreviewState {
		return {
			rotationX: this.spinRoot.rotation.x,
			rotationY: this.spinRoot.rotation.y,
			tintHue: this.tintHue,
		};
	}

	setPreviewState(state: MinecraftModelPreviewState) {
		this.spinRoot.rotation.x = state.rotationX;
		this.spinRoot.rotation.y = state.rotationY;
		this.tintHue = ((state.tintHue % 360) + 360) % 360;
		if (this.dyeable) this.modelObject.setTint(0, hsvToRgb(this.tintHue, 0.82, 1));
		this.ensureAnimating();
	}

	isAnimated() {
		return this.modelObject.isAnimated();
	}

	hasFailedTextures() {
		return this.modelObject.hasFailedTextures();
	}

	animationKey() {
		return this.modelObject.animationKey();
	}

	renderFrame(deltaMs = 0) {
		if (this.destroyed || this.contextLost) return;
		if (deltaMs > 0) this.modelObject.update(deltaMs, this.frameDelayMs);
		this.renderer.render(this.scene, this.camera);
	}

	copyFrameTo(canvas: HTMLCanvasElement) {
		if (this.destroyed || this.contextLost) return false;
		this.renderFrame();
		const source = this.renderer.domElement;
		const context = canvas.getContext('2d');
		if (!context) return false;
		canvas.width = source.width;
		canvas.height = source.height;
		context.drawImage(source, 0, 0);
		return true;
	}

	captureFrame() {
		return createImageBitmap(this.renderer.domElement);
	}

	async loadModel(model: MinecraftModel) {
		if (this.destroyed) return;
		this.currentResolvedModel = await this.modelObject.load({
			assetRoot: this.assetRoot,
			model,
			textureUrl: this.fallbackTextureSource,
		});
		this.applyDisplayTransform(this.currentDisplayMode);
		this.fitCameraToModel();
		this.ensureAnimating();
	}

	async loadItem(source: MinecraftItemSource) {
		if (this.destroyed) return;
		this.currentResolvedModel = await this.modelObject.load({
			assetRoot: source.assetRoot ?? this.assetRoot,
			itemId: source.itemId,
			model: source.model,
			modelUrl: source.modelUrl,
			textureUrl: source.textureUrl ?? this.fallbackTextureSource,
		});
		this.applyDisplayTransform(this.currentDisplayMode);
		this.fitCameraToModel();
		this.ensureAnimating();
	}

	applyDisplayTransform(mode = 'gui') {
		this.currentDisplayMode = mode;
		const transform =
			this.view === 'cosmetic' ? {} : (this.currentResolvedModel?.display?.[mode] ?? {});
		const isIconBlock =
			this.view === 'icon' && Boolean(this.currentResolvedModel?.elements?.length);
		const rotation = isIconBlock
			? new THREE.Vector3(35.264, 225, 0)
			: normalizeVector3(transform.rotation, [0, 0, 0]);
		const translation = normalizeVector3(transform.translation, [0, 0, 0]).multiplyScalar(
			1 / 16,
		);
		const scale = normalizeVector3(transform.scale, [1, 1, 1]);

		this.modelRoot.position.copy(translation);
		this.modelRoot.rotation.set(
			toRadians(rotation.x),
			toRadians(rotation.y),
			toRadians(rotation.z),
		);
		this.modelRoot.scale.copy(scale);
	}

	private handleResize() {
		const width = Math.max(1, this.container.clientWidth || 1);
		const height = Math.max(1, this.container.clientHeight || 1);
		this.camera.aspect = width / height;
		this.camera.updateProjectionMatrix();
		this.renderer.setSize(width, height, false);
		this.ensureAnimating();
	}

	private animate() {
		if (this.destroyed || this.contextLost || !this.renderer.domElement.isConnected) return;

		const deltaMs = this.clock.getDelta() * 1000;
		if (this.autoRotate) {
			this.spinRoot.rotation.y += (deltaMs / 1000) * this.rotationSpeed;
		}
		if (this.dyeable) {
			if (this.animateDye)
				this.tintHue = (this.tintHue + (deltaMs * 360) / DYE_CYCLE_MS) % 360;
			this.modelObject.setTint(0, hsvToRgb(this.tintHue, 0.82, 1));
		}

		if (this.animateTextures) this.modelObject.update(deltaMs, this.frameDelayMs);
		try {
			this.renderer.render(this.scene, this.camera);
		} catch {
			this.contextLost = true;
			return;
		}
		this.animationFrame =
			this.autoRotate ||
			this.animateDye ||
			(this.animateTextures && this.modelObject.isAnimated())
				? requestAnimationFrame(this.animate)
				: null;
	}

	private ensureAnimating() {
		if (
			this.destroyed ||
			this.contextLost ||
			this.animationFrame !== null ||
			!this.renderer.domElement.isConnected
		)
			return;
		this.clock.start();
		this.animate();
	}

	private fitCameraToModel() {
		const box = new THREE.Box3().setFromObject(this.displayRoot);
		if (box.isEmpty()) return;
		const dimensions = box.getSize(new THREE.Vector3());
		const size = Math.max(0.1, dimensions.length());
		const center = box.getCenter(new THREE.Vector3());
		const verticalFov = toRadians(this.camera.fov);
		const iconDistance =
			Math.max(
				dimensions.y / (2 * Math.tan(verticalFov / 2)),
				dimensions.x / (2 * Math.tan(verticalFov / 2) * this.camera.aspect),
			) +
			dimensions.z / 2;
		const distance =
			this.view === 'icon' ? Math.max(0.1, iconDistance) : Math.max(1.6, size * 0.9 + 1);
		const offset =
			this.view === 'cosmetic'
				? new THREE.Vector3(0, distance * 0.28, distance * 1.12)
				: new THREE.Vector3(0, 0, distance * (this.view === 'icon' ? 1 : 1.28));

		this.camera.position.copy(center.clone().add(offset));
		this.camera.lookAt(center);
		this.camera.near = Math.max(0.01, distance / 200);
		this.camera.far = Math.max(50, distance * 20);
		this.camera.updateProjectionMatrix();
	}

	private handlePointerDown = (event: PointerEvent) => {
		if (!this.enableDrag) return;
		this.dragging = true;
		this.pointerX = event.clientX;
		this.pointerY = event.clientY;
		this.renderer.domElement.setPointerCapture(event.pointerId);
		this.renderer.domElement.style.cursor = 'grabbing';
	};

	private handleContextLost = (event: Event) => {
		event.preventDefault();
		this.contextLost = true;
		if (this.animationFrame !== null) cancelAnimationFrame(this.animationFrame);
		this.animationFrame = null;
	};

	private handleContextRestored = () => {
		if (this.destroyed) return;
		this.contextLost = false;
		this.clock.start();
		this.animate();
	};

	private handlePointerMove = (event: PointerEvent) => {
		if (!this.dragging) return;
		const deltaX = event.clientX - this.pointerX;
		const deltaY = event.clientY - this.pointerY;
		this.pointerX = event.clientX;
		this.pointerY = event.clientY;
		this.spinRoot.rotation.y += deltaX * 0.012;
		this.spinRoot.rotation.x = clamp(this.spinRoot.rotation.x + deltaY * 0.008, -0.75, 0.75);
		this.ensureAnimating();
	};

	private handlePointerUp = (event: PointerEvent) => {
		if (!this.dragging) return;
		this.dragging = false;
		if (this.renderer.domElement.hasPointerCapture(event.pointerId))
			this.renderer.domElement.releasePointerCapture(event.pointerId);
		this.renderer.domElement.style.cursor = 'grab';
	};
}
