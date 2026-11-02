const assert = require('node:assert/strict');
const { test } = require('node:test');
const { VelocityPublicController } = require('../services/api/dist/velocity/velocity.controller');
const { VelocityService } = require('../services/api/dist/velocity/velocity.service');

test('dashboard capacity shows only the main server', () => {
	const service = Object.create(VelocityService.prototype);
	service.lastHeartbeatUnixMs = Date.now();
	service.servers = () => [{ name: 'main', max_players: 25 }];
	service.identities = { findByUuid: () => null };
	service.connectedPlayers = [];
	service.livePlayers = [
		{ uuid: 'main-player', username: 'Alice', serverName: 'main' },
		{ uuid: 'admin-player', username: 'MMU_Admin', serverName: 'main' },
	];
	const snapshot = service.capacitySnapshot();
	assert.deepEqual(
		snapshot.servers.map((server) => server.name),
		['main'],
	);
	assert.deepEqual(
		snapshot.servers.map((server) => server.playerCount),
		[1],
	);
});

test('dashboard roster lists main players without their locations', () => {
	const controller = new VelocityPublicController(
		{ requireSession: () => undefined },
		{
			adminSnapshot: () => ({
				players: [
					{
						username: 'Alice',
						serverName: 'main',
						state: 'playing',
						color: '#fff',
						isCommittee: false,
						customEmojis: [],
					},
					{
						username: 'Waiting',
						serverName: 'waiting-room',
						state: 'queued',
						color: '#fff',
						isCommittee: false,
						customEmojis: [],
					},
					{
						username: 'MMU_Admin',
						serverName: 'main',
						state: 'playing',
						color: '#fff',
						isCommittee: true,
						customEmojis: [],
					},
				],
			}),
		},
	);
	const players = controller.onlinePlayers('session').players;
	assert.deepEqual(
		players.map((player) => player.minecraftUsername),
		['Alice'],
	);
	assert.ok(players.every((player) => !('serverName' in player)));
});
