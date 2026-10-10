const assert = require('node:assert/strict');
const { test } = require('node:test');
const { VelocityPublicController } = require('../services/api/dist/velocity/velocity.controller');
const { VelocityService } = require('../services/api/dist/velocity/velocity.service');

test('dashboard capacity shows the event only when a live event has players on a healthy server', () => {
	const service = Object.create(VelocityService.prototype);
	service.lastHeartbeatUnixMs = Date.now();
	service.servers = () => [
		{ name: 'main', max_players: 25 },
		{ name: 'surprising-saturday', max_players: 42 },
	];
	service.identities = { findByUuid: () => null };
	service.connectedPlayers = [];
	service.livePlayers = [
		{ uuid: 'main-player', serverName: 'main' },
		{ uuid: 'event-player', serverName: 'surprising-saturday' },
	];
	service.liveServers = new Map([['surprising-saturday', { online: true }]]);
	service.events = { active: () => null };
	service.settings = () => ({ event_override: null });

	assert.equal(service.capacitySnapshot().showEventCapacity, false);
	service.events.active = () => ({ id: 1 });
	service.liveServers.get('surprising-saturday').online = false;
	assert.equal(service.capacitySnapshot().showEventCapacity, false);
	service.liveServers.get('surprising-saturday').online = true;
	service.livePlayers.pop();
	assert.equal(service.capacitySnapshot().showEventCapacity, false);
	service.livePlayers.push({ uuid: 'event-player', serverName: 'surprising-saturday' });
	service.settings = () => ({ event_override: 0 });
	assert.equal(service.capacitySnapshot().showEventCapacity, false);
	service.settings = () => ({ event_override: null });
	const snapshot = service.capacitySnapshot();
	assert.equal(snapshot.showEventCapacity, true);
	assert.deepEqual(
		snapshot.servers.map((server) => server.playerCount),
		[1, 1],
	);
});

test('dashboard roster lists players from both servers without their locations', () => {
	const controller = new VelocityPublicController(
		{ requireSession: () => undefined },
		{
			adminSnapshot: () => ({
				players: [
					{
						username: 'Zed',
						serverName: 'surprising-saturday',
						state: 'playing',
						color: '#fff',
						isCommittee: false,
						customEmojis: [],
					},
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
				],
			}),
		},
	);
	const players = controller.onlinePlayers('session').players;
	assert.deepEqual(
		players.map((player) => player.minecraftUsername),
		['Alice', 'Zed'],
	);
	assert.ok(players.every((player) => !('serverName' in player)));
});
