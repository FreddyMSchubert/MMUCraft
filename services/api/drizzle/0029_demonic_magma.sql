CREATE TABLE `announcement_reads` (
	`announcement_id` integer NOT NULL,
	`minecraft_uuid` text NOT NULL,
	`read_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`announcement_id`, `minecraft_uuid`),
	FOREIGN KEY (`announcement_id`) REFERENCES `announcements`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `announcement_reads_player_idx` ON `announcement_reads` (`minecraft_uuid`);--> statement-breakpoint
CREATE TABLE `announcements` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`text` text NOT NULL,
	`link_url` text,
	`starts_at_unix_ms` integer NOT NULL,
	`ends_at_unix_ms` integer NOT NULL,
	`position` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	CONSTRAINT "announcements_window_check" CHECK("announcements"."ends_at_unix_ms" > "announcements"."starts_at_unix_ms"),
	CONSTRAINT "announcements_position_check" CHECK("announcements"."position" >= 0)
);
--> statement-breakpoint
CREATE INDEX `announcements_window_idx` ON `announcements` (`starts_at_unix_ms`,`ends_at_unix_ms`);