import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { and, count, eq } from 'drizzle-orm';
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
				response?.message ??
					'Join the Minecraft server and stand in the chunk you want to claim, then try again.',
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
		const { priceDabloons, nextClaimNumber } = this.getNextClaimPricing(user);
		const claim = this.insertClaim(
			user,
			input,
			false,
			`My ${formatOrdinal(nextClaimNumber)} Claim`,
		);

		let purchase: PurchaseClaimResponse;
		try {
			purchase = await this.minecraft.gameplay<PurchaseClaimResponse>('PurchaseClaim', {
				minecraft_username: user.minecraftUsername,
				dimension: claim.dimension,
				chunk_x: claim.chunkX,
				chunk_z: claim.chunkZ,
				price_dabloons: priceDabloons,
			});
		} catch {
			this.database.connection.delete(claims).where(eq(claims.id, claim.id)).run();
			throw new BadRequestException(
				'You have to stay online in that chunk while buying the claim.',
			);
		}

		if (!purchase.purchased) {
			this.database.connection.delete(claims).where(eq(claims.id, claim.id)).run();
			throw new BadRequestException(purchase.message || 'The claim could not be purchased.');
		}

		await this.minecraftSynchronization.synchronize();
		return {
			created: true,
			claimId: claim.id,
			balanceDabloons: purchase.balance_dabloons,
			message: purchase.message,
		};
	}

	async createServerClaim(user: AuthenticatedUser, input: CreateClaimInput) {
		const claim = this.insertClaim(user, input, true, 'Server claim');
		await this.minecraftSynchronization.synchronize();
		return { created: true, claimId: claim.id };
	}

	getNextClaimPricing(user: AuthenticatedUser) {
		const claimCount =
			this.database.connection
				.select({ value: count() })
				.from(claims)
				.where(and(eq(claims.owner_user_id, user.id), eq(claims.is_server, 0)))
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

	private insertClaim(
		user: AuthenticatedUser,
		input: CreateClaimInput,
		isServer: boolean,
		claimName: string,
	) {
		const claim = {
			id: randomUUID(),
			dimension: normalizeDimension(input.dimension),
			chunkX: normalizeChunkCoordinate(input.chunkX),
			chunkZ: normalizeChunkCoordinate(input.chunkZ),
		};
		const inserted = this.database.connection
			.insert(claims)
			.values({
				id: claim.id,
				owner_user_id: user.id,
				dimension: claim.dimension,
				chunk_x: claim.chunkX,
				chunk_z: claim.chunkZ,
				claim_name: claimName,
				is_server: isServer ? 1 : 0,
				created_at_unix_ms: Date.now(),
			})
			.onConflictDoNothing()
			.run();
		if (inserted.changes !== 1) {
			throw new ConflictException('That chunk has already been claimed.');
		}
		return claim;
	}
}

function claimPriceDabloons(claimNumber: number, growth: number) {
	return Math.min(
		MAX_CLAIM_PRICE_DABLOONS,
		Math.round(CLAIM_BASE_PRICE_DABLOONS * growth ** (claimNumber - 1)),
	);
}

function formatOrdinal(value: number) {
	const lastTwoDigits = value % 100;
	const suffix =
		lastTwoDigits >= 11 && lastTwoDigits <= 13
			? 'th'
			: (['th', 'st', 'nd', 'rd'][value % 10] ?? 'th');
	return `${value}${suffix}`;
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
