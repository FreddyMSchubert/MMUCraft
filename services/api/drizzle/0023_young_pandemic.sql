CREATE TABLE `feature_toggles` (
	`key` text PRIMARY KEY NOT NULL,
	`enabled` integer DEFAULT 0 NOT NULL,
	CONSTRAINT "feature_toggles_enabled_check" CHECK("feature_toggles"."enabled" in (0, 1))
);
--> statement-breakpoint
INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES ('nether', 0), ('end', 0);
