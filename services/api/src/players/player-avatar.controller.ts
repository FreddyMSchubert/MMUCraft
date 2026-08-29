import {
	BadGatewayException,
	BadRequestException,
	Controller,
	Get,
	Header,
	NotFoundException,
	Param,
	Res,
	StreamableFile,
} from '@nestjs/common';
import type { FastifyReply } from 'fastify';
import { eq } from 'drizzle-orm';
import sharp from 'sharp';
import { DatabaseService, playerProfiles, users } from '../database/database.service';
import { effectivePlayerColor } from './player-color';
import { fetchMinecraftProfileByUuid } from './minecraft-profile-fetcher';

const AVATAR_CACHE_SECONDS = 25 * 60 * 60;
const AVATAR_CACHE_MS = AVATAR_CACHE_SECONDS * 1000;
const AVATAR_CACHE_SIZE = 1_000;
const MAX_SKIN_BYTES = 1_000_000;
const FETCH_TIMEOUT_MS = 5_000;

interface CachedAsset<T> {
	expiresAt: number;
	value: Promise<T>;
}

interface PlayerSkin {
	model: 'classic' | 'legacy' | 'slim';
	png: Buffer;
}

@Controller('api')
export class PlayerAvatarController {
	// ponytail: This cache is process-local. Use a shared cache if the API runs multiple replicas.
	private readonly skins = new Map<string, CachedAsset<PlayerSkin>>();
	private readonly heads = new Map<string, CachedAsset<Buffer>>();
	private readonly discordAvatars = new Map<string, CachedAsset<Buffer>>();

	constructor(private readonly database: DatabaseService) {}

	@Get('players/avatar/:uuid.png')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', `public, max-age=${AVATAR_CACHE_SECONDS}, must-revalidate`)
	async playerAvatar(@Param('uuid') uuidInput: string) {
		const uuid = this.normalizeUuid(uuidInput);
		const user = this.findUser(uuid);
		if (!user) throw new NotFoundException('Player avatar not found');
		return new StreamableFile(await this.getHead(uuid, user.minecraft_username));
	}

	@Get('players/skin/:uuid.png')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', `public, max-age=${AVATAR_CACHE_SECONDS}, must-revalidate`)
	async playerSkin(
		@Param('uuid') uuidInput: string,
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		const uuid = this.normalizeUuid(uuidInput);
		const user = this.findUser(uuid);
		if (!user) throw new NotFoundException('Player skin not found');
		const skin = await this.getSkin(uuid, user.minecraft_username);
		response.header('X-Minecraft-Skin-Model', skin.model);
		return new StreamableFile(skin.png);
	}

	@Get('discord/avatar/:uuid.png')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', `public, max-age=${AVATAR_CACHE_SECONDS}, must-revalidate`)
	async discordAvatar(@Param('uuid') uuidInput: string) {
		const uuid = this.normalizeUuid(uuidInput);
		const user = this.findUser(uuid);
		const profile = user
			? this.database.connection
					.select()
					.from(playerProfiles)
					.where(eq(playerProfiles.user_id, user.id))
					.get()
			: null;
		const color = effectivePlayerColor(uuid, profile?.color_hex).slice(1);
		const png = await this.getCached(this.discordAvatars, `${uuid}:${color}`, async () => {
			return this.addDiscordRing(
				await this.getHead(uuid, user?.minecraft_username ?? ''),
				color,
			);
		});
		return new StreamableFile(png);
	}

	private findUser(uuid: string) {
		return this.database.connection
			.select()
			.from(users)
			.where(eq(users.minecraft_uuid, uuid))
			.get();
	}

	private normalizeUuid(input: string) {
		const uuid = input.replaceAll('-', '').toLowerCase();
		if (!/^[0-9a-f]{32}$/.test(uuid)) throw new BadRequestException('Invalid Minecraft UUID');
		return uuid;
	}

	private getHead(uuid: string, fallbackName: string) {
		return this.getCached(this.heads, uuid, async () =>
			this.generateHead((await this.getSkin(uuid, fallbackName)).png),
		);
	}

	private getSkin(uuid: string, fallbackName: string) {
		return this.getCached(this.skins, uuid, () => this.generateSkin(uuid, fallbackName));
	}

	private async getCached<T>(
		cache: Map<string, CachedAsset<T>>,
		key: string,
		generate: () => Promise<T>,
	) {
		const cached = cache.get(key);
		if (cached && cached.expiresAt > Date.now()) return cached.value;
		if (cached) cache.delete(key);
		if (cache.size >= AVATAR_CACHE_SIZE) {
			const oldestKey = cache.keys().next().value;
			if (oldestKey !== undefined) cache.delete(oldestKey);
		}

		const value = generate().catch((error: unknown) => {
			cache.delete(key);
			if (error instanceof BadGatewayException) throw error;
			throw new BadGatewayException('The Minecraft player image could not be generated');
		});
		cache.set(key, { expiresAt: Date.now() + AVATAR_CACHE_MS, value });
		return value;
	}

	private async generateSkin(uuid: string, fallbackName: string): Promise<PlayerSkin> {
		const signal = AbortSignal.timeout(FETCH_TIMEOUT_MS);
		const profile = await fetchMinecraftProfileByUuid(uuid, fallbackName, signal);
		const skinUrl = profile.skinUrl;
		if (!skinUrl) throw new BadGatewayException('Mojang did not return a player skin');
		const url = new URL(skinUrl);
		if (url.protocol !== 'https:' || url.hostname !== 'textures.minecraft.net') {
			throw new BadGatewayException('Mojang returned an invalid player skin URL');
		}
		const response = await fetch(url, { signal });
		if (!response.ok)
			throw new BadGatewayException(
				'The Minecraft avatar service did not return a player skin',
			);
		const png = await readLimitedBody(response, MAX_SKIN_BYTES);
		const metadata = await sharp(png).metadata();
		if (
			metadata.format !== 'png' ||
			metadata.width !== 64 ||
			(metadata.height !== 32 && metadata.height !== 64)
		) {
			throw new BadGatewayException('Mojang returned an invalid player skin');
		}
		return {
			model:
				metadata.height === 32 ? 'legacy' : profile.model === 'slim' ? 'slim' : 'classic',
			png,
		};
	}

	private async generateHead(png: Buffer) {
		const skin = sharp(png);
		const metadata = await skin.metadata();
		if (metadata.width < 48 || metadata.height < 16) {
			throw new BadGatewayException('Mojang returned an invalid player skin');
		}
		const face = await skin
			.clone()
			.extract({ left: 8, top: 8, width: 8, height: 8 })
			.png()
			.toBuffer();
		const hat = await skin
			.clone()
			.extract({ left: 40, top: 8, width: 8, height: 8 })
			.png()
			.toBuffer();
		return sharp(face)
			.composite([{ input: hat }])
			.resize(128, 128, { kernel: 'nearest' })
			.png()
			.toBuffer();
	}

	private async addDiscordRing(head: Buffer, color: string) {
		const ring = Buffer.from(
			`<svg width="128" height="128" xmlns="http://www.w3.org/2000/svg"><circle cx="64" cy="64" r="64" fill="none" stroke="#${color}" stroke-width="20"/></svg>`,
		);
		const mask = Buffer.from(
			'<svg width="128" height="128" xmlns="http://www.w3.org/2000/svg"><circle cx="64" cy="64" r="64" fill="white"/></svg>',
		);
		const playerHead = await sharp(head)
			.resize(108, 108, { kernel: 'nearest' })
			.png()
			.toBuffer();
		return sharp({
			create: {
				width: 128,
				height: 128,
				channels: 4,
				background: { r: 0, g: 0, b: 0, alpha: 0 },
			},
		})
			.composite([
				{ input: playerHead, left: 10, top: 10 },
				{ input: ring },
				{ input: mask, blend: 'dest-in' },
			])
			.png()
			.toBuffer();
	}
}

async function readLimitedBody(response: Response, limit: number) {
	const contentLength = Number(response.headers.get('content-length'));
	if (Number.isFinite(contentLength) && contentLength > limit) {
		await response.body?.cancel();
		throw new BadGatewayException('The Minecraft avatar response was too large');
	}
	if (!response.body) throw new BadGatewayException('The Minecraft avatar response was empty');

	const reader = response.body.getReader();
	const chunks: Buffer[] = [];
	let size = 0;
	for (;;) {
		const { done, value } = await reader.read();
		if (done) break;
		size += value.byteLength;
		if (size > limit) {
			await reader.cancel();
			throw new BadGatewayException('The Minecraft avatar response was too large');
		}
		chunks.push(Buffer.from(value));
	}
	return Buffer.concat(chunks, size);
}
