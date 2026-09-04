import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, playerProfiles, users } from '../database/database.service';
import { ClaimMinecraftSynchronizationService } from '../claims/claim-minecraft-synchronization.service';
import { effectivePlayerColor } from '../players/player-color';
import { PlayersService } from '../players/players.service';

@Injectable()
export class PlayerRoleAdministrationService {
	constructor(
		private readonly database: DatabaseService,
		private readonly claims: ClaimMinecraftSynchronizationService,
		private readonly players: PlayersService,
	) {}

	listPlayers() {
		const rows = this.database.connection
			.select({
				id: users.id,
				minecraft_username: users.minecraft_username,
				email: users.email,
				is_member: users.is_member,
				is_committee: users.is_committee,
				is_super_admin: users.is_super_admin,
				responsible_user_id: users.responsible_user_id,
				minecraft_uuid: users.minecraft_uuid,
				discord_username: playerProfiles.discord_username,
				color_hex: playerProfiles.color_hex,
			})
			.from(users)
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
			.all()
			.sort((left, right) =>
				left.minecraft_username.localeCompare(right.minecraft_username, 'en', {
					sensitivity: 'base',
				}),
			);

		return {
			players: rows.map((row) => ({
				id: row.id,
				minecraftUsername: row.minecraft_username,
				color: effectivePlayerColor(row.minecraft_uuid, row.color_hex),
				discordUsername: row.discord_username ?? '',
				email: row.email,
				isMember: row.is_member === 1,
				isCommittee: row.is_super_admin === 1 || row.is_committee === 1,
				isExternal: row.responsible_user_id !== null,
			})),
		};
	}

	async setMembership(userIdInput: string, isMember: boolean | undefined) {
		const userId = parseUserId(userIdInput);
		if (typeof isMember !== 'boolean') {
			throw new BadRequestException('isMember must be a boolean');
		}

		const updated = this.database.connection
			.update(users)
			.set({ is_member: isMember ? 1 : 0 })
			.where(eq(users.id, userId))
			.run();

		if (updated.changes !== 1) {
			throw new NotFoundException('Player not found');
		}
		await this.players.synchronizePlayerPresentation(userId);
		return { ok: true, userId, isMember };
	}

	async setCommittee(userIdInput: string, isCommittee: boolean | undefined) {
		const userId = parseUserId(userIdInput);
		if (typeof isCommittee !== 'boolean') {
			throw new BadRequestException('isCommittee must be a boolean');
		}

		const target = this.database.connection
			.select({ is_super_admin: users.is_super_admin })
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!target) {
			throw new NotFoundException('Player not found');
		}
		if (target.is_super_admin === 1 && !isCommittee) {
			throw new BadRequestException(
				'The permanent super-admin cannot be removed from committee',
			);
		}

		this.database.connection
			.update(users)
			.set({ is_committee: isCommittee ? 1 : 0 })
			.where(eq(users.id, userId))
			.run();
		await this.players.synchronizePlayerPresentation(userId);
		await this.claims.synchronize();
		return { ok: true, userId, isCommittee };
	}
}

function parseUserId(input: string) {
	const userId = Number(input);
	if (!Number.isInteger(userId) || userId <= 0) {
		throw new NotFoundException('Player not found');
	}
	return userId;
}
