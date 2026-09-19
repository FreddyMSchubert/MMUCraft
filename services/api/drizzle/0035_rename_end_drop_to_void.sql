INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('void', 0)
ON CONFLICT (`key`) DO NOTHING;
--> statement-breakpoint
UPDATE `feature_toggles`
SET `enabled` = COALESCE(
	(SELECT `enabled` FROM `feature_toggles` WHERE `key` = 'end'),
	`enabled`
)
WHERE `key` = 'void';
--> statement-breakpoint
DELETE FROM `feature_toggles` WHERE `key` = 'end';
