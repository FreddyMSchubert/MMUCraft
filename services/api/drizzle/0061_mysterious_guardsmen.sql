CREATE TABLE `christmas_present_settings` (
	`id` integer PRIMARY KEY NOT NULL,
	`simulated_at_unix_ms` integer
);
--> statement-breakpoint
CREATE TABLE `christmas_presents` (
	`id` text PRIMARY KEY NOT NULL,
	`sender_user_id` integer NOT NULL,
	`recipient_user_id` integer,
	`anonymous` integer DEFAULT 0 NOT NULL,
	`title` text,
	`item_data` text,
	`item_name` text,
	`item_count` integer,
	`status` text DEFAULT 'draft' NOT NULL,
	`admin_reason` text,
	`created_at_unix_ms` integer NOT NULL,
	`sent_at_unix_ms` integer,
	`opened_at_unix_ms` integer,
	`claimed_at_unix_ms` integer,
	`admin_copy_at_unix_ms` integer,
	FOREIGN KEY (`sender_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`recipient_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `christmas_presents_sender_idx` ON `christmas_presents` (`sender_user_id`);--> statement-breakpoint
CREATE INDEX `christmas_presents_recipient_idx` ON `christmas_presents` (`recipient_user_id`);