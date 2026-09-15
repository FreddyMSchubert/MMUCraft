INSERT INTO `feature_toggles` (`key`, `enabled`) VALUES
	('nether', 0),
	('inventors', 0),
	('end', 0)
ON CONFLICT (`key`) DO NOTHING;
