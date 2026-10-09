PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TEMP TABLE `old_max_players` AS SELECT `max_players` FROM `velocity_settings`;--> statement-breakpoint
CREATE TABLE `__new_velocity_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`maintenance_mode` integer DEFAULT 0 NOT NULL,
	`event_override` integer,
	CONSTRAINT "velocity_settings_singleton_check" CHECK("__new_velocity_settings"."id" = 1),
	CONSTRAINT "velocity_settings_maintenance_check" CHECK("__new_velocity_settings"."maintenance_mode" in (0, 1)),
	CONSTRAINT "velocity_settings_event_override_check" CHECK("__new_velocity_settings"."event_override" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `__new_velocity_settings`("id", "maintenance_mode", "event_override") SELECT "id", "maintenance_mode", "event_override" FROM `velocity_settings`;--> statement-breakpoint
DROP TABLE `velocity_settings`;--> statement-breakpoint
ALTER TABLE `__new_velocity_settings` RENAME TO `velocity_settings`;--> statement-breakpoint
PRAGMA foreign_keys=ON;--> statement-breakpoint
ALTER TABLE `velocity_servers` ADD COLUMN `max_players` integer DEFAULT 42 NOT NULL CONSTRAINT "velocity_servers_max_players_check" CHECK(`max_players` between 0 and 99);
--> statement-breakpoint
UPDATE `velocity_servers` SET `max_players` = (SELECT min(`max_players`, 99) FROM `old_max_players`) WHERE `name` = 'main';
--> statement-breakpoint
DROP TABLE `old_max_players`;
