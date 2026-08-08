CREATE TABLE `player_bans` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`banned_by_user_id` integer NOT NULL,
	`expires_at_unix_ms` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`banned_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "player_bans_expiry_check" CHECK("player_bans"."expires_at_unix_ms" is null or "player_bans"."expires_at_unix_ms" > "player_bans"."created_at_unix_ms")
);
