CREATE TABLE `signin_attempt_logs` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`email` text,
	`journey` text NOT NULL,
	`event` text NOT NULL,
	`succeeded` integer,
	`detail` text,
	`created_at_unix_ms` integer NOT NULL,
	CONSTRAINT "signin_attempt_logs_journey_check" CHECK("signin_attempt_logs"."journey" in ('signin', 'signup')),
	CONSTRAINT "signin_attempt_logs_event_check" CHECK("signin_attempt_logs"."event" in ('email_send', 'email_resend', 'email_code_input', 'minecraft_username_input', 'minecraft_code_input', 'rules_accept')),
	CONSTRAINT "signin_attempt_logs_succeeded_check" CHECK("signin_attempt_logs"."succeeded" in (0, 1))
);
--> statement-breakpoint
CREATE INDEX `signin_attempt_logs_created_at_idx` ON `signin_attempt_logs` (`created_at_unix_ms`);--> statement-breakpoint
CREATE INDEX `signin_attempt_logs_email_created_at_idx` ON `signin_attempt_logs` (`email`,`created_at_unix_ms`);