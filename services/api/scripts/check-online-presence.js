const assert = require('node:assert/strict');
const { PlayersService } = require('../dist/players/players.service');

const service = new PlayersService({}, { findByUuid: () => null }, {}, {});
service.onlinePlayersReconciledAt = Date.now();

async function check() {
	const player = {
		minecraft_username: 'Merlinspace',
		minecraft_uuid: '00000000000000000000000000000001',
	};
	service.recordPresenceEvent({ type: 'join', ...player });
	assert.deepEqual(
		(await service.listOnlinePlayers()).players.map(
			({ minecraftUsername }) => minecraftUsername,
		),
		['Merlinspace'],
	);

	service.recordPresenceEvent({ type: 'first_join', ...player });
	assert.equal((await service.listOnlinePlayers()).players.length, 1);

	service.recordPresenceEvent({ type: 'leave', ...player });
	assert.equal((await service.listOnlinePlayers()).players.length, 0);
}

void check();
