CREATE TABLE `duck_awards` (
	`user_id` integer NOT NULL,
	`duck_id` integer NOT NULL,
	`awarded_at_unix_ms` integer NOT NULL,
	`free_delivered_at_unix_ms` integer,
	PRIMARY KEY(`user_id`, `duck_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	CONSTRAINT "duck_awards_duck_id_check" CHECK("duck_awards"."duck_id" between 1 and 13)
);
--> statement-breakpoint
CREATE INDEX `duck_awards_user_id_idx` ON `duck_awards` (`user_id`);--> statement-breakpoint
CREATE TABLE `duck_notes` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`note` text DEFAULT '' NOT NULL,
	`updated_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
