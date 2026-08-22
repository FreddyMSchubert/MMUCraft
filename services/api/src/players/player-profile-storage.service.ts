import { Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, playerProfiles, users } from '../database/database.service';
import { effectivePlayerColor } from './player-color';
import type { PlayerProfile } from './player-profile';

@Injectable()
export class PlayerProfileStorageService {
	constructor(private readonly database: DatabaseService) {}

	get(userId: number): PlayerProfile {
		const row = this.database.connection
			.select()
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, userId))
			.get();
		const minecraftUuid =
			this.database.connection
				.select({ value: users.minecraft_uuid })
				.from(users)
				.where(eq(users.id, userId))
				.get()?.value ?? null;
		if (!row)
			return {
				preferredName: '',
				pronouns: '',
				courseYear: '',
				discordUsername: '',
				base: { x: null, y: null, z: null },
				bio: '',
				color: effectivePlayerColor(minecraftUuid),
				defaultColor: effectivePlayerColor(minecraftUuid),
				customColor: null,
				showDeathCounter: true,
				updatedAtUnixMs: 0,
			};
		return {
			preferredName: row.preferred_name,
			pronouns: row.pronouns,
			courseYear: row.course_year,
			discordUsername: row.discord_username,
			base: { x: row.base_x, y: row.base_y, z: row.base_z },
			bio: row.bio,
			color: effectivePlayerColor(minecraftUuid, row.color_hex),
			defaultColor: effectivePlayerColor(minecraftUuid),
			customColor: row.color_hex,
			showDeathCounter: row.show_death_counter === 1,
			updatedAtUnixMs: row.updated_at_unix_ms,
		};
	}
}
