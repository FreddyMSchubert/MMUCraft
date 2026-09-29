PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_velocity_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`maintenance_mode` integer DEFAULT 0 NOT NULL,
	`event_override` integer,
	`max_players` integer DEFAULT 42 NOT NULL,
	CONSTRAINT "velocity_settings_singleton_check" CHECK("__new_velocity_settings"."id" = 1),
	CONSTRAINT "velocity_settings_maintenance_check" CHECK("__new_velocity_settings"."maintenance_mode" in (0, 1)),
	CONSTRAINT "velocity_settings_event_override_check" CHECK("__new_velocity_settings"."event_override" in (0, 1)),
	CONSTRAINT "velocity_settings_max_players_check" CHECK("__new_velocity_settings"."max_players" >= 0)
);
--> statement-breakpoint
INSERT INTO `__new_velocity_settings`("id", "maintenance_mode", "event_override", "max_players") SELECT "id", "maintenance_mode", "event_override", 42 FROM `velocity_settings`;--> statement-breakpoint
DROP TABLE `velocity_settings`;--> statement-breakpoint
ALTER TABLE `__new_velocity_settings` RENAME TO `velocity_settings`;--> statement-breakpoint
PRAGMA foreign_keys=ON;
