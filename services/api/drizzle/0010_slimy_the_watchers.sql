ALTER TABLE `users` ADD `responsible_user_id` integer REFERENCES users(id);--> statement-breakpoint
UPDATE `users`
SET `responsible_user_id` = (
	SELECT `email_whitelist`.`responsible_user_id`
	FROM `email_whitelist`
	WHERE `email_whitelist`.`email` = `users`.`email`
)
WHERE EXISTS (
	SELECT 1
	FROM `email_whitelist`
	WHERE `email_whitelist`.`email` = `users`.`email`
		AND `email_whitelist`.`responsible_user_id` IS NOT NULL
);
