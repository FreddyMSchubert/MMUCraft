import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { asc, eq } from 'drizzle-orm';
import { claims, DatabaseService, playerProfiles, users } from '../database/database.service';
import { effectivePlayerColor } from '../players/player-color';
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
			})
			.from(claims)
			.innerJoin(users, eq(users.id, claims.owner_user_id))
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
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
			})),
			hasMore: rows.length > limit,
		};
	}

	async remove(claimId: string) {
		const removed = this.database.connection.delete(claims).where(eq(claims.id, claimId)).run();
		if (removed.changes !== 1) throw new NotFoundException('Claim not found.');
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
