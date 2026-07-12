ALTER TABLE `users` ADD `is_super_admin` integer DEFAULT 0 NOT NULL CHECK (`is_super_admin` in (0, 1));--> statement-breakpoint
UPDATE `users` SET `is_super_admin` = 1, `is_committee` = 1 WHERE lower(`minecraft_username`) = 'merlinspace';
