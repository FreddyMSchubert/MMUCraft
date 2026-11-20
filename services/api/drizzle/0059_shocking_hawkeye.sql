CREATE TABLE `advent_secret_claims` (
	`user_id` integer NOT NULL,
	`year` integer NOT NULL,
	`day` integer NOT NULL,
	`claim_id` text NOT NULL,
	`status` text NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `year`, `day`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `advent_secret_claim_id_unique` ON `advent_secret_claims` (`claim_id`);--> statement-breakpoint
CREATE TABLE `advent_secrets` (
	`year` integer NOT NULL,
	`day` integer NOT NULL,
	`x` integer NOT NULL,
	`y` integer NOT NULL,
	PRIMARY KEY(`year`, `day`)
);
