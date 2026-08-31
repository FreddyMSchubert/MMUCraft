ALTER TABLE `discord_admin_command_logs` RENAME TO `command_logs`;--> statement-breakpoint
ALTER TABLE `command_logs` RENAME COLUMN "discord_username" TO "actor_name";--> statement-breakpoint
DROP INDEX `discord_admin_command_logs_created_at_idx`;--> statement-breakpoint
ALTER TABLE `command_logs` ADD `source` text NOT NULL DEFAULT 'discord';--> statement-breakpoint
ALTER TABLE `command_logs` ADD `user_id` integer REFERENCES users(id);--> statement-breakpoint
ALTER TABLE `command_logs` ADD `is_operator` integer NOT NULL DEFAULT 1;--> statement-breakpoint
CREATE INDEX `command_logs_created_at_idx` ON `command_logs` (`created_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `command_logs_user_id_created_at_idx` ON `command_logs` (`user_id`,`created_at_unix_ms`);--> statement-breakpoint
PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_command_logs` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`command` text NOT NULL,
	`source` text NOT NULL,
	`actor_name` text NOT NULL,
	`user_id` integer,
	`is_operator` integer NOT NULL,
	`succeeded` integer,
	`result` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null,
	CONSTRAINT "command_logs_source_check" CHECK("__new_command_logs"."source" in ('minecraft', 'discord')),
	CONSTRAINT "command_logs_is_operator_check" CHECK("__new_command_logs"."is_operator" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `__new_command_logs`("id", "command", "source", "actor_name", "user_id", "is_operator", "succeeded", "result", "created_at_unix_ms") SELECT "id", "command", "source", "actor_name", "user_id", "is_operator", NULL, NULL, "created_at_unix_ms" FROM `command_logs`;--> statement-breakpoint
DROP TABLE `command_logs`;--> statement-breakpoint
ALTER TABLE `__new_command_logs` RENAME TO `command_logs`;--> statement-breakpoint
PRAGMA foreign_keys=ON;
