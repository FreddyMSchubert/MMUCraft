CREATE TABLE `launch_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`launch_at_unix_ms` integer NOT NULL,
	CONSTRAINT "launch_settings_singleton_check" CHECK("launch_settings"."id" = 1)
);
--> statement-breakpoint
INSERT INTO `launch_settings` (`id`, `launch_at_unix_ms`) VALUES (1, 1790708400000);
