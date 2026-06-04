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

export interface ShopUnlockRow {
	user_id: number
	item_id: string
	unlock_type: string
	unlocked_at_unix_ms: number
	source: string
}

export interface DailyClaimRow {
	user_id: number
	task_id: string
	period_key: string
	claimed_at_unix_ms: number
}

export interface DailyAdvancementTargetRow {
	user_id: number
	period_key: string
	advancement_id: string
	title: string
	tab_title: string
	icon_item: string
	base_reward_dabloons: number
	bonus_reward_dabloons: number
	selected_at_unix_ms: number
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

			CREATE TABLE IF NOT EXISTS shop_unlocks (
				user_id INTEGER NOT NULL,
				item_id TEXT NOT NULL,
				unlock_type TEXT NOT NULL,
				unlocked_at_unix_ms INTEGER NOT NULL,
				source TEXT NOT NULL,
				PRIMARY KEY(user_id, item_id),
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS shop_unlocks_user_id_idx
				ON shop_unlocks(user_id);

			CREATE TABLE IF NOT EXISTS daily_claims (
				user_id INTEGER NOT NULL,
				task_id TEXT NOT NULL,
				period_key TEXT NOT NULL,
				claimed_at_unix_ms INTEGER NOT NULL,
				PRIMARY KEY(user_id, task_id, period_key),
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS daily_claims_user_id_idx
				ON daily_claims(user_id);

			CREATE TABLE IF NOT EXISTS daily_advancement_targets (
				user_id INTEGER NOT NULL,
				period_key TEXT NOT NULL,
				advancement_id TEXT NOT NULL,
				title TEXT NOT NULL,
				tab_title TEXT NOT NULL,
				icon_item TEXT NOT NULL,
				base_reward_dabloons INTEGER NOT NULL,
				bonus_reward_dabloons INTEGER NOT NULL,
				selected_at_unix_ms INTEGER NOT NULL,
				PRIMARY KEY(user_id, period_key),
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS daily_advancement_targets_user_id_idx
				ON daily_advancement_targets(user_id);
		`)
	}

	get connection(): Database.Database {
		return this.db
	}

	onModuleDestroy() {
		this.db.close()
	}
}
