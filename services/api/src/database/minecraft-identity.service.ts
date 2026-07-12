import { Injectable, Logger } from '@nestjs/common'
import { eq, sql } from 'drizzle-orm'
import { DatabaseService, UserRow, users } from './database.service'

const MINECRAFT_UUID_PATTERN = /^[0-9a-f]{32}$/
const MINECRAFT_USERNAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/

@Injectable()
export class MinecraftIdentityService {
	private readonly logger = new Logger(MinecraftIdentityService.name)

	constructor(private readonly database: DatabaseService) { }

	findByUuid(uuidInput: string): UserRow | null {
		const uuid = normalizeMinecraftUuid(uuidInput)
		if (!uuid) return null
		return this.database.connection.select().from(users)
			.where(eq(users.minecraft_uuid, uuid)).get() ?? null
	}

	/**
	 * Resolves a stable Minecraft identity and refreshes its display username.
	 * Username fallback is permitted only for legacy rows that do not have a UUID yet.
	 */
	resolveAndRefresh(uuidInput: string, usernameInput: string): UserRow | null {
		const uuid = normalizeMinecraftUuid(uuidInput)
		const username = normalizeMinecraftUsername(usernameInput)

		if (uuid) {
			const byUuid = this.findByUuid(uuid)
			if (byUuid) {
				this.refreshUsername(byUuid, username)
				return this.findByUuid(uuid)
			}

			if (!username) return null
			const legacy = this.findLegacyByUsername(username)
			if (!legacy) return null

			try {
				this.database.connection.update(users)
					.set({ minecraft_uuid: uuid, minecraft_username: username })
					.where(eq(users.id, legacy.id))
					.run()
				return this.findByUuid(uuid)
			} catch (error) {
				this.logger.warn(`Could not attach Minecraft UUID ${uuid} to legacy user ${legacy.id}: ${errorMessage(error)}`)
				return null
			}
		}

		// Backwards compatibility for an older Minecraft mod during a rolling deployment.
		return username ? this.findByUsername(username) : null
	}

	private refreshUsername(user: UserRow, username: string | null) {
		if (!username || user.minecraft_username === username) return

		const conflict = this.findByUsername(username)
		if (conflict && conflict.id !== user.id) {
			this.logger.warn(`Refused Minecraft username refresh for UUID ${user.minecraft_uuid}: ${username} belongs to user ${conflict.id}`)
			return
		}

		try {
			this.database.connection.update(users)
				.set({ minecraft_username: username })
				.where(eq(users.id, user.id))
				.run()
		} catch (error) {
			this.logger.warn(`Could not refresh Minecraft username for user ${user.id}: ${errorMessage(error)}`)
		}
	}

	private findLegacyByUsername(username: string): UserRow | null {
		const row = this.findByUsername(username)
		return row && !row.minecraft_uuid ? row : null
	}

	private findByUsername(username: string): UserRow | null {
		return this.database.connection.select().from(users)
			.where(sql`lower(${users.minecraft_username}) = ${username.toLowerCase()}`)
			.get() ?? null
	}
}

export function normalizeMinecraftUuid(value: string): string | null {
	const normalized = value.trim().toLowerCase().replaceAll('-', '')
	return MINECRAFT_UUID_PATTERN.test(normalized) ? normalized : null
}

function normalizeMinecraftUsername(value: string): string | null {
	const normalized = value.trim()
	return MINECRAFT_USERNAME_PATTERN.test(normalized) ? normalized : null
}

function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : String(error)
}
