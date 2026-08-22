import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { count, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import { claims, DatabaseService } from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { ClaimMinecraftSynchronizationService } from './claim-minecraft-synchronization.service';

const CLAIM_BASE_PRICE_DABLOONS = 100;
const MEMBER_CLAIM_PRICE_GROWTH = 1.42;
const NORMAL_PLAYER_CLAIM_PRICE_GROWTH = 1.69;
const MAX_CLAIM_PRICE_DABLOONS = 2_000_000_000;
const MAX_CHUNK_COORDINATE = 1_875_000;

export interface CreateClaimInput {
	dimension?: string;
	chunkX?: number;
	chunkZ?: number;
}

interface CurrentChunkResponse {
	online: boolean;
	dimension: string;
	chunk_x: number;
	chunk_z: number;
	balance_dabloons: number;
	message: string;
}

interface PurchaseClaimResponse {
	purchased: boolean;
	online: boolean;
	balance_dabloons: number;
	message: string;
}

@Injectable()
export class ClaimPurchasingService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly minecraftSynchronization: ClaimMinecraftSynchronizationService,
	) {}

	async getCurrentChunk(user: AuthenticatedUser) {
		const response = await this.minecraft
			.gameplay<CurrentChunkResponse>('GetCurrentClaimChunk', {
				minecraft_username: user.minecraftUsername,
			})
			.catch(() => null);
		if (!response?.online) {
			throw new BadRequestException(
				response?.message ?? 'You have to be online on the server to claim a chunk.',
			);
		}

		return {
			dimension: response.dimension,
			chunkX: response.chunk_x,
			chunkZ: response.chunk_z,
			balanceDabloons: response.balance_dabloons,
			...this.getNextClaimPricing(user),
		};
	}

	async create(user: AuthenticatedUser, input: CreateClaimInput) {
		const dimension = normalizeDimension(input.dimension);
		const chunkX = normalizeChunkCoordinate(input.chunkX);
		const chunkZ = normalizeChunkCoordinate(input.chunkZ);
		const { priceDabloons } = this.getNextClaimPricing(user);
		const claimId = randomUUID();
		const inserted = this.database.connection
			.insert(claims)
			.values({
				id: claimId,
				owner_user_id: user.id,
				dimension,
				chunk_x: chunkX,
				chunk_z: chunkZ,
				created_at_unix_ms: Date.now(),
			})
			.onConflictDoNothing()
			.run();
		if (inserted.changes !== 1) {
			throw new ConflictException('That chunk has already been claimed.');
		}

		let purchase: PurchaseClaimResponse;
		try {
			purchase = await this.minecraft.gameplay<PurchaseClaimResponse>('PurchaseClaim', {
				minecraft_username: user.minecraftUsername,
				dimension,
				chunk_x: chunkX,
				chunk_z: chunkZ,
				price_dabloons: priceDabloons,
			});
		} catch {
			this.database.connection.delete(claims).where(eq(claims.id, claimId)).run();
			throw new BadRequestException(
				'You have to stay online in that chunk while buying the claim.',
			);
		}

		if (!purchase.purchased) {
			this.database.connection.delete(claims).where(eq(claims.id, claimId)).run();
			throw new BadRequestException(purchase.message || 'The claim could not be purchased.');
		}

		await this.minecraftSynchronization.synchronize();
		return {
			created: true,
			claimId,
			balanceDabloons: purchase.balance_dabloons,
			message: purchase.message,
		};
	}

	getNextClaimPricing(user: AuthenticatedUser) {
		const claimCount =
			this.database.connection
				.select({ value: count() })
				.from(claims)
				.where(eq(claims.owner_user_id, user.id))
				.get()?.value ?? 0;
		const nextClaimNumber = claimCount + 1;
		const memberPriceDabloons = claimPriceDabloons(nextClaimNumber, MEMBER_CLAIM_PRICE_GROWTH);
		const normalPlayerPriceDabloons = claimPriceDabloons(
			nextClaimNumber,
			NORMAL_PLAYER_CLAIM_PRICE_GROWTH,
		);
		return {
			isMember: user.isMember,
			nextClaimNumber,
			memberPriceDabloons,
			normalPlayerPriceDabloons,
			priceDabloons: user.isMember ? memberPriceDabloons : normalPlayerPriceDabloons,
		};
	}
}

function claimPriceDabloons(claimNumber: number, growth: number) {
	return Math.min(
		MAX_CLAIM_PRICE_DABLOONS,
		Math.round(CLAIM_BASE_PRICE_DABLOONS * growth ** (claimNumber - 1)),
	);
}

function normalizeDimension(value: string | undefined) {
	if (typeof value !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value)) {
		throw new BadRequestException('Invalid Minecraft dimension.');
	}
	return value;
}

function normalizeChunkCoordinate(value: number | undefined) {
	if (
		typeof value !== 'number' ||
		!Number.isInteger(value) ||
		Math.abs(value) > MAX_CHUNK_COORDINATE
	) {
		throw new BadRequestException('Invalid chunk coordinate.');
	}
	return value;
}
