import { sql } from 'drizzle-orm'
import {
	check,
	index,
	integer,
	primaryKey,
	sqliteTable,
	text,
	uniqueIndex,
} from 'drizzle-orm/sqlite-core'

export const users = sqliteTable('users', {
	id: integer('id').primaryKey({ autoIncrement: true }),
	email: text('email').notNull(),
	minecraft_uuid: text('minecraft_uuid'),
	minecraft_username: text('minecraft_username').notNull(),
	is_member: integer('is_member').notNull().default(0),
	is_committee: integer('is_committee').notNull().default(0),
	is_super_admin: integer('is_super_admin').notNull().default(0),
	whitelisted_at_unix_ms: integer('whitelisted_at_unix_ms').notNull(),
	rules_accepted_at_unix_ms: integer('rules_accepted_at_unix_ms').notNull(),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [
	uniqueIndex('users_email_unique').on(table.email),
	uniqueIndex('users_minecraft_uuid_unique').on(table.minecraft_uuid),
	uniqueIndex('users_minecraft_username_unique').on(table.minecraft_username),
	check('users_is_member_check', sql`${table.is_member} in (0, 1)`),
	check('users_is_committee_check', sql`${table.is_committee} in (0, 1)`),
	check('users_is_super_admin_check', sql`${table.is_super_admin} in (0, 1)`),
])

export const sessions = sqliteTable('sessions', {
	id: text('id').primaryKey(),
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	token_hash: text('token_hash').notNull(),
	expires_at_unix_ms: integer('expires_at_unix_ms').notNull(),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [
	uniqueIndex('sessions_token_hash_unique').on(table.token_hash),
	index('sessions_token_hash_idx').on(table.token_hash),
])

export const playerProfiles = sqliteTable('player_profiles', {
	user_id: integer('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
	preferred_name: text('preferred_name').notNull().default(''),
	pronouns: text('pronouns').notNull().default(''),
	course_year: text('course_year').notNull().default(''),
	discord_username: text('discord_username').notNull().default(''),
	base_x: integer('base_x'),
	base_y: integer('base_y'),
	base_z: integer('base_z'),
	bio: text('bio').notNull().default(''),
	updated_at_unix_ms: integer('updated_at_unix_ms').notNull(),
})

export const playerStats = sqliteTable('player_stats', {
	user_id: integer('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
	stats_json: text('stats_json').notNull(),
	updated_at_unix_ms: integer('updated_at_unix_ms').notNull(),
})

export const playerMoneyEvents = sqliteTable('player_money_events', {
	id: text('id').primaryKey(),
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	direction: text('direction').notNull(),
	source: text('source').notNull(),
	amount_dabloons: integer('amount_dabloons').notNull(),
	balance_dabloons: integer('balance_dabloons'),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [index('player_money_events_user_id_idx').on(table.user_id)])

export const knowledgeUnlocks = sqliteTable('knowledge_unlocks', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	knowledge_id: text('knowledge_id').notNull(),
	unlocked_at_unix_ms: integer('unlocked_at_unix_ms').notNull(),
	source: text('source').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.knowledge_id] }),
	index('knowledge_unlocks_user_id_idx').on(table.user_id),
])

export const shopUnlocks = sqliteTable('shop_unlocks', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	item_id: text('item_id').notNull(),
	unlock_type: text('unlock_type').notNull(),
	unlocked_at_unix_ms: integer('unlocked_at_unix_ms').notNull(),
	source: text('source').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.item_id] }),
	index('shop_unlocks_user_id_idx').on(table.user_id),
])

export const dailyClaims = sqliteTable('daily_claims', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	task_id: text('task_id').notNull(),
	period_key: text('period_key').notNull(),
	claimed_at_unix_ms: integer('claimed_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.task_id, table.period_key] }),
	index('daily_claims_user_id_idx').on(table.user_id),
])

export const dailyAdvancementTargets = sqliteTable('daily_advancement_targets', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	period_key: text('period_key').notNull(),
	advancement_id: text('advancement_id').notNull(),
	title: text('title').notNull(),
	tab_title: text('tab_title').notNull(),
	icon_item: text('icon_item').notNull(),
	base_reward_dabloons: integer('base_reward_dabloons').notNull(),
	bonus_reward_dabloons: integer('bonus_reward_dabloons').notNull(),
	selected_at_unix_ms: integer('selected_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.period_key] }),
	index('daily_advancement_targets_user_id_idx').on(table.user_id),
])

export const giftCodes = sqliteTable('gift_codes', {
	code: text('code').primaryKey(),
	amount_dabloons: integer('amount_dabloons').notNull(),
	redemption_mode: text('redemption_mode', { enum: ['single', 'per_user'] }).notNull().default('single'),
	expires_at_unix_ms: integer('expires_at_unix_ms'),
	created_by_user_id: integer('created_by_user_id').notNull().references(() => users.id),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
	redeemed_by_user_id: integer('redeemed_by_user_id').references(() => users.id),
	redeemed_at_unix_ms: integer('redeemed_at_unix_ms'),
}, (table) => [
	check('gift_codes_amount_check', sql`${table.amount_dabloons} > 0`),
	check('gift_codes_redemption_mode_check', sql`${table.redemption_mode} in ('single', 'per_user')`),
	check('gift_codes_redeemed_pair_check', sql`(${table.redeemed_by_user_id} is null and ${table.redeemed_at_unix_ms} is null) or (${table.redeemed_by_user_id} is not null and ${table.redeemed_at_unix_ms} is not null)`),
])

export const giftCodeRedemptions = sqliteTable('gift_code_redemptions', {
	code: text('code').notNull().references(() => giftCodes.code, { onDelete: 'cascade' }),
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	redeemed_at_unix_ms: integer('redeemed_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.code, table.user_id] }),
	index('gift_code_redemptions_code_idx').on(table.code),
])

export type UserRow = typeof users.$inferSelect
export type SessionRow = typeof sessions.$inferSelect
export type PlayerProfileRow = typeof playerProfiles.$inferSelect
export type PlayerStatsRow = typeof playerStats.$inferSelect
export type PlayerMoneyEventRow = typeof playerMoneyEvents.$inferSelect
export type KnowledgeUnlockRow = typeof knowledgeUnlocks.$inferSelect
export type ShopUnlockRow = typeof shopUnlocks.$inferSelect
export type DailyClaimRow = typeof dailyClaims.$inferSelect
export type DailyAdvancementTargetRow = typeof dailyAdvancementTargets.$inferSelect
export type GiftCodeRow = typeof giftCodes.$inferSelect

export const schema = {
	users,
	sessions,
	playerProfiles,
	playerStats,
	playerMoneyEvents,
	knowledgeUnlocks,
	shopUnlocks,
	dailyClaims,
	dailyAdvancementTargets,
	giftCodes,
	giftCodeRedemptions,
}
