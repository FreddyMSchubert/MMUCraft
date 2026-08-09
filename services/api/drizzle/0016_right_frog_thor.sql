CREATE TABLE `discord_admin_command_logs` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`command` text NOT NULL,
	`discord_username` text NOT NULL,
	`created_at_unix_ms` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `discord_admin_command_logs_created_at_idx` ON `discord_admin_command_logs` (`created_at_unix_ms`);