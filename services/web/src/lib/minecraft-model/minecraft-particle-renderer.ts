import * as THREE from 'three';
import { modelSpaceToWorld, TICK_MS } from './minecraft-model-geometry';
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

export class MinecraftParticleRenderer {
	private readonly group = new THREE.Group();
	private readonly emitters: Emitter[];
	private readonly particles: VisibleParticle[] = [];
	private readonly textures = new Map<ParticleTexture, THREE.CanvasTexture>();

	constructor(parent: THREE.Group, definition: ParticleEmissionDefinition | null | undefined) {
		this.emitters = (definition?.particles ?? []).flatMap((spec): Emitter[] => {
			const type = PARTICLE_TYPES.get(spec.particle);
			if (!type) return [];
			const [fallbackMin, fallbackMax] = type.spawnIntervalTicks;
			const min = validTicks(spec.minTicks, fallbackMin);
			const max = Math.max(min, validTicks(spec.maxTicks, fallbackMax));
			return [{ spec, type, remainingMs: Math.random() * randomInterval(min, max) }];
		});
		parent.add(this.group);
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
		for (const visible of this.particles) {
			this.group.remove(visible.sprite);
			visible.sprite.material.dispose();
		}
		this.particles.length = 0;
		for (const texture of this.textures.values()) texture.dispose();
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
		const sprite = new THREE.Sprite(
			new THREE.SpriteMaterial({
				map: this.texture(particle.texture),
				color: particle.color,
				transparent: true,
				depthWrite: false,
				blending:
					particle.texture === 'flame' ? THREE.AdditiveBlending : THREE.NormalBlending,
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
	}

	private texture(kind: ParticleTexture) {
		const cached = this.textures.get(kind);
		if (cached) return cached;
		const canvas = document.createElement('canvas');
		canvas.width = 16;
		canvas.height = 16;
		const context = canvas.getContext('2d');
		if (context) {
			context.fillStyle = '#fff';
			if (kind === 'note') {
				context.fillRect(8, 2, 2, 9);
				context.fillRect(10, 2, 3, 2);
				context.fillRect(11, 4, 2, 2);
				context.fillRect(5, 9, 5, 3);
				context.fillRect(4, 10, 2, 3);
			} else {
				context.fillStyle = 'rgba(255,255,255,0.63)';
				for (const [y, x, width] of [
					[1, 8, 2],
					[2, 7, 3],
					[3, 7, 4],
					[4, 6, 5],
					[5, 5, 7],
					[6, 5, 7],
					[7, 4, 8],
					[8, 4, 9],
					[9, 3, 10],
					[10, 4, 9],
					[11, 4, 8],
					[12, 5, 7],
					[13, 5, 6],
				] as [number, number, number][]) {
					context.fillRect(x, y, width, 1);
				}
				context.fillStyle = '#fff';
				context.fillRect(7, 6, 3, 6);
				context.fillRect(6, 9, 5, 3);
			}
		}
		const texture = new THREE.CanvasTexture(canvas);
		texture.magFilter = THREE.NearestFilter;
		texture.minFilter = THREE.NearestFilter;
		texture.colorSpace = THREE.SRGBColorSpace;
		this.textures.set(kind, texture);
		return texture;
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
