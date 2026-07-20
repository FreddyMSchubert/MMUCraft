CREATE TABLE `email_whitelist` (
	`email` text PRIMARY KEY NOT NULL,
	`added_by_user_id` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`added_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
