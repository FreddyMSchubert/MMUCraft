CREATE TABLE `velocity_schedules` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`name` text NOT NULL,
	`server_id` integer NOT NULL,
	`starts_at_unix_ms` integer NOT NULL,
	`ends_at_unix_ms` integer NOT NULL,
	`created_by_user_id` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`server_id`) REFERENCES `velocity_servers`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`created_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "velocity_schedules_window_check" CHECK("velocity_schedules"."ends_at_unix_ms" > "velocity_schedules"."starts_at_unix_ms")
);
--> statement-breakpoint
CREATE INDEX `velocity_schedules_window_idx` ON `velocity_schedules` (`starts_at_unix_ms`,`ends_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `velocity_schedules_server_id_idx` ON `velocity_schedules` (`server_id`);--> statement-breakpoint
CREATE TABLE `velocity_servers` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`name` text NOT NULL,
	`address` text NOT NULL,
	`is_default` integer DEFAULT 0 NOT NULL,
	`created_by_user_id` integer,
	`created_at_unix_ms` integer NOT NULL,
	`updated_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`created_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "velocity_servers_default_check" CHECK("velocity_servers"."is_default" in (0, 1))
);
--> statement-breakpoint
CREATE UNIQUE INDEX `velocity_servers_name_unique` ON `velocity_servers` (`name`);--> statement-breakpoint
CREATE UNIQUE INDEX `velocity_servers_address_unique` ON `velocity_servers` (`address`);--> statement-breakpoint
CREATE INDEX `velocity_servers_default_idx` ON `velocity_servers` (`is_default`);--> statement-breakpoint
CREATE TABLE `velocity_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`maintenance_mode` integer DEFAULT 0 NOT NULL,
	`updated_by_user_id` integer,
	`updated_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`updated_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "velocity_settings_singleton_check" CHECK("velocity_settings"."id" = 1),
	CONSTRAINT "velocity_settings_maintenance_check" CHECK("velocity_settings"."maintenance_mode" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `velocity_settings` (`id`, `maintenance_mode`, `updated_at_unix_ms`) VALUES (1, 0, 0);
--> statement-breakpoint
INSERT INTO `velocity_servers` (`name`, `address`, `is_default`, `created_at_unix_ms`, `updated_at_unix_ms`) VALUES ('main', 'minecraft:25565', 1, 0, 0);
