CREATE TABLE `auth_requests` (
	`id` text PRIMARY KEY NOT NULL,
	`kind` text NOT NULL,
	`email` text NOT NULL,
	`user_id` integer,
	`code_hash` text NOT NULL,
	`active_code` text,
	`delivery_status` text NOT NULL,
	`expires_at_unix_ms` integer NOT NULL,
	`completed_at_unix_ms` integer,
	`created_at_unix_ms` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null,
	CONSTRAINT "auth_requests_kind_check" CHECK("auth_requests"."kind" in ('signup', 'signin')),
	CONSTRAINT "auth_requests_delivery_status_check" CHECK("auth_requests"."delivery_status" in ('sent', 'manual'))
);
--> statement-breakpoint
CREATE INDEX `auth_requests_email_idx` ON `auth_requests` (`email`);--> statement-breakpoint
CREATE INDEX `auth_requests_created_at_idx` ON `auth_requests` (`created_at_unix_ms`);