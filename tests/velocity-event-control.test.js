const assert = require('node:assert/strict');
const { test } = require('node:test');
const { AuthSigninService } = require('../services/api/dist/auth/auth-signin.service');
const { VelocityService } = require('../services/api/dist/velocity/velocity.service');

const uuid = '00000000000040008000000000000001';

test('the schedule starts warmup despite a manual stop and cannot be switched off', () => {
	const service = Object.create(VelocityService.prototype);
	service.settings = () => ({ event_override: 0 });
	service.events = { active: () => null, warmingUp: () => ({ id: 1 }) };
	service.proxyIsOnline = () => false;
	service.livePlayers = [];

	assert.equal(service.eventControlState().desiredRunning, true);
	assert.throws(() => service.setEventOverride(false), /event schedule controls/);
});

test('event control reports a rescheduled end time', () => {
	const service = Object.create(VelocityService.prototype);
	let end = Date.now() + 10 * 60_000;
	service.settings = () => ({ event_override: null });
	service.events = { active: () => ({ ends_at_unix_ms: end }), warmingUp: () => null };
	service.proxyIsOnline = () => false;
	service.livePlayers = [];

	assert.equal(service.eventControlState().endsAtUnixMs, end);
	end += 10 * 60_000;
	assert.equal(service.eventControlState().endsAtUnixMs, end);
	end -= 15 * 60_000;
	assert.equal(service.eventControlState().endsAtUnixMs, end);
	service.events.active = () => null;
	assert.equal(service.eventControlState().endsAtUnixMs, null);
});

test('an empty event leaves the event server stopped and players on main', () => {
	const service = Object.create(VelocityService.prototype);
	service.settings = () => ({ event_override: null });
	service.events = {
		active: () => ({ id: 7, criteria_type: 'empty' }),
		warmingUp: () => ({ id: 7, criteria_type: 'empty' }),
	};
	service.proxyIsOnline = () => true;
	service.livePlayers = [];

	assert.deepEqual(service.eventRoute(Date.now(), null), {
		activeEvent: undefined,
		eventOpen: false,
		revision: 'main',
		targetServerName: 'main',
	});
	assert.equal(service.eventControlState().desiredRunning, false);
	assert.equal(service.preferredServer(uuid), 'main');
});

test('an admin move starts a stopped event server and queues the transfer', () => {
	const service = Object.create(VelocityService.prototype);
	const overrides = [];
	service.identities = { findByUuid: () => ({ id: 1 }) };
	service.settings = () => ({ maintenance_mode: 0 });
	service.database = {
		connection: {
			select: () => ({
				from: () => ({
					where: () => ({ get: () => ({ id: 2, name: 'surprising-saturday' }) }),
				}),
			}),
			update: () => ({
				set: (value) => ({ where: () => ({ run: () => overrides.push(value) }) }),
			}),
		},
	};
	service.eventControlState = () => ({ desiredRunning: false });
	service.events = { active: () => null, warmingUp: () => null };
	service.proxyIsOnline = () => true;
	service.connectedPlayers = [
		{ uuid, sessionId: 'session', serverName: 'main', state: 'playing' },
	];
	service.commands = new Map();
	service.nextCommandId = 1;
	service.currentRouteRevision = () => 'main:manual';
	service.refreshEventState = () => {};

	assert.equal(service.movePlayer(uuid, 2, true).commandId, 1);
	assert.deepEqual(overrides, [{ event_override: 1 }]);
	assert.equal(service.commands.get(1).targetServerName, 'surprising-saturday');
	service.eventControlState = () => ({ desiredRunning: true });
	service.events.warmingUp = () => ({ id: 1 });
	service.currentRouteRevision = () => 'warmup:1';
	assert.equal(service.movePlayer(uuid, 2, true).commandId, 2);
	assert.equal(overrides.length, 1);
});

test('a pending test transfer survives scheduled warmup but ends at the live route', () => {
	const service = Object.create(VelocityService.prototype);
	service.commands = new Map([
		[
			1,
			{
				id: 1,
				createdAtUnixMs: Date.now(),
				routeRevision: 'main:manual',
				playerUuid: uuid,
				sessionId: 'session',
				targetServerName: 'surprising-saturday',
				temporary: true,
			},
		],
	]);
	service.pendingQueued = new Map();
	service.identities = { findByUuid: () => null };
	service.settings = () => ({ maintenance_mode: 0, event_override: null });
	service.servers = () => [];
	service.events = { recordParticipants: () => {} };
	service.eventRoute = () => ({
		activeEvent: null,
		eventOpen: false,
		revision: 'warmup:7',
		targetServerName: 'main',
	});

	assert.equal(service.synchronize({ servers: [], players: [] }).commands.length, 1);
	service.eventRoute = () => ({
		activeEvent: null,
		eventOpen: true,
		revision: 'event:7',
		targetServerName: 'surprising-saturday',
	});
	assert.equal(service.synchronize({ servers: [], players: [] }).commands.length, 0);
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

test('only the MMU admin account enters during maintenance or a ban', () => {
	const service = Object.create(VelocityService.prototype);
	service.settings = () => ({ maintenance_mode: 1 });
	service.bans = { resolve: () => ({ active: true, expiresAtUnixMs: null }) };
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
	assert.equal(service.authorizePlayer(uuid, 'Committee').status, 'MAINTENANCE');
	service.settings = () => ({ maintenance_mode: 0 });
	assert.equal(service.authorizePlayer(uuid, 'Committee').status, 'BANNED');
	const signin = Object.create(AuthSigninService.prototype);
	signin.bans = service.bans;
	assert.equal(
		signin.requirePlayerNotBanned({ id: 1, email: 'mmuminecraftsociety@gmail.com' }),
		false,
	);
	assert.throws(() => signin.requirePlayerNotBanned({ id: 2, email: 'committee@example.com' }));
});

test('a new live event routes everyone to it except players who chose main for this event', () => {
	const service = Object.create(VelocityService.prototype);
	let choice;
	service.events = { active: () => ({ id: 7 }) };
	service.database = {
		connection: {
			select: () => ({ from: () => ({ where: () => ({ get: () => choice }) }) }),
		},
	};
	assert.equal(service.preferredServer(uuid), 'surprising-saturday');
	choice = { preferred: 'main', eventId: 6 };
	assert.equal(service.preferredServer(uuid), 'surprising-saturday');
	choice.eventId = 7;
	assert.equal(service.preferredServer(uuid), 'main');
	service.events.active = () => null;
	assert.equal(service.preferredServer(uuid), 'main');
});
