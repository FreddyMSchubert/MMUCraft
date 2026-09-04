import { Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { eq } from 'drizzle-orm';
import { DatabaseService, playerMoneyEvents, users } from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import {
	normalizeNullableInteger,
	normalizePositiveInteger,
	normalizeUnixMs,
	sanitizeEventId,
	sanitizeToken,
} from './player-statistics';
import { PlayerStatisticsSynchronizationService } from './player-statistics-synchronization.service';

@Injectable()
export class PlayerMoneyHistoryService {
	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly playerStatistics: PlayerStatisticsSynchronizationService,
	) {}

	recordForUser(
		userId: number,
		source: string,
		amountDabloonsInput: number,
		balanceDabloonsInput: number | null,
		eventIdInput?: string,
		unixMsInput?: number,
	) {
		const amountDabloons = normalizePositiveInteger(amountDabloonsInput);
		if (amountDabloons <= 0 || !this.userExists(userId)) {
			return { recorded: false, duplicate: false };
		}
		return this.recordMoneyEvent(
			userId,
			source,
			amountDabloons,
			normalizeNullableInteger(balanceDabloonsInput),
			eventIdInput,
			unixMsInput,
		);
	}

	async grantKnowledgeReadMoney(minecraftUsername: string, amountDabloons: number) {
		return await this.minecraft.gameplay<{
			granted: boolean;
			balance_dabloons: number;
			message: string;
		}>('GrantKnowledgeReadMoney', {
			minecraft_username: minecraftUsername,
			amount_dabloons: amountDabloons,
			message: `Knowledge read: you received ${amountDabloons} Dabloons.`,
		});
	}

	recordForMinecraftPlayer(
		minecraftUuidInput: string,
		minecraftUsernameInput: string,
		sourceInput: string,
		amountDabloonsInput: number,
		balanceDabloonsInput: number | null,
		referenceIdInput: string,
		unixMsInput: number | null,
	) {
		const user = this.identities.resolveAndRefresh(minecraftUuidInput, minecraftUsernameInput);
		if (!user) {
			return {
				recorded: false,
				duplicate: false,
				accountLinked: false,
				userId: null,
				message: 'No website account is linked to this Minecraft username yet.',
			};
		}

		const source = sanitizeToken(sourceInput, 'minecraft');
		const referenceId = sanitizeToken(referenceIdInput, '');
		const eventId = referenceId ? `${source}:${user.id}:${referenceId}` : randomUUID();
		const result = this.recordMoneyEvent(
			user.id,
			source,
			normalizePositiveInteger(amountDabloonsInput),
			normalizeNullableInteger(balanceDabloonsInput),
			eventId,
			normalizeUnixMs(unixMsInput),
		);

		return {
			...result,
			accountLinked: true,
			userId: user.id,
			message: result.duplicate
				? 'Dabloon event already recorded.'
				: 'Dabloon event recorded.',
		};
	}

	private recordMoneyEvent(
		userId: number,
		sourceInput: string,
		amountDabloons: number,
		balanceDabloons: number | null,
		eventIdInput?: string,
		unixMsInput?: number,
	) {
		if (amountDabloons <= 0) return { recorded: false, duplicate: false };

		const source = sanitizeToken(sourceInput, 'website');
		const eventId = sanitizeEventId(eventIdInput) ?? randomUUID();
		const unixMs = normalizeUnixMs(unixMsInput);
		return this.database.connection.transaction((tx) => {
			const inserted = tx
				.insert(playerMoneyEvents)
				.values({
					id: eventId,
					user_id: userId,
					direction: 'earned',
					source,
					amount_dabloons: amountDabloons,
					balance_dabloons: balanceDabloons,
					created_at_unix_ms: unixMs,
				})
				.onConflictDoNothing()
				.run();
			if (inserted.changes !== 1) return { recorded: false, duplicate: true };

			const stats = this.playerStatistics.getForUser(userId);
			const sourceStats = stats.money.sources[source] ?? { earnedDabloons: 0 };
			stats.money.earnedDabloons += amountDabloons;
			sourceStats.earnedDabloons += amountDabloons;
			if (balanceDabloons !== null && balanceDabloons >= 0) {
				stats.money.balanceDabloons = balanceDabloons;
			}
			stats.money.sources[source] = sourceStats;
			stats.money.lastUpdatedAtUnixMs = unixMs;
			this.playerStatistics.saveForUser(userId, stats, unixMs);
			return { recorded: true, duplicate: false };
		});
	}

	private userExists(userId: number): boolean {
		return Boolean(
			this.database.connection
				.select({ id: users.id })
				.from(users)
				.where(eq(users.id, userId))
				.get(),
		);
	}
}
