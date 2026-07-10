import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { Injectable, OnModuleDestroy } from '@nestjs/common'
import Database from 'better-sqlite3'

export interface UserRow {
	id: number
	email: string
	minecraft_username: string
	is_member: number
	is_committee: number
	whitelisted_at_unix_ms: number
	rules_accepted_at_unix_ms: number
	created_at_unix_ms: number
}

export interface PlayerProfileRow {
	user_id: number
	preferred_name: string
	pronouns: string
	course_year: string
	discord_username: string
	base_x: number | null
	base_y: number | null
	base_z: number | null
	bio: string
	updated_at_unix_ms: number
}

export interface PlayerStatsRow {
	user_id: number
	stats_json: string
	updated_at_unix_ms: number
}

export interface PlayerMoneyEventRow {
	id: string
	user_id: number
	direction: string
	source: string
	amount_dabloons: number
	balance_dabloons: number | null
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

export interface GiftCodeRow {
	code: string
	amount_dabloons: number
	redemption_mode: string
	expires_at_unix_ms: number | null
	created_by_user_id: number
	created_at_unix_ms: number
	redeemed_by_user_id: number | null
	redeemed_at_unix_ms: number | null
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
				is_member INTEGER NOT NULL DEFAULT 0 CHECK (is_member IN (0, 1)),
				is_committee INTEGER NOT NULL DEFAULT 0 CHECK (is_committee IN (0, 1)),
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

			CREATE TABLE IF NOT EXISTS player_profiles (
				user_id INTEGER PRIMARY KEY,
				preferred_name TEXT NOT NULL DEFAULT '',
				pronouns TEXT NOT NULL DEFAULT '',
				course_year TEXT NOT NULL DEFAULT '',
				discord_username TEXT NOT NULL DEFAULT '',
				base_x INTEGER,
				base_y INTEGER,
				base_z INTEGER,
				bio TEXT NOT NULL DEFAULT '',
				updated_at_unix_ms INTEGER NOT NULL,
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE TABLE IF NOT EXISTS player_stats (
				user_id INTEGER PRIMARY KEY,
				stats_json TEXT NOT NULL,
				updated_at_unix_ms INTEGER NOT NULL,
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE TABLE IF NOT EXISTS player_money_events (
				id TEXT PRIMARY KEY,
				user_id INTEGER NOT NULL,
				direction TEXT NOT NULL,
				source TEXT NOT NULL,
				amount_dabloons INTEGER NOT NULL,
				balance_dabloons INTEGER,
				created_at_unix_ms INTEGER NOT NULL,
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS player_money_events_user_id_idx
				ON player_money_events(user_id);

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

			CREATE TABLE IF NOT EXISTS gift_codes (
				code TEXT PRIMARY KEY COLLATE NOCASE,
				amount_dabloons INTEGER NOT NULL CHECK (amount_dabloons > 0),
				redemption_mode TEXT NOT NULL DEFAULT 'single' CHECK (redemption_mode IN ('single', 'per_user')),
				expires_at_unix_ms INTEGER,
				created_by_user_id INTEGER NOT NULL,
				created_at_unix_ms INTEGER NOT NULL,
				redeemed_by_user_id INTEGER,
				redeemed_at_unix_ms INTEGER,
				FOREIGN KEY(created_by_user_id) REFERENCES users(id),
				FOREIGN KEY(redeemed_by_user_id) REFERENCES users(id),
				CHECK (
					(redeemed_by_user_id IS NULL AND redeemed_at_unix_ms IS NULL)
					OR (redeemed_by_user_id IS NOT NULL AND redeemed_at_unix_ms IS NOT NULL)
				)
			);
		`)

		const userColumns = this.db.prepare('PRAGMA table_info(users)').all() as Array<{ name: string }>
		if (!userColumns.some((column) => column.name === 'is_member')) {
			this.db.exec('ALTER TABLE users ADD COLUMN is_member INTEGER NOT NULL DEFAULT 0 CHECK (is_member IN (0, 1))')
		}
		if (!userColumns.some((column) => column.name === 'is_committee')) {
			this.db.exec('ALTER TABLE users ADD COLUMN is_committee INTEGER NOT NULL DEFAULT 0 CHECK (is_committee IN (0, 1))')
		}

		const giftCodeColumns = this.db.prepare('PRAGMA table_info(gift_codes)').all() as Array<{ name: string }>
		if (!giftCodeColumns.some((column) => column.name === 'redemption_mode')) {
			this.db.exec("ALTER TABLE gift_codes ADD COLUMN redemption_mode TEXT NOT NULL DEFAULT 'single' CHECK (redemption_mode IN ('single', 'per_user'))")
		}
		if (!giftCodeColumns.some((column) => column.name === 'expires_at_unix_ms')) {
			this.db.exec('ALTER TABLE gift_codes ADD COLUMN expires_at_unix_ms INTEGER')
		}

		this.db.exec(`
			CREATE TABLE IF NOT EXISTS gift_code_redemptions (
				code TEXT NOT NULL COLLATE NOCASE,
				user_id INTEGER NOT NULL,
				redeemed_at_unix_ms INTEGER NOT NULL,
				PRIMARY KEY(code, user_id),
				FOREIGN KEY(code) REFERENCES gift_codes(code) ON DELETE CASCADE,
				FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
			);

			CREATE INDEX IF NOT EXISTS gift_code_redemptions_code_idx
				ON gift_code_redemptions(code);

			INSERT OR IGNORE INTO gift_code_redemptions (code, user_id, redeemed_at_unix_ms)
			SELECT code, redeemed_by_user_id, redeemed_at_unix_ms
			FROM gift_codes
			WHERE redeemed_by_user_id IS NOT NULL
			  AND redeemed_at_unix_ms IS NOT NULL;
		`)

		const profileColumns = this.db.prepare('PRAGMA table_info(player_profiles)').all() as Array<{ name: string }>
		if (!profileColumns.some((column) => column.name === 'pronouns')) {
			this.db.exec("ALTER TABLE player_profiles ADD COLUMN pronouns TEXT NOT NULL DEFAULT ''")
		}

		this.promoteSuperAdmin()
	}

	get connection(): Database.Database {
		return this.db
	}

	onModuleDestroy() {
		this.db.close()
	}

	private promoteSuperAdmin() {
		this.db.prepare(`
			UPDATE users
			SET is_committee = 1
			WHERE lower(minecraft_username) = lower('MerlinSpace')
		`).run()
	}
}
