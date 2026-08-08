PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_gift_codes` (
	`code` text PRIMARY KEY NOT NULL,
	`amount_dabloons` integer NOT NULL,
	`redemption_mode` text DEFAULT 'single' NOT NULL,
	`members_only` integer DEFAULT 0 NOT NULL,
	`expires_at_unix_ms` integer,
	`created_by_user_id` integer NOT NULL,
	`created_at_unix_ms` integer NOT NULL,
	`redeemed_by_user_id` integer,
	`redeemed_at_unix_ms` integer,
	FOREIGN KEY (`created_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`redeemed_by_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	CONSTRAINT "gift_codes_amount_check" CHECK("__new_gift_codes"."amount_dabloons" > 0),
	CONSTRAINT "gift_codes_redemption_mode_check" CHECK("__new_gift_codes"."redemption_mode" in ('single', 'per_user')),
	CONSTRAINT "gift_codes_members_only_check" CHECK("__new_gift_codes"."members_only" in (0, 1)),
	CONSTRAINT "gift_codes_redeemed_pair_check" CHECK(("__new_gift_codes"."redeemed_by_user_id" is null and "__new_gift_codes"."redeemed_at_unix_ms" is null) or ("__new_gift_codes"."redeemed_by_user_id" is not null and "__new_gift_codes"."redeemed_at_unix_ms" is not null))
);
--> statement-breakpoint
INSERT INTO `__new_gift_codes`("code", "amount_dabloons", "redemption_mode", "members_only", "expires_at_unix_ms", "created_by_user_id", "created_at_unix_ms", "redeemed_by_user_id", "redeemed_at_unix_ms") SELECT "code", "amount_dabloons", "redemption_mode", 0, "expires_at_unix_ms", "created_by_user_id", "created_at_unix_ms", "redeemed_by_user_id", "redeemed_at_unix_ms" FROM `gift_codes`;--> statement-breakpoint
DROP TABLE `gift_codes`;--> statement-breakpoint
ALTER TABLE `__new_gift_codes` RENAME TO `gift_codes`;--> statement-breakpoint
PRAGMA foreign_keys=ON;
