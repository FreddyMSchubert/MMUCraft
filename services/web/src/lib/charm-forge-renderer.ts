import * as THREE from 'three';
import { type MinecraftItemSource, MinecraftModelObject } from '@/lib/minecraft-model-renderer';

interface CharmForgeRendererOptions {
	charm: MinecraftItemSource;
	ingredients: MinecraftItemSource[];
}

interface OrbitingItem {
	mesh: THREE.Group;
	angle: number;
	appearedAt: number;
	previousPosition: THREE.Vector3;
}

export class CharmForgeRenderer {
	private readonly scene = new THREE.Scene();
	private readonly camera = new THREE.PerspectiveCamera(36, 1, 0.1, 100);
	private readonly renderer: THREE.WebGLRenderer;
	private readonly clock = new THREE.Clock();
	private readonly root = new THREE.Group();
	private readonly ingredientRoot = new THREE.Group();
	private readonly modelObjects: MinecraftModelObject[] = [];
	private readonly orbiters: OrbitingItem[] = [];
	private readonly particles: THREE.Points;
	private readonly particlePositions: Float32Array;
	private readonly resizeObserver: ResizeObserver;
	private centralCharm: THREE.Group | null = null;
	private centralAppearedAt = 0;
	private frame: number | null = null;
	private destroyed = false;
	private pointer = new THREE.Vector2();
	private pointerActive = false;
	private enchantStartedAt: number | null = null;
	private enchantResolve: (() => void) | null = null;
	private burst = 0;
	private readonly reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

	constructor(
		private readonly container: HTMLElement,
		options: CharmForgeRendererOptions,
	) {
		this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
		this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
		this.renderer.outputColorSpace = THREE.SRGBColorSpace;
		this.renderer.domElement.className = 'charmForgeCanvas';
		this.container.appendChild(this.renderer.domElement);

		this.camera.position.set(0, 0.15, 10);
		this.scene.add(new THREE.AmbientLight(0xb9a7ff, 2.2));
		const light = new THREE.PointLight(0x9f65ff, 18, 20);
		light.position.set(0, 2.5, 4);
		this.scene.add(light);

		this.particlePositions = new Float32Array(360 * 3);
		for (let index = 0; index < this.particlePositions.length; index += 3) {
			const radius = 1.2 + Math.random() * 4.1;
			const angle = Math.random() * Math.PI * 2;
			this.particlePositions[index] = Math.cos(angle) * radius;
			this.particlePositions[index + 1] = (Math.random() - 0.5) * 4.8;
			this.particlePositions[index + 2] = (Math.random() - 0.5) * 2;
		}
		const particleGeometry = new THREE.BufferGeometry();
		particleGeometry.setAttribute(
			'position',
			new THREE.BufferAttribute(this.particlePositions, 3),
		);
		this.particles = new THREE.Points(
			particleGeometry,
			new THREE.PointsMaterial({
				color: 0xd5b6ff,
				size: 0.045,
				transparent: true,
				opacity: 0.75,
				blending: THREE.AdditiveBlending,
				depthWrite: false,
			}),
		);
		this.root.add(this.particles);
		this.root.add(this.ingredientRoot);
		this.scene.add(this.root);

		void this.addItem(options.charm, 2.15).then((mesh) => {
			if (this.destroyed) return;
			mesh.scale.setScalar(0);
			this.centralCharm = mesh;
			this.centralAppearedAt = performance.now();
			this.root.add(mesh);
		});
		options.ingredients.forEach((source, index) => {
			void this.addItem(source, 0.82).then((mesh) => {
				if (this.destroyed) return;
				const angle = (index / Math.max(1, options.ingredients.length)) * Math.PI * 2;
				const position = this.orbitTarget(angle, this.clock.elapsedTime, 1);
				mesh.position.copy(position);
				mesh.scale.setScalar(0);
				this.ingredientRoot.add(mesh);
				this.orbiters.push({
					mesh,
					angle,
					appearedAt: performance.now(),
					previousPosition: position.clone(),
				});
			});
		});

		this.handlePointerMove = this.handlePointerMove.bind(this);
		this.animate = this.animate.bind(this);
		this.container.addEventListener('pointermove', this.handlePointerMove);
		this.container.addEventListener('pointerleave', this.handlePointerLeave);
		this.resizeObserver = new ResizeObserver(() => {
			this.resize();
		});
		this.resizeObserver.observe(container);
		this.resize();
		this.animate();
	}

	playUpgrade() {
		if (this.enchantStartedAt !== null) return Promise.resolve();
		this.enchantStartedAt = performance.now();
		return new Promise<void>((resolve) => {
			this.enchantResolve = resolve;
		});
	}

	destroy() {
		if (this.destroyed) return;
		this.destroyed = true;
		if (this.frame !== null) cancelAnimationFrame(this.frame);
		this.resizeObserver.disconnect();
		this.container.removeEventListener('pointermove', this.handlePointerMove);
		this.container.removeEventListener('pointerleave', this.handlePointerLeave);
		for (const modelObject of this.modelObjects) modelObject.dispose();
		this.scene.traverse((object) => {
			if (!(object instanceof THREE.Mesh || object instanceof THREE.Points)) return;
			const renderable = object as THREE.Mesh;
			renderable.geometry.dispose();
			const materials = Array.isArray(renderable.material)
				? renderable.material
				: [renderable.material];
			for (const material of materials) material.dispose();
		});
		this.renderer.dispose();
		this.renderer.domElement.remove();
	}

	private async addItem(source: MinecraftItemSource, size: number) {
		const modelObject = new MinecraftModelObject();
		this.modelObjects.push(modelObject);
		await modelObject.load(source);
		const mesh = new THREE.Group();
		mesh.add(modelObject.group);
		const bounds = new THREE.Box3().setFromObject(modelObject.group);
		const center = bounds.getCenter(new THREE.Vector3());
		const extent = Math.max(0.01, ...bounds.getSize(new THREE.Vector3()).toArray());
		modelObject.group.position.sub(center);
		modelObject.group.scale.setScalar(size / extent);
		return mesh;
	}

	private animate() {
		if (this.destroyed) return;
		this.frame = requestAnimationFrame(this.animate);
		const deltaMs = this.clock.getDelta() * 1000;
		const elapsed = this.clock.elapsedTime;
		for (const modelObject of this.modelObjects) modelObject.update(deltaMs, 50);
		const duration = this.reducedMotion ? 0.35 : 1.9;
		const enchantProgress =
			this.enchantStartedAt === null
				? 0
				: Math.min(1, (performance.now() - this.enchantStartedAt) / 1000 / duration);
		const pull = enchantProgress <= 0 ? 0 : easeInOut(Math.min(1, enchantProgress / 0.56));
		const frameSeconds = Math.min(0.1, deltaMs / 1000);
		const moveAlpha = 1 - Math.exp(-(enchantProgress > 0 ? 12 : 7) * frameSeconds);
		const rotationAlpha = 1 - Math.exp(-9 * frameSeconds);

		for (const orbiter of this.orbiters) {
			const target = this.orbitTarget(orbiter.angle, elapsed, 1 - pull);
			const velocity = target.clone().sub(orbiter.previousPosition);
			orbiter.mesh.position.lerp(target, moveAlpha);
			orbiter.mesh.rotation.z = THREE.MathUtils.lerp(
				orbiter.mesh.rotation.z,
				-velocity.x * 1.8,
				rotationAlpha,
			);
			orbiter.mesh.rotation.x = THREE.MathUtils.lerp(
				orbiter.mesh.rotation.x,
				this.pointer.y * 0.24 + velocity.y,
				rotationAlpha,
			);
			orbiter.mesh.rotation.y = THREE.MathUtils.lerp(
				orbiter.mesh.rotation.y,
				this.pointer.x * 0.3 + elapsed * 0.35,
				rotationAlpha,
			);
			const entrance = easeOut(Math.min(1, (performance.now() - orbiter.appearedAt) / 450));
			const mergeScale = 1 - easeInOut(Math.max(0, (pull - 0.6) / 0.4));
			orbiter.mesh.scale.setScalar(entrance * mergeScale);
			orbiter.previousPosition.copy(target);
		}

		if (this.centralCharm) {
			this.centralCharm.position.y =
				Math.sin(elapsed * 1.25) * (this.reducedMotion ? 0.025 : 0.12);
			const idleTilt = this.reducedMotion ? 0 : 0.085;
			const targetX = this.pointerActive
				? -this.pointer.y * 0.34
				: Math.sin(elapsed * 0.7) * idleTilt;
			const targetY = this.pointerActive
				? this.pointer.x * 0.44
				: Math.cos(elapsed * 0.7) * idleTilt;
			this.centralCharm.rotation.x = THREE.MathUtils.lerp(
				this.centralCharm.rotation.x,
				targetX,
				rotationAlpha,
			);
			this.centralCharm.rotation.y = THREE.MathUtils.lerp(
				this.centralCharm.rotation.y,
				targetY,
				rotationAlpha,
			);
			const pulseProgress = Math.max(0, Math.min(1, (enchantProgress - 0.5) / 0.5));
			const pulse = Math.sin(pulseProgress * Math.PI) * 0.18;
			const entrance = easeOut(
				Math.min(1, (performance.now() - this.centralAppearedAt) / 500),
			);
			this.centralCharm.scale.setScalar(entrance * (1 + pulse));
		}

		this.root.rotation.z = Math.sin(elapsed * 0.35) * 0.025;
		this.particles.rotation.y = elapsed * (this.reducedMotion ? 0.01 : 0.08);
		if (enchantProgress > 0.5) this.burst = Math.max(this.burst, (enchantProgress - 0.5) * 2);
		const positions = this.particles.geometry.getAttribute('position') as THREE.BufferAttribute;
		for (let index = 0; index < this.particlePositions.length; index += 3) {
			this.particlePositions[index + 1] += 0.002 + this.burst * 0.018;
			if (this.particlePositions[index + 1] > 3.2) this.particlePositions[index + 1] = -3.2;
		}
		positions.needsUpdate = true;

		if (enchantProgress >= 1 && this.enchantResolve) {
			this.ingredientRoot.visible = false;
			this.enchantResolve();
			this.enchantResolve = null;
		}
		this.renderer.render(this.scene, this.camera);
	}

	private resize() {
		const width = Math.max(1, this.container.clientWidth);
		const height = Math.max(1, this.container.clientHeight);
		this.renderer.setSize(width, height, false);
		this.camera.aspect = width / height;
		this.camera.position.z = width < 620 ? 12.5 : 10;
		this.camera.updateProjectionMatrix();
	}

	private orbitTarget(angle: number, elapsed: number, radiusScale: number) {
		const currentAngle = angle + elapsed * (this.reducedMotion ? 0.06 : 0.24);
		return new THREE.Vector3(
			Math.cos(currentAngle) * 3.3 * radiusScale,
			Math.sin(currentAngle) * 1.85 * radiusScale,
			Math.sin(currentAngle * 2) * 0.65 * radiusScale,
		);
	}

	private handlePointerMove(event: PointerEvent) {
		const bounds = this.renderer.domElement.getBoundingClientRect();
		this.pointerActive = true;
		this.pointer.set(
			((event.clientX - bounds.left) / bounds.width) * 2 - 1,
			-(((event.clientY - bounds.top) / bounds.height) * 2 - 1),
		);
	}

	private readonly handlePointerLeave = () => {
		this.pointerActive = false;
		this.pointer.set(0, 0);
	};
}

function easeInOut(value: number) {
	return value < 0.5 ? 2 * value * value : 1 - Math.pow(-2 * value + 2, 2) / 2;
}

function easeOut(value: number) {
	return 1 - Math.pow(1 - value, 3);
}
