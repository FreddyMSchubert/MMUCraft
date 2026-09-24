ALTER TABLE `surprising_saturday_events` ADD `short_description` text DEFAULT '' NOT NULL;
--> statement-breakpoint
UPDATE `surprising_saturday_events` SET `short_description` = substr(trim(replace(`description`, char(10), ' ')), 1, 280);
