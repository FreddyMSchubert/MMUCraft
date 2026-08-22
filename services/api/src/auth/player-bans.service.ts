import { Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import {
	DatabaseService,
	playerBans,
	playerProfiles,
	sessions,
	users,
} from '../database/database.service';
import { effectivePlayerColor } from '../players/player-color';

export interface PlayerBanState {
	active: boolean;
	expired: boolean;
	expiresAtUnixMs: number | null;
}

@Injectable()
export class PlayerBansService {
	constructor(private readonly database: DatabaseService) {}

	list() {
		const userRows = this.database.connection.select().from(users).all();
		const usersById = new Map(userRows.map((user) => [user.id, user]));
		const profilesById = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);

		return {
			bans: this.database.connection
				.select()
				.from(playerBans)
				.all()
				.sort((left, right) => right.created_at_unix_ms - left.created_at_unix_ms)
				.map((ban) => {
					const player = usersById.get(ban.user_id);
					const admin = usersById.get(ban.banned_by_user_id);
					return {
						userId: ban.user_id,
						minecraftUsername: player?.minecraft_username ?? 'Unknown player',
						color: effectivePlayerColor(
							player?.minecraft_uuid ?? null,
							profilesById.get(ban.user_id)?.color_hex,
						),
						bannedByMinecraftUsername: admin?.minecraft_username ?? 'Unknown user',
						expiresAtUnixMs: ban.expires_at_unix_ms,
						createdAtUnixMs: ban.created_at_unix_ms,
					};
				}),
		};
	}

	set(userId: number, bannedByUserId: number, expiresAtUnixMs: number | null, now = Date.now()) {
		this.database.connection.transaction((tx) => {
			tx.insert(playerBans)
				.values({
					user_id: userId,
					banned_by_user_id: bannedByUserId,
					expires_at_unix_ms: expiresAtUnixMs,
					created_at_unix_ms: now,
				})
				.onConflictDoUpdate({
					target: playerBans.user_id,
					set: {
						banned_by_user_id: bannedByUserId,
						expires_at_unix_ms: expiresAtUnixMs,
						created_at_unix_ms: now,
					},
				})
				.run();
			tx.delete(sessions).where(eq(sessions.user_id, userId)).run();
		});
	}

	remove(userId: number) {
		return (
			this.database.connection.delete(playerBans).where(eq(playerBans.user_id, userId)).run()
				.changes === 1
		);
	}

	resolve(userId: number, now = Date.now()): PlayerBanState {
		const ban = this.database.connection
			.select()
			.from(playerBans)
			.where(eq(playerBans.user_id, userId))
			.get();
		if (!ban) return { active: false, expired: false, expiresAtUnixMs: null };

		if (ban.expires_at_unix_ms !== null && ban.expires_at_unix_ms <= now) {
			this.remove(userId);
			return { active: false, expired: true, expiresAtUnixMs: ban.expires_at_unix_ms };
		}

		return { active: true, expired: false, expiresAtUnixMs: ban.expires_at_unix_ms };
	}
}
