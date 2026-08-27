import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, asc, eq, isNull } from 'drizzle-orm';
import {
	DatabaseService,
	emailWhitelist,
	playerProfiles,
	users,
} from '../database/database.service';
import { effectivePlayerColor } from '../players/player-color';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import type { AuthenticatedUser } from './auth-session.service';
import { isAllowedEmail, isValidEmail, normalizeEmail } from './auth.util';
import { PlayerBansService } from './player-bans.service';

export const MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 150;
export const NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 250;

@Injectable()
export class AuthAccountAdministrationService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly bans: PlayerBansService,
	) {}

	listPlayerBans() {
		return this.bans.list();
	}

	applyPlayerBan(
		admin: AuthenticatedUser,
		userIdInput: number | undefined,
		expiresAtUnixMsInput: number | null | undefined,
	) {
		if (!Number.isInteger(userIdInput) || Number(userIdInput) <= 0)
			throw new BadRequestException('Select a player');
		if (
			expiresAtUnixMsInput !== null &&
			(!Number.isSafeInteger(expiresAtUnixMsInput) ||
				Number(expiresAtUnixMsInput) <= Date.now())
		)
			throw new BadRequestException('Select a timeout date and time in the future');

		const userId = Number(userIdInput);
		const expiresAtUnixMs = expiresAtUnixMsInput ?? null;
		const target = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!target) throw new NotFoundException('Player not found');
		if (target.id === admin.id)
			throw new BadRequestException('You cannot ban your own account');
		if (target.is_super_admin === 1)
			throw new BadRequestException('The permanent super-admin cannot be banned');

		this.bans.set(target.id, admin.id, expiresAtUnixMs);
		return {
			ok: true,
			userId: target.id,
			minecraftUsername: target.minecraft_username,
			expiresAtUnixMs,
		};
	}

	removePlayerBan(userIdInput: string) {
		const userId = Number(userIdInput);
		if (!Number.isInteger(userId) || userId <= 0)
			throw new NotFoundException('Player not found');
		const target = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!target || !this.bans.remove(userId))
			throw new NotFoundException('Active ban not found');
		return {
			ok: true,
			userId,
			minecraftUsername: target.minecraft_username,
		};
	}

	listEmailWhitelist() {
		const profilesById = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		const usernamesById = new Map(
			this.database.connection
				.select({
					id: users.id,
					minecraftUsername: users.minecraft_username,
					minecraftUuid: users.minecraft_uuid,
				})
				.from(users)
				.all()
				.map((user) => [
					user.id,
					{
						name: user.minecraftUsername,
						color: effectivePlayerColor(
							user.minecraftUuid,
							profilesById.get(user.id)?.color_hex,
						),
					},
				]),
		);
		return {
			entries: this.database.connection
				.select()
				.from(emailWhitelist)
				.orderBy(asc(emailWhitelist.email))
				.all()
				.map((entry) => ({
					email: entry.email,
					addedByMinecraftUsername:
						usernamesById.get(entry.added_by_user_id)?.name ?? 'Unknown user',
					addedByColor: usernamesById.get(entry.added_by_user_id)?.color ?? '#E6E6E6',
					responsibleMinecraftUsername:
						entry.responsible_user_id === null
							? null
							: (usernamesById.get(entry.responsible_user_id)?.name ??
								'Unknown user'),
					responsiblePlayerColor:
						entry.responsible_user_id === null
							? null
							: (usernamesById.get(entry.responsible_user_id)?.color ?? '#E6E6E6'),
					createdAtUnixMs: entry.created_at_unix_ms,
				})),
		};
	}

	async addEmailToWhitelist(
		admin: AuthenticatedUser,
		emailInput: string | undefined,
		responsibleUserIdInput: number | undefined,
	) {
		const email = normalizeEmail(emailInput ?? '');
		if (!isValidEmail(email)) throw new BadRequestException('Enter a valid email address');
		if (isAllowedEmail(email))
			throw new BadRequestException('MMU email addresses are already allowed');
		if (!Number.isInteger(responsibleUserIdInput) || Number(responsibleUserIdInput) <= 0)
			throw new BadRequestException('Select a responsible user');

		const responsibleUserId = Number(responsibleUserIdInput);
		const responsibleUser = this.database.connection
			.select()
			.from(users)
			.where(and(eq(users.id, responsibleUserId), isNull(users.responsible_user_id)))
			.get();
		if (!responsibleUser)
			throw new BadRequestException(
				'External players cannot be responsible for another external player',
			);

		const result = this.database.connection
			.insert(emailWhitelist)
			.values({
				email,
				added_by_user_id: admin.id,
				responsible_user_id: responsibleUserId,
				created_at_unix_ms: Date.now(),
			})
			.onConflictDoNothing()
			.run();
		if (result.changes !== 1)
			throw new ConflictException('That email address is already whitelisted');

		try {
			const priceDabloons =
				responsibleUser.is_member === 1
					? MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS
					: NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS;
			const purchase = await this.minecraft.gameplay<{
				purchased: boolean;
				balance_dabloons: number;
				message: string;
			}>('PurchaseExternalPlayerInvite', {
				minecraft_username: responsibleUser.minecraft_username,
			});
			if (!purchase.purchased)
				throw new BadRequestException(
					purchase.message ||
						`The responsible player must be online with ${priceDabloons} dabloons`,
				);
			return { email, priceDabloons, balanceDabloons: purchase.balance_dabloons };
		} catch (error) {
			this.database.connection
				.delete(emailWhitelist)
				.where(eq(emailWhitelist.email, email))
				.run();
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'The responsible player must be online to pay for this invitation',
			);
		}
	}

	removeEmailFromWhitelist(emailInput: string) {
		const email = normalizeEmail(emailInput);
		const result = this.database.connection
			.delete(emailWhitelist)
			.where(eq(emailWhitelist.email, email))
			.run();
		if (result.changes !== 1)
			throw new NotFoundException('Whitelisted email address not found');
		return { ok: true };
	}
}
