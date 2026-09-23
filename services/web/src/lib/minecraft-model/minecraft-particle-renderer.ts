import * as THREE from 'three';
import { ASSETS } from '@/lib/assets';
import { modelSpaceToWorld, TICK_MS } from './minecraft-model-geometry';
import { loadImageFromSource } from './minecraft-texture-registry';
import {
	PARTICLE_TYPES,
	type MinecraftParticle,
	type MinecraftParticleConstructor,
	type ParticleTexture,
} from './minecraft-particles';
import type { ParticleEmissionDefinition, ParticleEmissionSpec } from './minecraft-model.types';

interface Emitter {
	spec: ParticleEmissionSpec;
	type: MinecraftParticleConstructor;
	remainingMs: number;
}

interface VisibleParticle {
	particle: MinecraftParticle;
	sprite: THREE.Sprite;
}

const MAX_PARTICLES = 96;
const TEXTURE_FILES: Record<ParticleTexture, string[]> = {
	flame: ['particle/flame'],
	smoke: Array.from({ length: 8 }, (_, index) => `particle/generic_${7 - index}`),
	note: ['particle/note'],
	slime: ['item/slime_ball'],
	heart: ['particle/heart'],
	totem: Array.from({ length: 8 }, (_, index) => `particle/glitter_${7 - index}`),
	bubble: ['particle/bubble'],
	bubble_pop: Array.from({ length: 5 }, (_, index) => `particle/bubble_pop_${index}`),
	spore: ['particle/drip_fall'],
};

export class MinecraftParticleRenderer {
	private readonly group = new THREE.Group();
	private readonly emitters: Emitter[];
	private readonly particles: VisibleParticle[] = [];
	private readonly textures = new Map<ParticleTexture, THREE.Texture[]>();
	private disposed = false;

	constructor(
		parent: THREE.Group,
		definition: ParticleEmissionDefinition | null | undefined,
		assetRoot: string = ASSETS.minecraft.root,
	) {
		this.emitters = (definition?.particles ?? []).flatMap((spec): Emitter[] => {
			const type = PARTICLE_TYPES.get(spec.particle);
			if (!type) return [];
			const [fallbackMin, fallbackMax] = type.spawnIntervalTicks;
			const min = validTicks(spec.minTicks, fallbackMin);
			const max = Math.max(min, validTicks(spec.maxTicks, fallbackMax));
			return [{ spec, type, remainingMs: Math.random() * randomInterval(min, max) }];
		});
		parent.add(this.group);
		for (const kind of new Set(this.emitters.map((emitter) => emitter.type.texture)))
			void this.loadTextures(kind, assetRoot);
	}

	get active() {
		return this.emitters.length > 0 || this.particles.length > 0;
	}

	update(deltaMs: number) {
		const elapsed = Math.min(Math.max(deltaMs, 0), 100);
		for (const emitter of this.emitters) {
			emitter.remainingMs -= elapsed;
			if (emitter.remainingMs > 0) continue;
			if (this.particles.length < MAX_PARTICLES) this.spawn(emitter);
			const [fallbackMin, fallbackMax] = emitter.type.spawnIntervalTicks;
			emitter.remainingMs = randomInterval(
				validTicks(emitter.spec.minTicks, fallbackMin),
				validTicks(emitter.spec.maxTicks, fallbackMax),
			);
		}
		for (let index = this.particles.length - 1; index >= 0; index--) {
			const visible = this.particles[index];
			visible.particle.update(elapsed);
			if (visible.particle.expired) {
				this.group.remove(visible.sprite);
				visible.sprite.material.dispose();
				this.particles.splice(index, 1);
				continue;
			}
			this.sync(visible);
		}
	}

	dispose() {
		this.disposed = true;
		for (const visible of this.particles) {
			this.group.remove(visible.sprite);
			visible.sprite.material.dispose();
		}
		this.particles.length = 0;
		for (const frames of this.textures.values())
			for (const texture of frames) texture.dispose();
		this.textures.clear();
		this.group.removeFromParent();
	}

	private spawn(emitter: Emitter) {
		const { from, to } = emitter.spec;
		const position = modelSpaceToWorld(
			new THREE.Vector3(
				randomBetween(from[0], to[0]),
				randomBetween(from[1], to[1]),
				randomBetween(from[2], to[2]),
			),
		);
		const particle = new emitter.type(position);
		const texture = this.textures.get(particle.texture)?.[0];
		if (!texture) return;
		const sprite = new THREE.Sprite(
			new THREE.SpriteMaterial({
				map: texture,
				color: particle.color,
				transparent: true,
				depthWrite: false,
				blending: THREE.NormalBlending,
			}),
		);
		const visible = { particle, sprite };
		this.particles.push(visible);
		this.group.add(sprite);
		this.sync(visible);
	}

	private sync({ particle, sprite }: VisibleParticle) {
		sprite.position.copy(particle.position);
		sprite.scale.setScalar(particle.size);
		sprite.material.color.setHex(particle.color);
		sprite.material.opacity = particle.opacity;
		const frames = this.textures.get(particle.texture);
		if (frames?.length)
			sprite.material.map =
				frames[Math.min(frames.length - 1, Math.floor(particle.progress * frames.length))];
	}

	private async loadTextures(kind: ParticleTexture, assetRoot: string) {
		const root = assetRoot.replace(/\/$/, '');
		try {
			const images = await Promise.all(
				TEXTURE_FILES[kind].map((file) =>
					loadImageFromSource(`${root}/minecraft/textures/${file}.png`),
				),
			);
			if (this.disposed) return;
			this.textures.set(
				kind,
				images.map((image) => {
					const texture = new THREE.Texture(image);
					texture.colorSpace = THREE.SRGBColorSpace;
					texture.magFilter = THREE.NearestFilter;
					texture.minFilter = THREE.NearestFilter;
					texture.generateMipmaps = false;
					texture.needsUpdate = true;
					return texture;
				}),
			);
			for (const emitter of this.emitters) {
				if (emitter.type.texture === kind) emitter.remainingMs = 0;
			}
		} catch {
			// The model preview stays usable if a Minecraft asset is unavailable.
		}
	}
}

function validTicks(value: number, fallback: number) {
	return Number.isInteger(value) && value > 0 ? value : fallback;
}

function randomInterval(minTicks: number, maxTicks: number) {
	return (minTicks + Math.random() * Math.max(0, maxTicks - minTicks)) * TICK_MS;
}

function randomBetween(min: number, max: number) {
	return min + Math.random() * (max - min);
}
