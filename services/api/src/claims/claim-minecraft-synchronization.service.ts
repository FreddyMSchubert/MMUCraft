import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import {
	claimMembers,
	claims,
	DatabaseService,
	playerProfiles,
	users,
} from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { effectivePlayerColor } from '../players/player-color';

const CLAIM_SYNC_RETRY_MS = 5_000;

interface ClaimData {
	id: string;
	dimension: string;
	chunk_x: number;
	chunk_z: number;
	owner_uuid: string;
	owner_name: string;
	name: string;
	color_hex: string;
	owner_color_hex: string;
	has_custom_color: boolean;
	is_server_claim: boolean;
	member_uuids: string[];
}

export interface ClaimsSnapshot {
	claims: ClaimData[];
}

@Injectable()
export class ClaimMinecraftSynchronizationService implements OnModuleDestroy {
	private readonly logger = new Logger(ClaimMinecraftSynchronizationService.name);
	private pending = false;
	private inFlight = false;
	private retryTimer: ReturnType<typeof setTimeout> | null = null;

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	onModuleDestroy() {
		if (this.retryTimer) clearTimeout(this.retryTimer);
	}

	getSnapshot(): ClaimsSnapshot {
		const userRows = this.database.connection.select().from(users).all();
		const usersById = new Map(userRows.map((user) => [user.id, user]));
		const profilesByUserId = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		const memberUuidsByClaim = new Map<string, string[]>();
		const committeeUuids = userRows.flatMap((user) =>
			user.minecraft_uuid && (user.is_committee === 1 || user.is_super_admin === 1)
				? [user.minecraft_uuid]
				: [],
		);
		for (const membership of this.database.connection.select().from(claimMembers).all()) {
			const member = usersById.get(membership.user_id);
			if (!member?.minecraft_uuid || member.is_member !== 1) continue;
			const memberUuids = memberUuidsByClaim.get(membership.claim_id) ?? [];
			memberUuids.push(member.minecraft_uuid);
			memberUuidsByClaim.set(membership.claim_id, memberUuids);
		}
		return {
			claims: this.database.connection
				.select()
				.from(claims)
				.all()
				.flatMap((claim) => {
					const owner = usersById.get(claim.owner_user_id);
					if (!owner?.minecraft_uuid) return [];
					const ownerColor = effectivePlayerColor(
						owner.minecraft_uuid,
						profilesByUserId.get(owner.id)?.color_hex,
					);
					return [
						{
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
							is_server_claim: claim.is_server === 1,
							member_uuids:
								claim.is_server === 1
									? committeeUuids
									: (memberUuidsByClaim.get(claim.id) ?? []),
						},
					];
				}),
		};
	}

	async synchronize() {
		this.pending = true;
		if (this.inFlight) return;
		this.inFlight = true;
		try {
			while (this.pending) {
				this.pending = false;
				try {
					const response = await this.minecraft.gameplay<{ applied: boolean }>(
						'ApplyClaimsSnapshot',
						this.getSnapshot(),
					);
					if (!response.applied)
						throw new Error('Minecraft server refused the claims snapshot');
					if (this.retryTimer) clearTimeout(this.retryTimer);
					this.retryTimer = null;
				} catch (error) {
					this.pending = true;
					this.logger.warn(
						`Could not synchronize claims to Minecraft; retrying: ${String(error)}`,
					);
					this.scheduleRetry();
					return;
				}
			}
		} finally {
			this.inFlight = false;
		}
	}

	private scheduleRetry() {
		if (this.retryTimer) return;
		this.retryTimer = setTimeout(() => {
			this.retryTimer = null;
			void this.synchronize();
		}, CLAIM_SYNC_RETRY_MS);
		this.retryTimer.unref();
	}
}
