CREATE TABLE `advent_claims` (
	`user_id` integer NOT NULL,
	`year` integer NOT NULL,
	`day` integer NOT NULL,
	`claim_id` text NOT NULL,
	`status` text NOT NULL,
	`amount_dabloons` integer NOT NULL,
	`streak_rate_after` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `year`, `day`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `advent_claim_id_unique` ON `advent_claims` (`claim_id`);--> statement-breakpoint
CREATE TABLE `advent_layouts` (
	`id` integer PRIMARY KEY NOT NULL,
	`revision` integer NOT NULL,
	`layout_json` text NOT NULL
);
