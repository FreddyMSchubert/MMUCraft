PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_command_logs` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`command` text NOT NULL,
	`source` text NOT NULL,
	`actor_name` text NOT NULL,
	`user_id` integer,
	`is_operator` integer,
	`succeeded` integer,
	`result` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null,
	CONSTRAINT "command_logs_source_check" CHECK("__new_command_logs"."source" in ('minecraft', 'waiting-room', 'surprising-saturday', 'proxy', 'discord')),
	CONSTRAINT "command_logs_is_operator_check" CHECK("__new_command_logs"."is_operator" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `__new_command_logs`("id", "command", "source", "actor_name", "user_id", "is_operator", "succeeded", "result", "created_at_unix_ms") SELECT "id", "command", "source", "actor_name", "user_id", "is_operator", "succeeded", "result", "created_at_unix_ms" FROM `command_logs`;--> statement-breakpoint
DROP TABLE `command_logs`;--> statement-breakpoint
ALTER TABLE `__new_command_logs` RENAME TO `command_logs`;--> statement-breakpoint
PRAGMA foreign_keys=ON;--> statement-breakpoint
CREATE INDEX `command_logs_created_at_idx` ON `command_logs` (`created_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `command_logs_user_id_created_at_idx` ON `command_logs` (`user_id`,`created_at_unix_ms`);