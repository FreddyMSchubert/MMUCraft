CREATE TABLE `surprising_saturday_completions` (
	`event_id` integer NOT NULL,
	`player_uuid` text NOT NULL,
	`item_id` text NOT NULL,
	`completed_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`event_id`, `player_uuid`, `item_id`),
	FOREIGN KEY (`event_id`) REFERENCES `surprising_saturday_events`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `surprising_saturday_events` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`title` text NOT NULL,
	`description` text NOT NULL,
	`starts_at_unix_ms` integer NOT NULL,
	`ends_at_unix_ms` integer NOT NULL,
	`criteria_type` text NOT NULL,
	`criteria_json` text NOT NULL,
	CONSTRAINT "surprising_saturday_events_window_check" CHECK("surprising_saturday_events"."ends_at_unix_ms" > "surprising_saturday_events"."starts_at_unix_ms")
);
--> statement-breakpoint
CREATE INDEX `surprising_saturday_events_window_idx` ON `surprising_saturday_events` (`starts_at_unix_ms`,`ends_at_unix_ms`);--> statement-breakpoint
CREATE TABLE `surprising_saturday_participants` (
	`event_id` integer NOT NULL,
	`player_uuid` text NOT NULL,
	`joined_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`event_id`, `player_uuid`),
	FOREIGN KEY (`event_id`) REFERENCES `surprising_saturday_events`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_velocity_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`maintenance_mode` integer DEFAULT 0 NOT NULL,
	`event_override` integer,
	CONSTRAINT "velocity_settings_singleton_check" CHECK("__new_velocity_settings"."id" = 1),
	CONSTRAINT "velocity_settings_maintenance_check" CHECK("__new_velocity_settings"."maintenance_mode" in (0, 1)),
	CONSTRAINT "velocity_settings_event_override_check" CHECK("__new_velocity_settings"."event_override" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `__new_velocity_settings`("id", "maintenance_mode", "event_override") SELECT "id", "maintenance_mode", NULL FROM `velocity_settings`;--> statement-breakpoint
DROP TABLE `velocity_settings`;--> statement-breakpoint
ALTER TABLE `__new_velocity_settings` RENAME TO `velocity_settings`;--> statement-breakpoint
PRAGMA foreign_keys=ON;--> statement-breakpoint
DELETE FROM `velocity_schedules`;--> statement-breakpoint
DELETE FROM `velocity_servers` WHERE `name` <> 'main';--> statement-breakpoint
UPDATE `velocity_servers` SET `is_default` = 1 WHERE `name` = 'main';--> statement-breakpoint
INSERT INTO `velocity_servers` (`name`, `address`, `is_default`) VALUES ('surprising-saturday', 'surprising-saturday:25565', 0);
