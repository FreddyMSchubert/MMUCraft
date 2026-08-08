CREATE TABLE `claim_members` (
	`claim_id` text NOT NULL,
	`user_id` integer NOT NULL,
	`added_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`claim_id`, `user_id`),
	FOREIGN KEY (`claim_id`) REFERENCES `claims`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `claim_members_user_id_idx` ON `claim_members` (`user_id`);--> statement-breakpoint
CREATE TABLE `claims` (
	`id` text PRIMARY KEY NOT NULL,
	`owner_user_id` integer NOT NULL,
	`dimension` text NOT NULL,
	`chunk_x` integer NOT NULL,
	`chunk_z` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`owner_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `claims_dimension_chunk_unique` ON `claims` (`dimension`,`chunk_x`,`chunk_z`);--> statement-breakpoint
CREATE INDEX `claims_owner_user_id_idx` ON `claims` (`owner_user_id`);