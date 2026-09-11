CREATE TABLE `limited_shop_purchases` (
	`user_id` integer NOT NULL,
	`item_id` text NOT NULL,
	`period_key` text NOT NULL,
	`slot` integer NOT NULL,
	`purchased_at_unix_ms` integer NOT NULL,
	PRIMARY KEY(`user_id`, `item_id`, `period_key`, `slot`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `limited_shop_purchases_user_period_idx` ON `limited_shop_purchases` (`user_id`,`period_key`);