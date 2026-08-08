CREATE TABLE `daily_tasks` (
	`user_id` integer NOT NULL,
	`period_key` text NOT NULL,
	`slot` integer NOT NULL,
	`task_id` text NOT NULL,
	`task_json` text NOT NULL,
	`updated_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `period_key`, `slot`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `daily_tasks_user_period_task_unique` ON `daily_tasks` (`user_id`,`period_key`,`task_id`);--> statement-breakpoint
CREATE INDEX `daily_tasks_period_key_idx` ON `daily_tasks` (`period_key`);