import * as THREE from 'three';

export type ParticleTexture = 'flame' | 'note';

/** Simulation state. Rendering is handled separately by MinecraftParticleRenderer. */
export abstract class MinecraftParticle {
	readonly position: THREE.Vector3;
	readonly id: string;
	readonly texture: ParticleTexture;
	readonly lifetimeMs: number;
	ageMs = 0;
	size: number;
	color: number;
	opacity = 1;

	protected constructor(
		id: string,
		texture: ParticleTexture,
		position: THREE.Vector3,
		lifetimeMs: number,
		size: number,
		color: number,
	) {
		this.id = id;
		this.texture = texture;
		this.position = position;
		this.lifetimeMs = lifetimeMs;
		this.size = size;
		this.color = color;
	}

	update(deltaMs: number) {
		this.ageMs = Math.min(this.lifetimeMs, this.ageMs + deltaMs);
	}

	get expired() {
		return this.ageMs >= this.lifetimeMs;
	}

	get progress() {
		return this.ageMs / this.lifetimeMs;
	}
}

export class FlameParticle extends MinecraftParticle {
	static readonly id = 'minecraft:flame';
	static readonly spawnIntervalTicks = [2, 4] as const;
	private readonly initialSize: number;
	private readonly driftX: number;
	private readonly driftZ: number;

	constructor(position: THREE.Vector3) {
		const size = 0.12 + Math.random() * 0.055;
		super(
			FlameParticle.id,
			'flame',
			position,
			550 + Math.random() * 260,
			size,
			[0xffc85a, 0xffa43a, 0xffdf79][Math.floor(Math.random() * 3)],
		);
		this.initialSize = size;
		this.driftX = (Math.random() - 0.5) * 0.022;
		this.driftZ = (Math.random() - 0.5) * 0.022;
	}

	override update(deltaMs: number) {
		super.update(deltaMs);
		const seconds = deltaMs / 1000;
		this.position.y += seconds * 0.12;
		this.position.x += seconds * this.driftX;
		this.position.z += seconds * this.driftZ;
		this.size =
			this.initialSize *
			(1 - this.progress * 0.64) *
			(1 + Math.sin(this.ageMs * 0.025) * 0.06);
		this.opacity = Math.min(1, (1 - this.progress) * 1.7);
	}
}

export class NoteParticle extends MinecraftParticle {
	static readonly id = 'minecraft:note';
	static readonly spawnIntervalTicks = [12, 28] as const;
	private readonly originX: number;
	private readonly phase = Math.random() * Math.PI * 2;

	constructor(position: THREE.Vector3) {
		const color = new THREE.Color().setHSL(Math.random(), 0.88, 0.66);
		super(NoteParticle.id, 'note', position, 1100 + Math.random() * 450, 0.2, color.getHex());
		this.originX = position.x;
	}

	override update(deltaMs: number) {
		super.update(deltaMs);
		this.position.y += (deltaMs / 1000) * 0.34;
		this.position.x = this.originX + Math.sin(this.ageMs * 0.006 + this.phase) * 0.035;
		this.opacity = Math.min(1, (1 - this.progress) * 3);
	}
}

export interface MinecraftParticleConstructor {
	new (position: THREE.Vector3): MinecraftParticle;
	id: string;
	spawnIntervalTicks: readonly [number, number];
}

export const PARTICLE_TYPES = new Map<string, MinecraftParticleConstructor>(
	[FlameParticle, NoteParticle].map((type) => [type.id, type]),
);
