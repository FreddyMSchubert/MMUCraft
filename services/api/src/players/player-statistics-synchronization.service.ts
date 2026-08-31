import { Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, playerStats } from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { effectivePlayerColor } from './player-color';
import { PlayerProfileStorageService } from './player-profile-storage.service';
import { PROFILE_TEXT_LIMITS } from './player-profile';
import {
	defaultStats,
	normalizeMinecraftStat,
	normalizeNullableInteger,
	normalizeStatsJson,
	normalizeUnixMs,
	type MinecraftStatInput,
	type MinecraftStatValue,
	type PlayerStats,
} from './player-statistics';

@Injectable()
export class PlayerStatisticsSynchronizationService {
	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly profiles: PlayerProfileStorageService,
	) {}

	synchronizeFromMinecraft(
		minecraftUuidInput: string,
		minecraftUsernameInput: string,
		statsInput: MinecraftStatInput[],
		balanceDabloonsInput: number | null,
		unixMsInput: number | null,
	) {
		const user = this.identities.resolveAndRefresh(minecraftUuidInput, minecraftUsernameInput);
		if (!user) {
			return {
				accepted: false,
				accountLinked: false,
				isMember: false,
				isCommittee: false,
				isExternal: false,
				nickname: '',
				pronouns: '',
				color: effectivePlayerColor(minecraftUuidInput),
				showDeathCounter: true,
				previousLastPlayedAtUnixMs: 0,
				message: 'No website account is linked to this Minecraft username yet.',
			};
		}

		const unixMs = normalizeUnixMs(unixMsInput);
		const stats = this.getForUser(user.id);
		const previousLastPlayedAtUnixMs = stats.minecraft.lastPlayedAtUnixMs ?? 0;
		const nextMinecraftStats: Record<string, MinecraftStatValue> = { ...stats.minecraft.stats };
		for (const statInput of statsInput) {
			const stat = normalizeMinecraftStat(statInput, unixMs);
			if (stat) nextMinecraftStats[stat.key] = stat;
		}

		stats.minecraft = {
			stats: nextMinecraftStats,
			lastSyncedAtUnixMs: unixMs,
			lastPlayedAtUnixMs: unixMs,
		};
		const balanceDabloons = normalizeNullableInteger(balanceDabloonsInput);
		if (balanceDabloons !== null && balanceDabloons >= 0) {
			stats.money.balanceDabloons = balanceDabloons;
			stats.money.lastUpdatedAtUnixMs = unixMs;
		}
		this.saveForUser(user.id, stats, unixMs);

		const profile = this.profiles.get(user.id);
		return {
			accepted: true,
			accountLinked: true,
			isMember: user.is_member === 1,
			isCommittee: user.is_super_admin === 1 || user.is_committee === 1,
			isExternal: user.responsible_user_id !== null,
			nickname: profile.preferredName.slice(0, PROFILE_TEXT_LIMITS.preferredName),
			pronouns: profile.pronouns.slice(0, PROFILE_TEXT_LIMITS.pronouns),
			color: profile.color,
			showDeathCounter: profile.showDeathCounter,
			previousLastPlayedAtUnixMs,
			message: 'Stats synced.',
		};
	}

	getForUser(userId: number): PlayerStats {
		const row = this.database.connection
			.select()
			.from(playerStats)
			.where(eq(playerStats.user_id, userId))
			.get();
		return row ? normalizeStatsJson(row.stats_json) : defaultStats();
	}

	saveForUser(userId: number, stats: PlayerStats, unixMs = Date.now()) {
		const values = {
			user_id: userId,
			stats_json: JSON.stringify(stats),
			updated_at_unix_ms: unixMs,
		};
		this.database.connection
			.insert(playerStats)
			.values(values)
			.onConflictDoUpdate({ target: playerStats.user_id, set: values })
			.run();
	}
}
