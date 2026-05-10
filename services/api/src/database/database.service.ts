import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { Injectable, OnModuleDestroy } from '@nestjs/common'
import Database from 'better-sqlite3'

export interface UserRow {
	id: number
	email: string
	minecraft_username: string
	whitelisted_at_unix_ms: number
	rules_accepted_at_unix_ms: number
	created_at_unix_ms: number
}

export enum SignupFlowStatus {
	EMAIL_PENDING = 'EMAIL_PENDING',
	MINECRAFT_USERNAME_PENDING = 'MINECRAFT_USERNAME_PENDING',
	MINECRAFT_CODE_PENDING = 'MINECRAFT_CODE_PENDING',
	RULES_PENDING = 'RULES_PENDING',
	COMPLETE = 'COMPLETE',
}
export interface SignupFlowRow {
	id: string
	email: string
	status: SignupFlowStatus
	email_code_hash: string
	email_code_expires_at_unix_ms: number
	minecraft_username: string | null
	minecraft_code_hash: string | null
	minecraft_code_expires_at_unix_ms: number | null
	created_at_unix_ms: number
	updated_at_unix_ms: number
}

export interface SessionRow {
	id: string
	user_id: number
	token_hash: string
	expires_at_unix_ms: number
	created_at_unix_ms: number
}

export interface KnowledgeUnlockRow {
	user_id: number
	knowledge_id: string
	unlocked_at_unix_ms: number
	source: string
}

@Injectable()
export class DatabaseService implements OnModuleDestroy {
	private readonly db: Database.Database

	constructor() {
		const dbPath = join(process.cwd(), 'data', 'app.sqlite')

		mkdirSync(dirname(dbPath), { recursive: true })

		this.db = new Database(dbPath)
		this.db.pragma('journal_mode = WAL')
		this.db.pragma('foreign_keys = ON')

		this.db.exec(`
			CREATE TABLE IF NOT EXISTS users (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				email TEXT NOT NULL UNIQUE,
				minecraft_username TEXT NOT NULL UNIQUE,
				whitelisted_at_unix_ms INTEGER NOT NULL,
				rules_accepted_at_unix_ms INTEGER NOT NULL,
				created_at_unix_ms INTEGER NOT NULL
			);

			CREATE TABLE IF NOT EXISTS signup_flows (
				id TEXT PRIMARY KEY,
				email TEXT NOT NULL,
				status TEXT NOT NULL,
				email_code_hash TEXT NOT NULL,
				email_code_expires_at_unix_ms INTEGER NOT NULL,
				minecraft_username TEXT,
				minecraft_code_hash TEXT,
				minecraft_code_expires_at_unix_ms INTEGER,
				created_at_unix_ms INTEGER NOT NULL,
				updated_at_unix_ms INTEGER NOT NULL
			);

			CREATE INDEX IF NOT EXISTS signup_flows_email_idx
				ON signup_flows(email);

			CREATE INDEX IF NOT EXISTS signup_flows_minecraft_username_idx
				ON signup_flows(minecraft_username);

			CREATE TABLE IF NOT EXISTS sessions (
				id TEXT PRIMARY KEY,
				user_id INTEGER NOT NULL,
				token_hash TEXT NOT NULL UNIQUE,
				expires_at_unix_ms INTEGER NOT NULL,
				created_at_unix_ms INTEGER NOT NULL,
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS sessions_token_hash_idx
				ON sessions(token_hash);

			CREATE TABLE IF NOT EXISTS knowledge_unlocks (
				user_id INTEGER NOT NULL,
				knowledge_id TEXT NOT NULL,
				unlocked_at_unix_ms INTEGER NOT NULL,
				source TEXT NOT NULL,
				PRIMARY KEY(user_id, knowledge_id),
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS knowledge_unlocks_user_id_idx
				ON knowledge_unlocks(user_id);
		`)
	}

	get connection(): Database.Database {
		return this.db
	}

	onModuleDestroy() {
		this.db.close()
	}
}
