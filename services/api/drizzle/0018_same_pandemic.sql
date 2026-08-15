CREATE TABLE `knowledge_reads` (
	`user_id` integer NOT NULL,
	`knowledge_id` text NOT NULL,
	`read_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `knowledge_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `knowledge_reads_user_id_idx` ON `knowledge_reads` (`user_id`);