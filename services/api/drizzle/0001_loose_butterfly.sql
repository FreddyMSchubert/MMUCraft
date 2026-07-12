ALTER TABLE `signup_flows` ADD `minecraft_uuid` text;--> statement-breakpoint
ALTER TABLE `users` ADD `minecraft_uuid` text;--> statement-breakpoint
CREATE UNIQUE INDEX `users_minecraft_uuid_unique` ON `users` (`minecraft_uuid`);