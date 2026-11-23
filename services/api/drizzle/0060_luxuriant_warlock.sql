CREATE TABLE `big_boss_progress` (
	`minecraft_username` text PRIMARY KEY NOT NULL,
	`qualifying_ms` integer DEFAULT 0 NOT NULL,
	`win_count` integer DEFAULT 0 NOT NULL,
	`stopped_after_win` integer DEFAULT 0 NOT NULL,
	`last_held_at_unix_ms` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE `big_boss_sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`reign_started_at_unix_ms` integer NOT NULL,
	`minecraft_username` text NOT NULL,
	`qualifying_ms` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE `big_boss_state` (
	`id` integer PRIMARY KEY NOT NULL,
	`holder_username` text,
	`reign_started_at_unix_ms` integer,
	`acquired_by_kill` integer DEFAULT 0 NOT NULL,
	`activity_day` text NOT NULL,
	`activity_ms` integer DEFAULT 0 NOT NULL,
	`last_sample_at_unix_ms` integer,
	`last_dimension` text,
	`last_x` integer,
	`last_y` integer,
	`last_z` integer,
	`last_in_claim` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE `big_boss_transitions` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`event_id` text,
	`at_unix_ms` integer NOT NULL,
	`previous_username` text,
	`next_username` text,
	`reason` text NOT NULL,
	`detail` text DEFAULT '' NOT NULL,
	`admin_user_id` integer,
	FOREIGN KEY (`admin_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX `big_boss_transitions_event_id_unique` ON `big_boss_transitions` (`event_id`);--> statement-breakpoint
ALTER TABLE `player_profiles` ADD `big_boss_opt_out` integer DEFAULT 0 NOT NULL;