ALTER TABLE `claims` ADD `chunk_x_end` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE `claims` ADD `chunk_z_end` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
UPDATE `claims` SET `chunk_x_end` = `chunk_x`, `chunk_z_end` = `chunk_z`;
