CREATE TABLE `external_membership_purchases` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`started_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
