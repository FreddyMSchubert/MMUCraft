CREATE TABLE `velocity_player_servers` (
	`player_uuid` text PRIMARY KEY NOT NULL,
	`preferred_server` text NOT NULL,
	`last_connected_server` text,
	CONSTRAINT "velocity_player_servers_preferred_check" CHECK("velocity_player_servers"."preferred_server" in ('main', 'surprising-saturday')),
	CONSTRAINT "velocity_player_servers_last_check" CHECK("velocity_player_servers"."last_connected_server" in ('main', 'surprising-saturday'))
);
