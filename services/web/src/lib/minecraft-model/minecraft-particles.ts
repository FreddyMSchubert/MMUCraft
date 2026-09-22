import * as THREE from 'three';

export type ParticleTexture =
	'flame' | 'smoke' | 'note' | 'slime' | 'heart' | 'totem' | 'bubble' | 'bubble_pop' | 'spore';

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
	static readonly texture = 'flame';
	static readonly spawnIntervalTicks = [2, 4] as const;
	private readonly initialSize: number;
	private readonly driftX: number;
	private readonly driftZ: number;

	constructor(position: THREE.Vector3) {
		const size = 0.12 + Math.random() * 0.055;
		super(FlameParticle.id, 'flame', position, 550 + Math.random() * 260, size, 0xffffff);
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
	}
}

export class SmokeParticle extends MinecraftParticle {
	static readonly id = 'minecraft:smoke';
	static readonly texture = 'smoke';
	static readonly spawnIntervalTicks = [14, 30] as const;
	constructor(position: THREE.Vector3) {
		super(SmokeParticle.id, 'smoke', position, 900, 0.17, 0xbababa);
	}

	override update(deltaMs: number) {
		super.update(deltaMs);
		this.position.y += (deltaMs / 1000) * 0.12;
		this.opacity = 1 - this.progress;
	}
}

export class NoteParticle extends MinecraftParticle {
	static readonly id = 'minecraft:note';
	static readonly texture = 'note';
	static readonly spawnIntervalTicks = [12, 28] as const;
	private readonly originX: number;
	private readonly phase = Math.random() * Math.PI * 2;

	constructor(position: THREE.Vector3) {
		const hue = Math.random();
		const channel = (phase: number) =>
			Math.max(0, Math.sin((hue + phase) * Math.PI * 2) * 0.65 + 0.35);
		const color = new THREE.Color().setRGB(channel(0), channel(1 / 3), channel(2 / 3));
		super(NoteParticle.id, 'note', position, 300, 0.2, color.getHex());
		this.originX = position.x;
	}

	override update(deltaMs: number) {
		super.update(deltaMs);
		this.position.y += (deltaMs / 1000) * 0.34;
		this.position.x = this.originX + Math.sin(this.ageMs * 0.006 + this.phase) * 0.035;
	}
}

class DriftParticle extends MinecraftParticle {
	private readonly velocity: THREE.Vector3;

	constructor(
		id: string,
		texture: ParticleTexture,
		position: THREE.Vector3,
		lifetimeMs: number,
		size: number,
		color: number,
		rise: number,
	) {
		super(id, texture, position, lifetimeMs, size, color);
		this.velocity = new THREE.Vector3(
			(Math.random() - 0.5) * 0.06,
			rise,
			(Math.random() - 0.5) * 0.06,
		);
	}

	override update(deltaMs: number) {
		super.update(deltaMs);
		this.position.addScaledVector(this.velocity, deltaMs / 1000);
	}
}

export class SlimeParticle extends DriftParticle {
	static readonly id = 'minecraft:item_slime';
	static readonly texture = 'slime';
	static readonly spawnIntervalTicks = [20, 80] as const;
	constructor(position: THREE.Vector3) {
		super(SlimeParticle.id, 'slime', position, 850, 0.13, 0xffffff, -0.08);
	}
}

export class HeartParticle extends DriftParticle {
	static readonly id = 'minecraft:heart';
	static readonly texture = 'heart';
	static readonly spawnIntervalTicks = [35, 80] as const;
	constructor(position: THREE.Vector3) {
		super(HeartParticle.id, 'heart', position, 800, 0.23, 0xffffff, 0.18);
	}
}

export class TotemParticle extends DriftParticle {
	static readonly id = 'minecraft:totem_of_undying';
	static readonly texture = 'totem';
	static readonly spawnIntervalTicks = [160, 320] as const;
	constructor(position: THREE.Vector3) {
		super(
			TotemParticle.id,
			'totem',
			position,
			3000 + Math.random() * 600,
			0.12,
			Math.random() < 0.5 ? 0xffe66b : 0x75e786,
			-0.06,
		);
	}
}

export class BubbleParticle extends DriftParticle {
	static readonly id = 'minecraft:bubble';
	static readonly texture = 'bubble';
	static readonly spawnIntervalTicks = [35, 80] as const;
	constructor(position: THREE.Vector3) {
		super(BubbleParticle.id, 'bubble', position, 1000, 0.16, 0xffffff, 0.22);
	}
}

export class BubblePopParticle extends DriftParticle {
	static readonly id = 'minecraft:bubble_pop';
	static readonly texture = 'bubble_pop';
	static readonly spawnIntervalTicks = [80, 160] as const;
	constructor(position: THREE.Vector3) {
		super(BubblePopParticle.id, 'bubble_pop', position, 200, 0.18, 0xffffff, 0.02);
	}
}

export class SporeParticle extends DriftParticle {
	static readonly id = 'minecraft:spore_blossom_air';
	static readonly texture = 'spore';
	static readonly spawnIntervalTicks = [80, 160] as const;
	constructor(position: THREE.Vector3) {
		const color = new THREE.Color().setRGB(0.32, 0.5, 0.22);
		super(
			SporeParticle.id,
			'spore',
			position,
			25000 + Math.random() * 25000,
			0.11,
			color.getHex(),
			-0.04,
		);
	}
}

export interface MinecraftParticleConstructor {
	new (position: THREE.Vector3): MinecraftParticle;
	id: string;
	texture: ParticleTexture;
	spawnIntervalTicks: readonly [number, number];
}

export const PARTICLE_TYPES = new Map<string, MinecraftParticleConstructor>(
	[
		FlameParticle,
		SmokeParticle,
		NoteParticle,
		SlimeParticle,
		HeartParticle,
		TotemParticle,
		BubbleParticle,
		BubblePopParticle,
		SporeParticle,
	].map((type) => [type.id, type]),
);
