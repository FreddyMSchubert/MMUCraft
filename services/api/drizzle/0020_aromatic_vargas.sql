CREATE TABLE `email_send_events` (
	`id` text PRIMARY KEY NOT NULL,
	`email_hash` text NOT NULL,
	`ip_hash` text NOT NULL,
	`sent_at_unix_ms` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `email_send_events_email_time_idx` ON `email_send_events` (`email_hash`,`sent_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `email_send_events_ip_time_idx` ON `email_send_events` (`ip_hash`,`sent_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `email_send_events_time_idx` ON `email_send_events` (`sent_at_unix_ms`);--> statement-breakpoint
DROP TABLE `auth_requests`;