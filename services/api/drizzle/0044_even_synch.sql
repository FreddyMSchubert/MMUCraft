CREATE TABLE `dabloon_interactions` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`action` text NOT NULL,
	`item_kind` text NOT NULL,
	`item_id` text NOT NULL,
	`item_count` integer NOT NULL,
	`amount_dabloons` integer NOT NULL,
	`actor_name` text NOT NULL,
	`minecraft_uuid` text,
	`user_id` integer,
	`context` text NOT NULL,
	`dimension` text NOT NULL,
	`x` integer NOT NULL,
	`y` integer NOT NULL,
	`z` integer NOT NULL,
	`balance_before` integer,
	`balance_after` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `dabloon_interactions_time_idx` ON `dabloon_interactions` (`created_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `dabloon_interactions_user_time_idx` ON `dabloon_interactions` (`user_id`,`created_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `dabloon_interactions_action_time_idx` ON `dabloon_interactions` (`action`,`created_at_unix_ms`);