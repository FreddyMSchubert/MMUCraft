import { BadRequestException, ConflictException, Injectable, Logger, NotFoundException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import * as grpc from '@grpc/grpc-js'
import { and, asc, eq } from 'drizzle-orm'
import { AuthenticatedUser } from '../auth/auth.service'
import {
	claimMembers,
	claims,
	DatabaseService,
	playerProfiles,
	users,
} from '../database/database.service'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { callUnary } from '../grpc/grpc.types'
import { effectivePlayerColor, normalizeOptionalColor, playerAvatarUrl } from '../players/player-color'

const CLAIM_BASE_PRICE_DABLOONS = 100
const MEMBER_CLAIM_PRICE_GROWTH = 1.42
const NORMAL_PLAYER_CLAIM_PRICE_GROWTH = 1.69
const MAX_CLAIM_PRICE_DABLOONS = 2_000_000_000
const CLAIM_NAME_MAX_LENGTH = 20
const MAX_CHUNK_COORDINATE = 1_875_000
const ADMIN_PAGE_SIZE = 42
const ADMIN_MAX_PAGE_SIZE = 100

interface ClaimData {
	id: string
	dimension: string
	chunk_x: number
	chunk_z: number
	owner_uuid: string
	owner_name: string
	name: string
	color_hex: string
	owner_color_hex: string
	has_custom_color: boolean
	member_uuids: string[]
}

export interface ClaimsSnapshot {
	claims: ClaimData[]
}

interface CurrentChunkResponse {
	online: boolean
	dimension: string
	chunk_x: number
	chunk_z: number
	balance_dabloons: number
	message: string
}

interface PurchaseClaimResponse {
	purchased: boolean
	online: boolean
	balance_dabloons: number
	message: string
}

interface GameplayControlClient extends grpc.Client {
	GetCurrentClaimChunk(
		request: { minecraft_username: string },
		callback: (error: grpc.ServiceError | null, response: CurrentChunkResponse) => void,
	): void
	PurchaseClaim(
		request: { minecraft_username: string; dimension: string; chunk_x: number; chunk_z: number; price_dabloons: number },
		callback: (error: grpc.ServiceError | null, response: PurchaseClaimResponse) => void,
	): void
	ApplyClaimsSnapshot(
		request: ClaimsSnapshot,
		callback: (error: grpc.ServiceError | null, response: { applied: boolean }) => void,
	): void
}

interface GameplayProtoRoot {
	mcstack: {
		gameplay: {
			v1: {
				GameplayControl: grpc.ServiceClientConstructor
			}
		}
	}
}

@Injectable()
export class ClaimsService {
	private readonly logger = new Logger(ClaimsService.name)
	private gameplayControlClient: GameplayControlClient | null = null

	constructor(
		private readonly database: DatabaseService,
		private readonly grpcServer: GrpcServerService,
	) { }

	list(user: AuthenticatedUser) {
		const people = this.getPeople()
		const peopleById = new Map(people.map((person) => [person.id, person]))
		const memberships = this.database.connection.select().from(claimMembers).all()
		const memberIdsByClaim = new Map<string, number[]>()

		for (const membership of memberships) {
			const ids = memberIdsByClaim.get(membership.claim_id) ?? []
			ids.push(membership.user_id)
			memberIdsByClaim.set(membership.claim_id, ids)
		}

		return {
			...this.getNextClaimPricing(user),
			claims: this.database.connection.select().from(claims)
				.where(eq(claims.owner_user_id, user.id)).all()
				.map((claim) => ({
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
						.map((person) => ({ ...person, isOwner: person.id === claim.owner_user_id })),
				})),
			candidates: people.filter((person) => person.id !== user.id && person.isMember),
		}
	}

	listAdmin(offsetInput: string | undefined, limitInput: string | undefined) {
		const { offset, limit } = normalizePagination(offsetInput, limitInput)
		const rows = this.database.connection.select({
			id: claims.id,
			name: claims.claim_name,
			dimension: claims.dimension,
			chunkX: claims.chunk_x,
			chunkZ: claims.chunk_z,
			minecraftUsername: users.minecraft_username,
			minecraftUuid: users.minecraft_uuid,
			color: playerProfiles.color_hex,
		}).from(claims)
			.innerJoin(users, eq(users.id, claims.owner_user_id))
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
			.orderBy(asc(users.minecraft_username), asc(claims.dimension), asc(claims.chunk_x), asc(claims.chunk_z))
			.limit(limit + 1)
			.offset(offset)
			.all()

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
		}
	}

	async getCurrentChunk(user: AuthenticatedUser) {
		const response = await this.callMod<CurrentChunkResponse>('GetCurrentClaimChunk', {
			minecraft_username: user.minecraftUsername,
		}).catch(() => null)

		if (!response?.online) {
			throw new BadRequestException(response?.message || 'You have to be online on the server to claim a chunk.')
		}

		return {
			dimension: response.dimension,
			chunkX: response.chunk_x,
			chunkZ: response.chunk_z,
			balanceDabloons: response.balance_dabloons,
			...this.getNextClaimPricing(user),
		}
	}

	async create(user: AuthenticatedUser, input: Record<string, unknown>) {
		const dimension = normalizeDimension(input.dimension)
		const chunkX = normalizeChunkCoordinate(input.chunkX)
		const chunkZ = normalizeChunkCoordinate(input.chunkZ)
		const { priceDabloons } = this.getNextClaimPricing(user)
		const claimId = randomUUID()
		const inserted = this.database.connection.insert(claims).values({
			id: claimId,
			owner_user_id: user.id,
			dimension,
			chunk_x: chunkX,
			chunk_z: chunkZ,
			created_at_unix_ms: Date.now(),
		}).onConflictDoNothing().run()

		if (inserted.changes !== 1) {
			throw new ConflictException('That chunk has already been claimed.')
		}

		let purchase: PurchaseClaimResponse
		try {
			purchase = await this.callMod<PurchaseClaimResponse>('PurchaseClaim', {
				minecraft_username: user.minecraftUsername,
				dimension,
				chunk_x: chunkX,
				chunk_z: chunkZ,
				price_dabloons: priceDabloons,
			})
		} catch (error) {
			this.database.connection.delete(claims).where(eq(claims.id, claimId)).run()
			throw new BadRequestException('You have to stay online in that chunk while buying the claim.')
		}

		if (!purchase.purchased) {
			this.database.connection.delete(claims).where(eq(claims.id, claimId)).run()
			throw new BadRequestException(purchase.message || 'The claim could not be purchased.')
		}

		await this.alertMod()
		return {
			created: true,
			claimId,
			balanceDabloons: purchase.balance_dabloons,
			message: purchase.message,
		}
	}

	async remove(user: AuthenticatedUser, claimId: string) {
		this.requireOwnedClaim(user.id, claimId)
		this.database.connection.delete(claims).where(eq(claims.id, claimId)).run()
		await this.alertMod()
		return { ok: true }
	}

	async removeAdmin(claimId: string) {
		const removed = this.database.connection.delete(claims).where(eq(claims.id, claimId)).run()
		if (removed.changes !== 1) throw new NotFoundException('Claim not found.')
		await this.alertMod()
		return { ok: true }
	}

	async updateAppearance(user: AuthenticatedUser, claimId: string, input: Record<string, unknown>) {
		this.requireOwnedClaim(user.id, claimId)
		const name = normalizeClaimName(input.name)
		const color = normalizeOptionalColor(input.color, 'Claim color')
		this.database.connection.update(claims).set({ claim_name: name, color_hex: color })
			.where(eq(claims.id, claimId)).run()
		await this.alertMod()
		return { name, customColor: color }
	}

	async addMember(user: AuthenticatedUser, claimId: string, targetUserIdInput: unknown) {
		const claim = this.requireOwnedClaim(user.id, claimId)
		const targetUserId = normalizeUserId(targetUserIdInput)
		if (targetUserId === claim.owner_user_id) {
			throw new BadRequestException('The claim owner already has access.')
		}

		const target = this.database.connection.select().from(users)
			.where(and(eq(users.id, targetUserId), eq(users.is_member, 1))).get()
		if (!target?.minecraft_uuid) {
			throw new BadRequestException('Select an active server member.')
		}

		const inserted = this.database.connection.insert(claimMembers).values({
			claim_id: claimId,
			user_id: targetUserId,
			added_at_unix_ms: Date.now(),
		}).onConflictDoNothing().run()
		if (inserted.changes !== 1) {
			throw new ConflictException('That member already has access.')
		}

		await this.alertMod()
		return { ok: true }
	}

	async removeMember(user: AuthenticatedUser, claimId: string, targetUserIdInput: string) {
		this.requireOwnedClaim(user.id, claimId)
		const targetUserId = normalizeUserId(targetUserIdInput)
		const removed = this.database.connection.delete(claimMembers)
			.where(and(eq(claimMembers.claim_id, claimId), eq(claimMembers.user_id, targetUserId))).run()
		if (removed.changes !== 1) {
			throw new NotFoundException('Claim member not found.')
		}

		await this.alertMod()
		return { ok: true }
	}

	getSnapshot(): ClaimsSnapshot {
		const userRows = this.database.connection.select().from(users).all()
		const usersById = new Map(userRows.map((user) => [user.id, user]))
		const profilesByUserId = new Map(this.database.connection.select().from(playerProfiles).all()
			.map((profile) => [profile.user_id, profile]))
		const memberships = this.database.connection.select().from(claimMembers).all()
		const memberUuidsByClaim = new Map<string, string[]>()

		for (const membership of memberships) {
			const member = usersById.get(membership.user_id)
			if (!member?.minecraft_uuid || member.is_member !== 1) continue
			const memberUuids = memberUuidsByClaim.get(membership.claim_id) ?? []
			memberUuids.push(member.minecraft_uuid)
			memberUuidsByClaim.set(membership.claim_id, memberUuids)
		}

		return {
			claims: this.database.connection.select().from(claims).all().flatMap((claim) => {
				const owner = usersById.get(claim.owner_user_id)
				if (!owner?.minecraft_uuid) return []
				const ownerColor = effectivePlayerColor(owner.minecraft_uuid, profilesByUserId.get(owner.id)?.color_hex)
				return [{
					id: claim.id,
					dimension: claim.dimension,
					chunk_x: claim.chunk_x,
					chunk_z: claim.chunk_z,
					owner_uuid: owner.minecraft_uuid,
					owner_name: owner.minecraft_username,
					name: claim.claim_name,
					color_hex: claim.color_hex ?? ownerColor,
					owner_color_hex: ownerColor,
					has_custom_color: claim.color_hex !== null,
					member_uuids: memberUuidsByClaim.get(claim.id) ?? [],
				}]
			}),
		}
	}

	private async pushSnapshot() {
		const response = await this.callMod<{ applied: boolean }>('ApplyClaimsSnapshot', this.getSnapshot())
		if (!response.applied) {
			throw new Error('Minecraft server refused the claims snapshot')
		}
	}

	private async alertMod() {
		try {
			await this.pushSnapshot()
		} catch (error) {
			this.logger.warn(`Could not immediately synchronize claims to Minecraft: ${String(error)}`)
		}
	}

	private requireOwnedClaim(ownerUserId: number, claimId: string) {
		const claim = this.database.connection.select().from(claims)
			.where(and(eq(claims.id, claimId), eq(claims.owner_user_id, ownerUserId))).get()
		if (!claim) throw new NotFoundException('Claim not found.')
		return claim
	}

	private getNextClaimPricing(user: AuthenticatedUser) {
		const nextClaimNumber = this.database.connection.select().from(claims)
			.where(eq(claims.owner_user_id, user.id)).all().length + 1
		const memberPriceDabloons = claimPriceDabloons(nextClaimNumber, MEMBER_CLAIM_PRICE_GROWTH)
		const normalPlayerPriceDabloons = claimPriceDabloons(nextClaimNumber, NORMAL_PLAYER_CLAIM_PRICE_GROWTH)
		return {
			isMember: user.isMember,
			nextClaimNumber,
			memberPriceDabloons,
			normalPlayerPriceDabloons,
			priceDabloons: user.isMember ? memberPriceDabloons : normalPlayerPriceDabloons,
		}
	}

	private callMod<T>(methodName: 'GetCurrentClaimChunk' | 'PurchaseClaim' | 'ApplyClaimsSnapshot', request: object) {
		return callUnary<T>(this.getGameplayControlClient(), methodName, request)
	}

	private getGameplayControlClient() {
		if (this.gameplayControlClient) return this.gameplayControlClient
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		) as unknown as GameplayControlClient
		return this.gameplayControlClient
	}

	private getPeople() {
		const profilesByUserId = new Map(this.database.connection.select().from(playerProfiles).all()
			.map((profile) => [profile.user_id, profile]))
		return this.database.connection.select().from(users).all().map((user) => {
			const profile = profilesByUserId.get(user.id)
			const color = effectivePlayerColor(user.minecraft_uuid, profile?.color_hex)
			return {
				id: user.id,
				minecraftUsername: user.minecraft_username,
				preferredName: profile?.preferred_name ?? '',
				pronouns: profile?.pronouns ?? '',
				color,
				avatarUrl: playerAvatarUrl(user.minecraft_uuid),
				isMember: user.is_member === 1 && Boolean(user.minecraft_uuid),
			}
		})
	}
}

function claimPriceDabloons(claimNumber: number, growth: number) {
	return Math.min(MAX_CLAIM_PRICE_DABLOONS, Math.round(CLAIM_BASE_PRICE_DABLOONS * growth ** (claimNumber - 1)))
}

function normalizeClaimName(value: unknown) {
	if (typeof value !== 'string') throw new BadRequestException('Enter a claim name.')
	const name = value.trim()
	if (!name || name.length > CLAIM_NAME_MAX_LENGTH || /[\u0000-\u001f\u007f]/.test(name)) {
		throw new BadRequestException(`Claim name must be 1-${CLAIM_NAME_MAX_LENGTH} characters on one line.`)
	}
	return name
}

function normalizeDimension(value: unknown) {
	if (typeof value !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value)) {
		throw new BadRequestException('Invalid Minecraft dimension.')
	}
	return value
}

function normalizeChunkCoordinate(value: unknown) {
	if (typeof value !== 'number' || !Number.isInteger(value) || Math.abs(value) > MAX_CHUNK_COORDINATE) {
		throw new BadRequestException('Invalid chunk coordinate.')
	}
	return value
}

function normalizeUserId(value: unknown) {
	const userId = typeof value === 'string' ? Number(value) : value
	if (typeof userId !== 'number' || !Number.isInteger(userId) || userId <= 0) {
		throw new BadRequestException('Select a server member.')
	}
	return userId
}

function normalizePagination(offsetInput: string | undefined, limitInput: string | undefined) {
	const offset = offsetInput === undefined ? 0 : Number(offsetInput)
	const limit = limitInput === undefined ? ADMIN_PAGE_SIZE : Number(limitInput)
	if (!Number.isInteger(offset) || offset < 0 || !Number.isInteger(limit) || limit < 1 || limit > ADMIN_MAX_PAGE_SIZE) {
		throw new BadRequestException(`Pagination requires a non-negative offset and a limit from 1 to ${ADMIN_MAX_PAGE_SIZE}.`)
	}
	return { offset, limit }
}
