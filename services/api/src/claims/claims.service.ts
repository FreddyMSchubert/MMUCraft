import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, eq, isNotNull } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import {
	claimMembers,
	claims,
	DatabaseService,
	playerProfiles,
	users,
} from '../database/database.service';
import {
	effectivePlayerColor,
	normalizeOptionalColor,
	playerAvatarUrl,
} from '../players/player-color';
import {
	ClaimMinecraftSynchronizationService,
	type ClaimsSnapshot,
} from './claim-minecraft-synchronization.service';
import { ClaimPurchasingService } from './claim-purchasing.service';

const CLAIM_NAME_MAX_LENGTH = 20;

export interface ClaimAppearanceInput {
	name?: string;
	color?: string | null;
}

export type { ClaimsSnapshot } from './claim-minecraft-synchronization.service';

@Injectable()
export class ClaimsService {
	constructor(
		private readonly database: DatabaseService,
		private readonly claimPurchasing: ClaimPurchasingService,
		private readonly minecraftSynchronization: ClaimMinecraftSynchronizationService,
	) {}

	list(user: AuthenticatedUser) {
		const people = this.getPeople();
		const peopleById = new Map(people.map((person) => [person.id, person]));
		const claimCandidateIds = new Set(
			this.database.connection
				.select({ id: users.id })
				.from(users)
				.where(isNotNull(users.minecraft_uuid))
				.all()
				.map(({ id }) => id),
		);
		const memberships = this.database.connection.select().from(claimMembers).all();
		const memberIdsByClaim = new Map<string, number[]>();

		for (const membership of memberships) {
			const ids = memberIdsByClaim.get(membership.claim_id) ?? [];
			ids.push(membership.user_id);
			memberIdsByClaim.set(membership.claim_id, ids);
		}
		const presentClaim = (claim: typeof claims.$inferSelect) => ({
			id: claim.id,
			dimension: claim.dimension,
			chunkX: claim.chunk_x,
			chunkZ: claim.chunk_z,
			name: claim.claim_name,
			color: claim.color_hex ?? peopleById.get(claim.owner_user_id)?.color ?? '#E6E6E6',
			defaultColor: peopleById.get(claim.owner_user_id)?.color ?? '#E6E6E6',
			customColor: claim.color_hex,
			members: [claim.owner_user_id, ...(memberIdsByClaim.get(claim.id) ?? [])]
				.map((userId) => peopleById.get(userId))
				.filter((person) => person !== undefined)
				.map((person) => ({
					...person,
					isOwner: person.id === claim.owner_user_id,
				})),
		});

		return {
			...this.claimPurchasing.getNextClaimPricing(user),
			claims: this.database.connection
				.select()
				.from(claims)
				.where(and(eq(claims.owner_user_id, user.id), eq(claims.is_server, 0)))
				.all()
				.map(presentClaim),
			memberClaims: this.database.connection
				.select({ claim: claims })
				.from(claimMembers)
				.innerJoin(claims, eq(claimMembers.claim_id, claims.id))
				.where(and(eq(claimMembers.user_id, user.id), eq(claims.is_server, 0)))
				.all()
				.map(({ claim }) => presentClaim(claim)),
			candidates: people.filter(
				(person) => person.id !== user.id && claimCandidateIds.has(person.id),
			),
		};
	}

	async remove(user: AuthenticatedUser, claimId: string) {
		this.requireOwnedClaim(user.id, claimId);
		this.database.connection.delete(claims).where(eq(claims.id, claimId)).run();
		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}

	async updateAppearance(user: AuthenticatedUser, claimId: string, input: ClaimAppearanceInput) {
		this.requireOwnedClaim(user.id, claimId);
		return this.saveAppearance(claimId, input);
	}

	async updateServerAppearance(claimId: string, input: ClaimAppearanceInput) {
		this.requireServerClaim(claimId);
		return this.saveAppearance(claimId, input);
	}

	private async saveAppearance(claimId: string, input: ClaimAppearanceInput) {
		const name = normalizeClaimName(input.name);
		const color = normalizeOptionalColor(input.color, 'Claim color');
		this.database.connection
			.update(claims)
			.set({ claim_name: name, color_hex: color })
			.where(eq(claims.id, claimId))
			.run();
		await this.minecraftSynchronization.synchronize();
		return { name, customColor: color };
	}

	async addMember(
		user: AuthenticatedUser,
		claimId: string,
		targetUserIdInput: number | undefined,
	) {
		const claim = this.requireOwnedClaim(user.id, claimId);
		const targetUserId = normalizeUserId(targetUserIdInput);
		if (targetUserId === claim.owner_user_id) {
			throw new BadRequestException('The claim owner already has access.');
		}

		const target = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, targetUserId))
			.get();
		if (!target?.minecraft_uuid) {
			throw new BadRequestException('Select an active server player.');
		}

		const inserted = this.database.connection
			.insert(claimMembers)
			.values({
				claim_id: claimId,
				user_id: targetUserId,
				added_at_unix_ms: Date.now(),
			})
			.onConflictDoNothing()
			.run();
		if (inserted.changes !== 1) {
			throw new ConflictException('That member already has access.');
		}

		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}

	async removeMember(user: AuthenticatedUser, claimId: string, targetUserIdInput: string) {
		this.requireOwnedClaim(user.id, claimId);
		const targetUserId = normalizeUserId(targetUserIdInput);
		const removed = this.database.connection
			.delete(claimMembers)
			.where(and(eq(claimMembers.claim_id, claimId), eq(claimMembers.user_id, targetUserId)))
			.run();
		if (removed.changes !== 1) {
			throw new NotFoundException('Claim member not found.');
		}

		await this.minecraftSynchronization.synchronize();
		return { ok: true };
	}

	getSnapshot(): ClaimsSnapshot {
		return this.minecraftSynchronization.getSnapshot();
	}

	private requireOwnedClaim(ownerUserId: number, claimId: string) {
		const claim = this.database.connection
			.select()
			.from(claims)
			.where(
				and(
					eq(claims.id, claimId),
					eq(claims.owner_user_id, ownerUserId),
					eq(claims.is_server, 0),
				),
			)
			.get();
		if (!claim) throw new NotFoundException('Claim not found.');
		return claim;
	}

	private requireServerClaim(claimId: string) {
		const claim = this.database.connection
			.select()
			.from(claims)
			.where(and(eq(claims.id, claimId), eq(claims.is_server, 1)))
			.get();
		if (!claim) throw new NotFoundException('Server claim not found.');
		return claim;
	}

	private getPeople() {
		const profilesByUserId = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		return this.database.connection
			.select()
			.from(users)
			.all()
			.map((user) => {
				const profile = profilesByUserId.get(user.id);
				const color = effectivePlayerColor(user.minecraft_uuid, profile?.color_hex);
				return {
					id: user.id,
					minecraftUsername: user.minecraft_username,
					preferredName: profile?.preferred_name ?? '',
					pronouns: profile?.pronouns ?? '',
					color,
					avatarUrl: playerAvatarUrl(user.minecraft_uuid),
					isMember: user.is_member === 1 && Boolean(user.minecraft_uuid),
				};
			});
	}
}

function normalizeClaimName(value: string | undefined) {
	if (typeof value !== 'string') throw new BadRequestException('Enter a claim name.');
	const name = value.trim();
	let hasControlCharacter = false;
	for (const character of name) {
		const codePoint = character.codePointAt(0) ?? 0;
		if (codePoint < 32 || codePoint === 127) {
			hasControlCharacter = true;
			break;
		}
	}
	if (!name || name.length > CLAIM_NAME_MAX_LENGTH || hasControlCharacter) {
		throw new BadRequestException(
			`Claim name must be 1-${CLAIM_NAME_MAX_LENGTH} characters on one line.`,
		);
	}
	return name;
}

function normalizeUserId(value: number | string | undefined) {
	const userId = typeof value === 'string' ? Number(value) : value;
	if (typeof userId !== 'number' || !Number.isInteger(userId) || userId <= 0) {
		throw new BadRequestException('Select a server player.');
	}
	return userId;
}
