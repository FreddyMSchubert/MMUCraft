CREATE TABLE `duck_committee_kills` (
	`killer_user_id` integer NOT NULL,
	`victim_user_id` integer NOT NULL,
	`killed_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`killer_user_id`, `victim_user_id`),
	FOREIGN KEY (`killer_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`victim_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
