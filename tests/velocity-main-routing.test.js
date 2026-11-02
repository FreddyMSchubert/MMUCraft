const assert = require('node:assert/strict');
const { test } = require('node:test');
const { VelocityService } = require('../services/api/dist/velocity/velocity.service');

const uuid = '00000000000040008000000000000001';

test('Velocity only accepts main as a destination', () => {
	const service = Object.create(VelocityService.prototype);
	service.commands = new Map();
	service.pendingQueued = new Map();
	service.identities = { findByUuid: () => null };
	service.bans = { resolve: () => ({ active: false }) };
	service.settings = () => ({ maintenance_mode: 0 });
	service.servers = () => [{ name: 'main', address: 'minecraft:25565', max_players: 42 }];
	service.connectedPlayers = [];
	service.livePlayers = [];
	const response = service.synchronize({ servers: [], players: [] });
	assert.deepEqual(response.route, {
		eventOpen: false,
		revision: 'main',
		targetServerName: 'main',
	});
	assert.deepEqual(
		response.servers.map((server) => server.name),
		['main', 'waiting-room'],
	);
	assert.equal(service.preferredServer(uuid), 'main');
});

test('only the designated MMU admin account bypasses player caps', () => {
	const service = Object.create(VelocityService.prototype);
	service.settings = () => ({ maintenance_mode: 0 });
	service.bans = { resolve: () => ({ active: false }) };
	service.preferredServer = () => 'main';
	for (const role of ['is_committee', 'is_super_admin']) {
		service.identities = {
			resolveAndRefresh: () => ({
				id: 1,
				email: 'admin@example.com',
				is_member: 0,
				[role]: 1,
			}),
		};
		assert.equal(service.authorizePlayer(uuid, 'Admin').exempt, false);
	}
	service.identities = {
		resolveAndRefresh: () => ({ id: 1, email: 'mmuminecraftsociety@gmail.com', is_member: 0 }),
	};
	assert.equal(service.authorizePlayer(uuid, 'Admin').exempt, true);
});

test('committee can enter during maintenance but bans still apply', () => {
	const service = Object.create(VelocityService.prototype);
	service.settings = () => ({ maintenance_mode: 1 });
	let banned = false;
	service.bans = { resolve: () => ({ active: banned, expiresAtUnixMs: null }) };
	service.preferredServer = () => 'main';
	service.identities = {
		resolveAndRefresh: () => ({ id: 1, email: 'mmuminecraftsociety@gmail.com', is_member: 0 }),
	};
	assert.equal(service.authorizePlayer(uuid, 'Admin').status, 'ALLOWED');
	service.identities.resolveAndRefresh = () => ({
		id: 2,
		email: 'committee@example.com',
		is_committee: 1,
	});
	const committeeAccess = service.authorizePlayer(uuid, 'Committee');
	assert.deepEqual(
		[committeeAccess.status, committeeAccess.exempt, committeeAccess.maintenanceAllowed],
		['ALLOWED', false, true],
	);
	service.identities.resolveAndRefresh = () => ({ id: 3, email: 'member@example.com' });
	assert.equal(service.authorizePlayer(uuid, 'Member').status, 'MAINTENANCE');
	service.identities.resolveAndRefresh = () => ({
		id: 2,
		email: 'committee@example.com',
		is_committee: 1,
	});
	banned = true;
	assert.equal(service.authorizePlayer(uuid, 'Committee').status, 'BANNED');
});
