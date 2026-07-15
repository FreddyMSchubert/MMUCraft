CREATE TABLE `fish_catches` (
	`user_id` integer NOT NULL,
	`fish_id` text NOT NULL,
	`first_length_cm` real NOT NULL,
	`first_caught_at_unix_ms` integer NOT NULL,
	`smallest_length_cm` real NOT NULL,
	`smallest_caught_at_unix_ms` integer NOT NULL,
	`largest_length_cm` real NOT NULL,
	`largest_caught_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `fish_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `fish_catches_fish_id_idx` ON `fish_catches` (`fish_id`);