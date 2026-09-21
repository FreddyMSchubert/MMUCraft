INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('beach-party', 0)
ON CONFLICT (`key`) DO NOTHING;
--> statement-breakpoint
UPDATE `feature_toggles`
SET `enabled` = COALESCE(
	(SELECT `enabled` FROM `feature_toggles` WHERE `key` = 'overpowered'),
	`enabled`
)
WHERE `key` = 'beach-party';
--> statement-breakpoint
DELETE FROM `feature_toggles` WHERE `key` = 'overpowered';
--> statement-breakpoint
INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('cold', 0)
ON CONFLICT (`key`) DO NOTHING;
--> statement-breakpoint
UPDATE `feature_toggles`
SET `enabled` = COALESCE(
	(SELECT `enabled` FROM `feature_toggles` WHERE `key` = 'jolly'),
	`enabled`
)
WHERE `key` = 'cold';
--> statement-breakpoint
DELETE FROM `feature_toggles` WHERE `key` = 'jolly';
--> statement-breakpoint
INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('jolly', 0)
ON CONFLICT (`key`) DO NOTHING;
--> statement-breakpoint
UPDATE `feature_toggles`
SET `enabled` = COALESCE(
	(SELECT `enabled` FROM `feature_toggles` WHERE `key` = 'joyful'),
	`enabled`
)
WHERE `key` = 'jolly';
--> statement-breakpoint
DELETE FROM `feature_toggles` WHERE `key` = 'joyful';
--> statement-breakpoint
INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('christmas', 0)
ON CONFLICT (`key`) DO NOTHING;
