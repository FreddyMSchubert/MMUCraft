import { randomUUID } from 'node:crypto'
import { dirname } from 'node:path'
import { mkdirSync } from 'node:fs'
import Database from 'better-sqlite3'

export interface AccountRecord {
	id: string
	minecraftUuid: string
	minecraftUsername: string
	email: string
	discordUserId: string
	discordUsername: string
	discordGlobalName: string | null
	discordAvatar: string | null
	createdAt: string
	updatedAt: string
}

export interface WebSessionRecord {
	id: string
	accountId: string
	createdAt: string
	expiresAt: string
	lastSeenAt: string
}

interface AccountRow {
	id: string
	minecraft_uuid: string
	minecraft_username: string
	email: string
	discord_user_id: string
	discord_username: string
	discord_global_name: string | null
	discord_avatar: string | null
	created_at: string
	updated_at: string
}

export class AccountStore {
	private readonly db: Database.Database

	constructor(databasePath: string) {
		mkdirSync(dirname(databasePath), { recursive: true })

		this.db = new Database(databasePath)
		this.db.pragma('journal_mode = WAL')
		this.db.pragma('foreign_keys = ON')
		this.migrate()
	}

	private migrate(): void {
		this.db.exec(`
      CREATE TABLE IF NOT EXISTS accounts (
        id TEXT PRIMARY KEY,
        minecraft_uuid TEXT NOT NULL UNIQUE,
        minecraft_username TEXT NOT NULL,
        email TEXT NOT NULL UNIQUE,
        discord_user_id TEXT NOT NULL UNIQUE,
        discord_username TEXT NOT NULL,
        discord_global_name TEXT,
        discord_avatar TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS web_sessions (
        id TEXT PRIMARY KEY,
        account_id TEXT NOT NULL,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        last_seen_at TEXT NOT NULL,
        FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
      );

      CREATE INDEX IF NOT EXISTS idx_web_sessions_account_id ON web_sessions(account_id);
      CREATE INDEX IF NOT EXISTS idx_web_sessions_expires_at ON web_sessions(expires_at);
    `)
	}

	private toAccount(row: AccountRow | undefined): AccountRecord | null {
		if (!row) return null

		return {
			id: row.id,
			minecraftUuid: row.minecraft_uuid,
			minecraftUsername: row.minecraft_username,
			email: row.email,
			discordUserId: row.discord_user_id,
			discordUsername: row.discord_username,
			discordGlobalName: row.discord_global_name,
			discordAvatar: row.discord_avatar,
			createdAt: row.created_at,
			updatedAt: row.updated_at,
		}
	}

	getAccountByMinecraftUuid(playerUuid: string): AccountRecord | null {
		const row = this.db
			.prepare('SELECT * FROM accounts WHERE minecraft_uuid = ? LIMIT 1')
			.get(playerUuid) as AccountRow | undefined

		return this.toAccount(row)
	}

	getAccountByDiscordUserId(discordUserId: string): AccountRecord | null {
		const row = this.db
			.prepare('SELECT * FROM accounts WHERE discord_user_id = ? LIMIT 1')
			.get(discordUserId) as AccountRow | undefined

		return this.toAccount(row)
	}

	getAccountByEmail(email: string): AccountRecord | null {
		const row = this.db
			.prepare('SELECT * FROM accounts WHERE lower(email) = lower(?) LIMIT 1')
			.get(email) as AccountRow | undefined

		return this.toAccount(row)
	}

	getAccountById(id: string): AccountRecord | null {
		const row = this.db.prepare('SELECT * FROM accounts WHERE id = ? LIMIT 1').get(id) as AccountRow | undefined
		return this.toAccount(row)
	}

	createAccount(input: {
		minecraftUuid: string
		minecraftUsername: string
		email: string
		discordUserId: string
		discordUsername: string
		discordGlobalName: string | null
		discordAvatar: string | null
	}): AccountRecord {
		const now = new Date().toISOString()
		const id = randomUUID()

		this.db
			.prepare(
				`
          INSERT INTO accounts (
            id,
            minecraft_uuid,
            minecraft_username,
            email,
            discord_user_id,
            discord_username,
            discord_global_name,
            discord_avatar,
            created_at,
            updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
			)
			.run(
				id,
				input.minecraftUuid,
				input.minecraftUsername,
				input.email,
				input.discordUserId,
				input.discordUsername,
				input.discordGlobalName,
				input.discordAvatar,
				now,
				now,
			)

		return this.getAccountById(id) as AccountRecord
	}

	createWebSession(accountId: string, ttlMs: number): WebSessionRecord {
		const now = new Date()
		const expiresAt = new Date(now.getTime() + ttlMs)
		const id = randomUUID()

		this.db
			.prepare(
				`
          INSERT INTO web_sessions (id, account_id, created_at, expires_at, last_seen_at)
          VALUES (?, ?, ?, ?, ?)
        `,
			)
			.run(id, accountId, now.toISOString(), expiresAt.toISOString(), now.toISOString())

		return {
			id,
			accountId,
			createdAt: now.toISOString(),
			expiresAt: expiresAt.toISOString(),
			lastSeenAt: now.toISOString(),
		}
	}

	getAccountByWebSession(sessionId: string): AccountRecord | null {
		this.deleteExpiredSessions()

		const row = this.db
			.prepare(
				`
          SELECT a.*
          FROM web_sessions s
          JOIN accounts a ON a.id = s.account_id
          WHERE s.id = ? AND s.expires_at > ?
          LIMIT 1
        `,
			)
			.get(sessionId, new Date().toISOString()) as AccountRow | undefined

		if (!row) {
			return null
		}

		this.db
			.prepare('UPDATE web_sessions SET last_seen_at = ? WHERE id = ?')
			.run(new Date().toISOString(), sessionId)

		return this.toAccount(row)
	}

	deleteWebSession(sessionId: string): void {
		this.db.prepare('DELETE FROM web_sessions WHERE id = ?').run(sessionId)
	}

	deleteExpiredSessions(): void {
		this.db.prepare('DELETE FROM web_sessions WHERE expires_at <= ?').run(new Date().toISOString())
	}

	close(): void {
		this.db.close()
	}
}
