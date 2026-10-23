CREATE TABLE `build_events` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`title` text NOT NULL,
	`description` text NOT NULL,
	`starts_at_unix_ms` integer NOT NULL,
	`build_ends_at_unix_ms` integer NOT NULL,
	`voting_starts_at_unix_ms` integer NOT NULL,
	`voting_ends_at_unix_ms` integer NOT NULL,
	`warp_ends_at_unix_ms` integer NOT NULL,
	`min_team_size` integer NOT NULL,
	`max_team_size` integer NOT NULL,
	`categories_json` text NOT NULL,
	`results_revealed` integer DEFAULT 0 NOT NULL,
	CONSTRAINT "build_events_dates_check" CHECK("build_events"."starts_at_unix_ms" < "build_events"."build_ends_at_unix_ms" and "build_events"."build_ends_at_unix_ms" <= "build_events"."voting_starts_at_unix_ms" and "build_events"."voting_starts_at_unix_ms" < "build_events"."voting_ends_at_unix_ms" and "build_events"."voting_ends_at_unix_ms" <= "build_events"."warp_ends_at_unix_ms"),
	CONSTRAINT "build_events_team_size_check" CHECK("build_events"."min_team_size" >= 1 and "build_events"."min_team_size" <= "build_events"."max_team_size")
);
--> statement-breakpoint
CREATE INDEX `build_events_starts_idx` ON `build_events` (`starts_at_unix_ms`);--> statement-breakpoint
CREATE TABLE `build_submission_members` (
	`event_id` integer NOT NULL,
	`submission_id` integer NOT NULL,
	`user_id` integer NOT NULL,
	PRIMARY KEY(`event_id`, `user_id`),
	FOREIGN KEY (`event_id`) REFERENCES `build_events`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`submission_id`) REFERENCES `build_submissions`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `build_submission_members_submission_idx` ON `build_submission_members` (`submission_id`);--> statement-breakpoint
CREATE TABLE `build_submissions` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`event_id` integer NOT NULL,
	`title` text NOT NULL,
	`description` text NOT NULL,
	`images_json` text NOT NULL,
	`dimension` text NOT NULL,
	`block_x` integer NOT NULL,
	`block_y` integer NOT NULL,
	`block_z` integer NOT NULL,
	`yaw` real DEFAULT 0 NOT NULL,
	`pitch` real DEFAULT 0 NOT NULL,
	`min_chunk_x` integer NOT NULL,
	`max_chunk_x` integer NOT NULL,
	`min_chunk_z` integer NOT NULL,
	`max_chunk_z` integer NOT NULL,
	FOREIGN KEY (`event_id`) REFERENCES `build_events`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `build_submissions_event_idx` ON `build_submissions` (`event_id`);--> statement-breakpoint
CREATE TABLE `build_visits` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`submission_id` integer NOT NULL,
	`origin_dimension` text NOT NULL,
	`origin_x` real NOT NULL,
	`origin_y` real NOT NULL,
	`origin_z` real NOT NULL,
	`origin_yaw` real NOT NULL,
	`origin_pitch` real NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`submission_id`) REFERENCES `build_submissions`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `build_voter_exclusions` (
	`event_id` integer NOT NULL,
	`voter_user_id` integer NOT NULL,
	PRIMARY KEY(`event_id`, `voter_user_id`),
	FOREIGN KEY (`event_id`) REFERENCES `build_events`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`voter_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `build_votes` (
	`submission_id` integer NOT NULL,
	`voter_user_id` integer NOT NULL,
	`scores_json` text NOT NULL,
	`cast_at_unix_ms` integer NOT NULL,
	`excluded` integer DEFAULT 0 NOT NULL,
	PRIMARY KEY(`submission_id`, `voter_user_id`),
	FOREIGN KEY (`submission_id`) REFERENCES `build_submissions`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`voter_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
