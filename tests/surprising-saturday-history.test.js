const assert = require('node:assert/strict');
const { test } = require('node:test');
const { join } = require('node:path');

process.env.DATABASE_URL = ':memory:';
process.env.DATABASE_MIGRATIONS_PATH = join(__dirname, '../services/api/drizzle');

const {
	DatabaseService,
	surprisingSaturdayCompletions,
	surprisingSaturdayEvents,
	surprisingSaturdayParticipants,
} = require('../services/api/dist/database/database.service');
const {
	SurprisingSaturdayService,
} = require('../services/api/dist/velocity/surprising-saturday.service');

test('Surprising Saturday exposes only finished history', () => {
	const database = new DatabaseService();
	try {
		const now = Date.now();
		const finished = database.connection
			.insert(surprisingSaturdayEvents)
			.values({
				title: 'Past event',
				short_description: 'The old results',
				description: 'Historical description',
				starts_at_unix_ms: now - 120_000,
				ends_at_unix_ms: now - 60_000,
				criteria_type: 'list_completion',
				criteria_json: JSON.stringify([{ id: 'mob', name: 'Mob', url: '', points: 2 }]),
			})
			.returning({ id: surprisingSaturdayEvents.id })
			.get();
		const uuid = '00000000000040008000000000000001';
		database.connection
			.insert(surprisingSaturdayParticipants)
			.values({ event_id: finished.id, player_uuid: uuid, joined_at_unix_ms: now - 90_000 })
			.run();
		database.connection
			.insert(surprisingSaturdayCompletions)
			.values({
				event_id: finished.id,
				player_uuid: uuid,
				item_id: 'mob',
				completed_at_unix_ms: now - 80_000,
			})
			.run();
		const upcoming = database.connection
			.insert(surprisingSaturdayEvents)
			.values({
				title: 'Future event',
				description: 'Should not be visible',
				starts_at_unix_ms: now + 60_000,
				ends_at_unix_ms: now + 120_000,
				criteria_type: 'empty',
				criteria_json: '[]',
			})
			.returning({ id: surprisingSaturdayEvents.id })
			.get();
		const service = new SurprisingSaturdayService(database);
		assert.deepEqual(
			service.list().map((event) => event.id),
			[finished.id],
		);
		assert.equal(service.detail(String(finished.id)).status, 'ended');
		assert.deepEqual(
			service.detail(String(finished.id)).players.map((player) => player.points),
			[2],
		);
		assert.throws(() => service.detail(String(upcoming.id)), /Event not found/);
		assert.equal(typeof service.create, 'undefined');
	} finally {
		database.onModuleDestroy();
	}
});
