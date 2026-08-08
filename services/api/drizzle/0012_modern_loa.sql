PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_claims` (
	`id` text PRIMARY KEY NOT NULL,
	`owner_user_id` integer NOT NULL,
	`dimension` text NOT NULL,
	`chunk_x` integer NOT NULL,
	`chunk_z` integer NOT NULL,
	`claim_name` text DEFAULT 'My claim' NOT NULL,
	`color_hex` text,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`owner_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `__new_claims`("id", "owner_user_id", "dimension", "chunk_x", "chunk_z", "claim_name", "color_hex", "created_at_unix_ms") SELECT "id", "owner_user_id", "dimension", "chunk_x", "chunk_z", "claim_name", CASE WHEN upper("color_hex") = '#FFD166' THEN NULL ELSE "color_hex" END, "created_at_unix_ms" FROM `claims`;--> statement-breakpoint
DROP TABLE `claims`;--> statement-breakpoint
ALTER TABLE `__new_claims` RENAME TO `claims`;--> statement-breakpoint
PRAGMA foreign_keys=ON;--> statement-breakpoint
CREATE UNIQUE INDEX `claims_dimension_chunk_unique` ON `claims` (`dimension`,`chunk_x`,`chunk_z`);--> statement-breakpoint
CREATE INDEX `claims_owner_user_id_idx` ON `claims` (`owner_user_id`);--> statement-breakpoint
ALTER TABLE `player_profiles` ADD `color_hex` text;
