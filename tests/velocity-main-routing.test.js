const assert = require('node:assert/strict');
const { test } = require('node:test');
const { VelocityService } = require('../services/api/dist/velocity/velocity.service');

const uuid = '00000000000040008000000000000001';

test('Velocity only accepts main as a destination', () => {
	const service = Object.create(VelocityService.prototype);
	service.commands = new Map();
	service.pendingQueued = new Map();
	service.banhammerUntil = new Map();
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
	service.banhammerUntil = new Map();
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
	service.banhammerUntil = new Map();
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

test('banhammer disconnects for one minute without creating a ban', () => {
	const service = Object.create(VelocityService.prototype);
	service.banhammerUntil = new Map();
	service.pendingQueued = new Map();
	service.commands = new Map();
	service.identities = {
		resolveAndRefresh: () => ({ id: 1, email: 'member@example.com', is_member: 1 }),
		findByUuid: () => ({ id: 1, email: 'member@example.com', is_member: 1 }),
	};
	service.bans = { resolve: () => ({ active: false }) };
	service.settings = () => ({ maintenance_mode: 0 });
	service.servers = () => [{ name: 'main', address: 'minecraft:25565', max_players: 42 }];
	service.preferredServer = () => 'main';
	service.rememberConnectedServer = () => {};

	assert.deepEqual(service.useAdventBanhammer('invalid'), { accepted: false });
	const start = Date.now();
	assert.deepEqual(service.useAdventBanhammer(uuid), { accepted: true });
	const access = service.authorizePlayer(uuid, 'Member');
	assert.equal(access.status, 'BANHAMMER');
	assert.ok(access.expiresAtUnixMs >= start + 60_000);
	service.bans.resolve = () => ({ active: true, expiresAtUnixMs: null });
	assert.equal(service.authorizePlayer(uuid, 'Member').status, 'BANNED');
	service.bans.resolve = () => ({ active: false });
	const response = service.synchronize({
		servers: [],
		players: [
			{
				uuid,
				sessionId: '00000000-0000-4000-8000-000000000001',
				username: 'Member',
				serverName: 'main',
				state: 'playing',
				targetServerName: 'main',
			},
		],
	});
	assert.deepEqual(response.disconnects, [
		{
			playerUuid: uuid,
			status: 'BANHAMMER',
			expiresAtUnixMs: access.expiresAtUnixMs,
		},
	]);
	service.banhammerUntil.set(uuid, Date.now() - 1);
	assert.equal(service.authorizePlayer(uuid, 'Member').status, 'ALLOWED');
	assert.equal(service.banhammerUntil.has(uuid), false);
});
