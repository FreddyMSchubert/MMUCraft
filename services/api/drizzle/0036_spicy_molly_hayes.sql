CREATE TABLE `referral_links` (
	`code` text PRIMARY KEY NOT NULL,
	`referrer_user_id` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	`referred_user_id` integer,
	`joined_at_unix_ms` integer,
	`join_reward_dabloons` integer DEFAULT 0 NOT NULL,
	`membership_reward_dabloons` integer DEFAULT 0 NOT NULL,
	FOREIGN KEY (`referrer_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`referred_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX `referral_links_referred_user_id_unique` ON `referral_links` (`referred_user_id`);--> statement-breakpoint
CREATE INDEX `referral_links_referrer_idx` ON `referral_links` (`referrer_user_id`);