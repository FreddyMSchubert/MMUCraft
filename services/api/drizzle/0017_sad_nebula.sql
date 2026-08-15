CREATE TABLE `countdowns` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`heading` text NOT NULL,
	`description` text NOT NULL,
	`heading_color` text NOT NULL,
	`description_color` text NOT NULL,
	`background_color` text NOT NULL,
	`background_alpha` integer NOT NULL,
	`background_image_url` text,
	`target_at_unix_ms` integer NOT NULL,
	`visible_until_unix_ms` integer NOT NULL,
	`position` integer NOT NULL,
	CONSTRAINT "countdowns_position_check" CHECK("countdowns"."position" >= 0),
	CONSTRAINT "countdowns_background_alpha_check" CHECK("countdowns"."background_alpha" between 0 and 100)
);
--> statement-breakpoint
CREATE INDEX `countdowns_visible_until_idx` ON `countdowns` (`visible_until_unix_ms`);