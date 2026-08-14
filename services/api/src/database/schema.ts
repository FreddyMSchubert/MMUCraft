import { sql } from 'drizzle-orm'
import {
	check,
	index,
	integer,
	primaryKey,
	real,
	sqliteTable,
	text,
	uniqueIndex,
	type AnySQLiteColumn,
} from 'drizzle-orm/sqlite-core'

export const users = sqliteTable('users', {
	id: integer('id').primaryKey({ autoIncrement: true }),
	email: text('email').notNull(),
	minecraft_uuid: text('minecraft_uuid'),
	minecraft_username: text('minecraft_username').notNull(),
	responsible_user_id: integer('responsible_user_id').references((): AnySQLiteColumn => users.id),
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

export const authRequests = sqliteTable('auth_requests', {
	id: text('id').primaryKey(),
	kind: text('kind', { enum: ['signup', 'signin'] }).notNull(),
	email: text('email').notNull(),
	user_id: integer('user_id').references(() => users.id, { onDelete: 'set null' }),
	code_hash: text('code_hash').notNull(),
	active_code: text('active_code'),
	failed_attempts: integer('failed_attempts').notNull().default(0),
	delivery_status: text('delivery_status', { enum: ['sent', 'manual'] }).notNull(),
	expires_at_unix_ms: integer('expires_at_unix_ms').notNull(),
	completed_at_unix_ms: integer('completed_at_unix_ms'),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [
	index('auth_requests_email_idx').on(table.email),
	index('auth_requests_created_at_idx').on(table.created_at_unix_ms),
	check('auth_requests_kind_check', sql`${table.kind} in ('signup', 'signin')`),
	check('auth_requests_delivery_status_check', sql`${table.delivery_status} in ('sent', 'manual')`),
])

export const emailWhitelist = sqliteTable('email_whitelist', {
	email: text('email').primaryKey(),
	added_by_user_id: integer('added_by_user_id').notNull().references(() => users.id),
	responsible_user_id: integer('responsible_user_id').references(() => users.id),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
})

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
	color_hex: text('color_hex'),
	updated_at_unix_ms: integer('updated_at_unix_ms').notNull(),
})

export const claims = sqliteTable('claims', {
	id: text('id').primaryKey(),
	owner_user_id: integer('owner_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	dimension: text('dimension').notNull(),
	chunk_x: integer('chunk_x').notNull(),
	chunk_z: integer('chunk_z').notNull(),
	claim_name: text('claim_name').notNull().default('My claim'),
	color_hex: text('color_hex'),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [
	uniqueIndex('claims_dimension_chunk_unique').on(table.dimension, table.chunk_x, table.chunk_z),
	index('claims_owner_user_id_idx').on(table.owner_user_id),
])

export const claimMembers = sqliteTable('claim_members', {
	claim_id: text('claim_id').notNull().references(() => claims.id, { onDelete: 'cascade' }),
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	added_at_unix_ms: integer('added_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.claim_id, table.user_id] }),
	index('claim_members_user_id_idx').on(table.user_id),
])

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

export const fishCatches = sqliteTable('fish_catches', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	fish_id: text('fish_id').notNull(),
	first_length_cm: real('first_length_cm').notNull(),
	first_caught_at_unix_ms: integer('first_caught_at_unix_ms').notNull(),
	smallest_length_cm: real('smallest_length_cm').notNull(),
	smallest_caught_at_unix_ms: integer('smallest_caught_at_unix_ms').notNull(),
	largest_length_cm: real('largest_length_cm').notNull(),
	largest_caught_at_unix_ms: integer('largest_caught_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.fish_id] }),
	index('fish_catches_fish_id_idx').on(table.fish_id),
])

export const knowledgeUnlocks = sqliteTable('knowledge_unlocks', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	knowledge_id: text('knowledge_id').notNull(),
	unlocked_at_unix_ms: integer('unlocked_at_unix_ms').notNull(),
	source: text('source').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.knowledge_id] }),
	index('knowledge_unlocks_user_id_idx').on(table.user_id),
])

export const knowledgeReads = sqliteTable('knowledge_reads', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	knowledge_id: text('knowledge_id').notNull(),
	read_at_unix_ms: integer('read_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.knowledge_id] }),
	index('knowledge_reads_user_id_idx').on(table.user_id),
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

export const playerBans = sqliteTable('player_bans', {
	user_id: integer('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
	banned_by_user_id: integer('banned_by_user_id').notNull().references(() => users.id),
	expires_at_unix_ms: integer('expires_at_unix_ms'),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [
	check('player_bans_expiry_check', sql`${table.expires_at_unix_ms} is null or ${table.expires_at_unix_ms} > ${table.created_at_unix_ms}`),
])

export const dailyTasks = sqliteTable('daily_tasks', {
	user_id: integer('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
	period_key: text('period_key').notNull(),
	slot: integer('slot').notNull(),
	task_id: text('task_id').notNull(),
	task_json: text('task_json').notNull(),
	updated_at_unix_ms: integer('updated_at_unix_ms').notNull(),
}, (table) => [
	primaryKey({ columns: [table.user_id, table.period_key, table.slot] }),
	uniqueIndex('daily_tasks_user_period_task_unique').on(table.user_id, table.period_key, table.task_id),
	index('daily_tasks_period_key_idx').on(table.period_key),
])

export const giftCodes = sqliteTable('gift_codes', {
	code: text('code').primaryKey(),
	amount_dabloons: integer('amount_dabloons').notNull(),
	redemption_mode: text('redemption_mode', { enum: ['single', 'per_user'] }).notNull().default('single'),
	members_only: integer('members_only').notNull().default(0),
	expires_at_unix_ms: integer('expires_at_unix_ms'),
	created_by_user_id: integer('created_by_user_id').notNull().references(() => users.id),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
	redeemed_by_user_id: integer('redeemed_by_user_id').references(() => users.id),
	redeemed_at_unix_ms: integer('redeemed_at_unix_ms'),
}, (table) => [
	check('gift_codes_amount_check', sql`${table.amount_dabloons} > 0`),
	check('gift_codes_redemption_mode_check', sql`${table.redemption_mode} in ('single', 'per_user')`),
	check('gift_codes_members_only_check', sql`${table.members_only} in (0, 1)`),
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

export const discordAdminCommandLogs = sqliteTable('discord_admin_command_logs', {
	id: integer('id').primaryKey({ autoIncrement: true }),
	command: text('command').notNull(),
	discord_username: text('discord_username').notNull(),
	created_at_unix_ms: integer('created_at_unix_ms').notNull(),
}, (table) => [index('discord_admin_command_logs_created_at_idx').on(table.created_at_unix_ms)])

export const countdowns = sqliteTable('countdowns', {
	id: integer('id').primaryKey({ autoIncrement: true }),
	heading: text('heading').notNull(),
	description: text('description').notNull(),
	heading_color: text('heading_color').notNull(),
	description_color: text('description_color').notNull(),
	background_color: text('background_color').notNull(),
	background_alpha: integer('background_alpha').notNull(),
	background_image_url: text('background_image_url'),
	target_at_unix_ms: integer('target_at_unix_ms').notNull(),
	visible_until_unix_ms: integer('visible_until_unix_ms').notNull(),
	position: integer('position').notNull(),
}, (table) => [
	index('countdowns_visible_until_idx').on(table.visible_until_unix_ms),
	check('countdowns_position_check', sql`${table.position} >= 0`),
	check('countdowns_background_alpha_check', sql`${table.background_alpha} between 0 and 100`),
])

export type UserRow = typeof users.$inferSelect
export type SessionRow = typeof sessions.$inferSelect
export type PlayerBanRow = typeof playerBans.$inferSelect
export type AuthRequestRow = typeof authRequests.$inferSelect
export type EmailWhitelistRow = typeof emailWhitelist.$inferSelect
export type PlayerProfileRow = typeof playerProfiles.$inferSelect
export type ClaimRow = typeof claims.$inferSelect
export type ClaimMemberRow = typeof claimMembers.$inferSelect
export type PlayerStatsRow = typeof playerStats.$inferSelect
export type PlayerMoneyEventRow = typeof playerMoneyEvents.$inferSelect
export type FishCatchRow = typeof fishCatches.$inferSelect
export type KnowledgeUnlockRow = typeof knowledgeUnlocks.$inferSelect
export type KnowledgeReadRow = typeof knowledgeReads.$inferSelect
export type ShopUnlockRow = typeof shopUnlocks.$inferSelect
export type DailyClaimRow = typeof dailyClaims.$inferSelect
export type DailyAdvancementTargetRow = typeof dailyAdvancementTargets.$inferSelect
export type DailyTaskRow = typeof dailyTasks.$inferSelect
export type GiftCodeRow = typeof giftCodes.$inferSelect
export type DiscordAdminCommandLogRow = typeof discordAdminCommandLogs.$inferSelect
export type CountdownRow = typeof countdowns.$inferSelect

export const schema = {
	users,
	sessions,
	playerBans,
	authRequests,
	emailWhitelist,
	playerProfiles,
	claims,
	claimMembers,
	playerStats,
	playerMoneyEvents,
	fishCatches,
	knowledgeUnlocks,
	knowledgeReads,
	shopUnlocks,
	dailyClaims,
	dailyAdvancementTargets,
	dailyTasks,
	giftCodes,
	giftCodeRedemptions,
	discordAdminCommandLogs,
	countdowns,
}
