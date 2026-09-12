INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('circus', 0)
ON CONFLICT (`key`) DO NOTHING;
--> statement-breakpoint
UPDATE `feature_toggles`
SET `enabled` = COALESCE(
	(SELECT `enabled` FROM `feature_toggles` WHERE `key` = 'humorous'),
	`enabled`
)
WHERE `key` = 'circus';
--> statement-breakpoint
DELETE FROM `feature_toggles` WHERE `key` = 'humorous';
