import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, asc, eq } from 'drizzle-orm';
import { claims, DatabaseService, playerProfiles, users } from '../database/database.service';
import { effectivePlayerColor } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';
import { type ServerClaimAreaInput, overlappingClaim, serverClaimArea } from './claim-area';
import { ClaimMinecraftSynchronizationService } from './claim-minecraft-synchronization.service';

const ADMIN_PAGE_SIZE = 42;
const ADMIN_MAX_PAGE_SIZE = 100;

@Injectable()
export class ClaimAdministrationService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraftSynchronization: ClaimMinecraftSynchronizationService,
	) {}

	list(offsetInput: string | undefined, limitInput: string | undefined) {
		const { offset, limit } = normalizePagination(offsetInput, limitInput);
		const rows = this.database.connection
			.select({
				id: claims.id,
				name: claims.claim_name,
				dimension: claims.dimension,
				chunkX: claims.chunk_x,
				chunkZ: claims.chunk_z,
				minecraftUsername: users.minecraft_username,
				minecraftUuid: users.minecraft_uuid,
				color: playerProfiles.color_hex,
				customEmojisJson: playerProfiles.custom_emojis_json,
				isMember: users.is_member,
				isCommittee: users.is_committee,
				isSuperAdmin: users.is_super_admin,
			})
			.from(claims)
			.innerJoin(users, eq(users.id, claims.owner_user_id))
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
			.where(eq(claims.is_server, 0))
			.orderBy(
				asc(users.minecraft_username),
				asc(claims.dimension),
				asc(claims.chunk_x),
				asc(claims.chunk_z),
			)
			.limit(limit + 1)
			.offset(offset)
			.all();

		return {
			claims: rows.slice(0, limit).map((claim) => ({
				id: claim.id,
				name: claim.name,
				dimension: claim.dimension,
				chunkX: claim.chunkX,
				chunkZ: claim.chunkZ,
				minecraftUsername: claim.minecraftUsername,
				color: effectivePlayerColor(claim.minecraftUuid, claim.color),
				isCommittee: claim.isSuperAdmin === 1 || claim.isCommittee === 1,
				customEmojis: customPlayerEmojis(claim.customEmojisJson),
			})),
			hasMore: rows.length > limit,
		};
	}

	async remove(claimId: string) {
		const removed = this.database.connection
			.delete(claims)
			.where(and(eq(claims.id, claimId), eq(claims.is_server, 0)))
			.run();
		if (removed.changes !== 1) throw new NotFoundException('Claim not found.');
		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}

	listServerClaims() {
		return {
			claims: this.database.connection
				.select({
					id: claims.id,
					name: claims.claim_name,
					dimension: claims.dimension,
					chunkX: claims.chunk_x,
					chunkZ: claims.chunk_z,
					chunkXEnd: claims.chunk_x_end,
					chunkZEnd: claims.chunk_z_end,
					customColor: claims.color_hex,
					permissions: claims.outsider_permissions,
					minecraftUuid: users.minecraft_uuid,
					ownerColor: playerProfiles.color_hex,
				})
				.from(claims)
				.innerJoin(users, eq(users.id, claims.owner_user_id))
				.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
				.where(eq(claims.is_server, 1))
				.orderBy(asc(claims.dimension), asc(claims.chunk_x), asc(claims.chunk_z))
				.all()
				.map((claim) => {
					const defaultColor = effectivePlayerColor(
						claim.minecraftUuid,
						claim.ownerColor,
					);
					return {
						id: claim.id,
						name: claim.name,
						dimension: claim.dimension,
						chunkX: claim.chunkX,
						chunkZ: claim.chunkZ,
						chunkXEnd: claim.chunkXEnd,
						chunkZEnd: claim.chunkZEnd,
						color: claim.customColor ?? defaultColor,
						defaultColor,
						customColor: claim.customColor,
						permissions: claim.permissions,
					};
				}),
		};
	}

	async updateServerArea(claimId: string, input: ServerClaimAreaInput) {
		const area = serverClaimArea(input);
		this.database.connection.transaction((tx) => {
			const existing = tx
				.select({ id: claims.id })
				.from(claims)
				.where(and(eq(claims.id, claimId), eq(claims.is_server, 1)))
				.get();
			if (!existing) throw new NotFoundException('Server claim not found.');
			if (
				tx
					.select({ id: claims.id })
					.from(claims)
					.where(overlappingClaim(area, claimId))
					.get()
			) {
				throw new ConflictException('The selected area overlaps an existing claim.');
			}
			tx.update(claims)
				.set({
					dimension: area.dimension,
					chunk_x: area.chunkX,
					chunk_z: area.chunkZ,
					chunk_x_end: area.chunkXEnd,
					chunk_z_end: area.chunkZEnd,
				})
				.where(eq(claims.id, claimId))
				.run();
		});
		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}

	async removeServerClaim(claimId: string) {
		const removed = this.database.connection
			.delete(claims)
			.where(and(eq(claims.id, claimId), eq(claims.is_server, 1)))
			.run();
		if (removed.changes !== 1) throw new NotFoundException('Server claim not found.');
		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}
}

function normalizePagination(offsetInput: string | undefined, limitInput: string | undefined) {
	const offset = offsetInput === undefined ? 0 : Number(offsetInput);
	const limit = limitInput === undefined ? ADMIN_PAGE_SIZE : Number(limitInput);
	if (
		!Number.isInteger(offset) ||
		offset < 0 ||
		!Number.isInteger(limit) ||
		limit < 1 ||
		limit > ADMIN_MAX_PAGE_SIZE
	) {
		throw new BadRequestException(
			`Pagination requires a non-negative offset and a limit from 1 to ${ADMIN_MAX_PAGE_SIZE}.`,
		);
	}
	return { offset, limit };
}
