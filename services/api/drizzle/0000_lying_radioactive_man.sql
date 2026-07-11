CREATE TABLE `daily_advancement_targets` (
	`user_id` integer NOT NULL,
	`period_key` text NOT NULL,
	`advancement_id` text NOT NULL,
	`title` text NOT NULL,
	`tab_title` text NOT NULL,
	`icon_item` text NOT NULL,
	`base_reward_dabloons` integer NOT NULL,
	`bonus_reward_dabloons` integer NOT NULL,
	`selected_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `period_key`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `daily_advancement_targets_user_id_idx` ON `daily_advancement_targets` (`user_id`);--> statement-breakpoint
CREATE TABLE `daily_claims` (
	`user_id` integer NOT NULL,
	`task_id` text NOT NULL,
	`period_key` text NOT NULL,
	`claimed_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `task_id`, `period_key`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `daily_claims_user_id_idx` ON `daily_claims` (`user_id`);--> statement-breakpoint
CREATE TABLE `gift_code_redemptions` (
	`code` text NOT NULL,
	`user_id` integer NOT NULL,
	`redeemed_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`code`, `user_id`),
	FOREIGN KEY (`code`) REFERENCES `gift_codes`(`code`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `gift_code_redemptions_code_idx` ON `gift_code_redemptions` (`code`);--> statement-breakpoint
CREATE TABLE `gift_codes` (
	`code` text PRIMARY KEY NOT NULL,
	`amount_dabloons` integer NOT NULL,
	`redemption_mode` text DEFAULT 'single' NOT NULL,
	`expires_at_unix_ms` integer,
	`created_by_user_id` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	`redeemed_by_user_id` integer,
	`redeemed_at_unix_ms` integer,
	FOREIGN KEY (`created_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`redeemed_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "gift_codes_amount_check" CHECK("gift_codes"."amount_dabloons" > 0),
	CONSTRAINT "gift_codes_redemption_mode_check" CHECK("gift_codes"."redemption_mode" in ('single', 'per_user')),
	CONSTRAINT "gift_codes_redeemed_pair_check" CHECK(("gift_codes"."redeemed_by_user_id" is null and "gift_codes"."redeemed_at_unix_ms" is null) or ("gift_codes"."redeemed_by_user_id" is not null and "gift_codes"."redeemed_at_unix_ms" is not null))
);
--> statement-breakpoint
CREATE TABLE `knowledge_unlocks` (
	`user_id` integer NOT NULL,
	`knowledge_id` text NOT NULL,
	`unlocked_at_unix_ms` integer NOT NULL,
	`source` text NOT NULL,
	PRIMARY KEY(`user_id`, `knowledge_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `knowledge_unlocks_user_id_idx` ON `knowledge_unlocks` (`user_id`);--> statement-breakpoint
CREATE TABLE `player_money_events` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` integer NOT NULL,
	`direction` text NOT NULL,
	`source` text NOT NULL,
	`amount_dabloons` integer NOT NULL,
	`balance_dabloons` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `player_money_events_user_id_idx` ON `player_money_events` (`user_id`);--> statement-breakpoint
CREATE TABLE `player_profiles` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`preferred_name` text DEFAULT '' NOT NULL,
	`pronouns` text DEFAULT '' NOT NULL,
	`course_year` text DEFAULT '' NOT NULL,
	`discord_username` text DEFAULT '' NOT NULL,
	`base_x` integer,
	`base_y` integer,
	`base_z` integer,
	`bio` text DEFAULT '' NOT NULL,
	`updated_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `player_stats` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`stats_json` text NOT NULL,
	`updated_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` integer NOT NULL,
	`token_hash` text NOT NULL,
	`expires_at_unix_ms` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `sessions_token_hash_unique` ON `sessions` (`token_hash`);--> statement-breakpoint
CREATE INDEX `sessions_token_hash_idx` ON `sessions` (`token_hash`);--> statement-breakpoint
CREATE TABLE `shop_unlocks` (
	`user_id` integer NOT NULL,
	`item_id` text NOT NULL,
	`unlock_type` text NOT NULL,
	`unlocked_at_unix_ms` integer NOT NULL,
	`source` text NOT NULL,
	PRIMARY KEY(`user_id`, `item_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `shop_unlocks_user_id_idx` ON `shop_unlocks` (`user_id`);--> statement-breakpoint
CREATE TABLE `signup_flows` (
	`id` text PRIMARY KEY NOT NULL,
	`email` text NOT NULL,
	`status` text NOT NULL,
	`email_code_hash` text NOT NULL,
	`email_code_expires_at_unix_ms` integer NOT NULL,
	`minecraft_username` text,
	`minecraft_code_hash` text,
	`minecraft_code_expires_at_unix_ms` integer,
	`created_at_unix_ms` integer NOT NULL,
	`updated_at_unix_ms` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `signup_flows_email_idx` ON `signup_flows` (`email`);--> statement-breakpoint
CREATE INDEX `signup_flows_minecraft_username_idx` ON `signup_flows` (`minecraft_username`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`email` text NOT NULL,
	`minecraft_username` text NOT NULL,
	`is_member` integer DEFAULT 0 NOT NULL,
	`is_committee` integer DEFAULT 0 NOT NULL,
	`whitelisted_at_unix_ms` integer NOT NULL,
	`rules_accepted_at_unix_ms` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	CONSTRAINT "users_is_member_check" CHECK("users"."is_member" in (0, 1)),
	CONSTRAINT "users_is_committee_check" CHECK("users"."is_committee" in (0, 1))
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_email_unique` ON `users` (`email`);--> statement-breakpoint
CREATE UNIQUE INDEX `users_minecraft_username_unique` ON `users` (`minecraft_username`);