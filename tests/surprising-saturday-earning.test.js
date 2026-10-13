const assert = require('node:assert/strict');
const { test } = require('node:test');
const {
	joinedEarningAmount,
	nextLondonDay,
	SurprisingSaturdayService,
} = require('../services/api/dist/velocity/surprising-saturday.service');

test('Joined Earning adds completed goal rewards and waits for the next London day', () => {
	const goals = [
		{ id: 'chunklock:wood', amount: 10 },
		{ id: 'chunklock:dragon', amount: 100 },
	];
	assert.equal(joinedEarningAmount(goals, [{ itemId: 'chunklock:dragon' }]), 100);
	assert.equal(
		joinedEarningAmount(goals, [{ itemId: 'chunklock:wood' }, { itemId: 'chunklock:dragon' }]),
		110,
	);
	assert.equal(joinedEarningAmount(goals, [{ itemId: 'chunklock:unknown' }]), 0);

	const eventEnd = Date.parse('2026-10-24T21:00:00Z');
	assert.equal(nextLondonDay(eventEnd, Date.parse('2026-10-24T22:59:59Z')), false);
	assert.equal(nextLondonDay(eventEnd, Date.parse('2026-10-24T23:00:00Z')), true);
});

test('Joined Earning pays committee players for completed goals after the event day', () => {
	const uuid = '00000000000000000000000000000001';
	const start = Date.parse('2026-10-24T15:00:00Z');
	const end = Date.parse('2026-10-24T21:00:00Z');
	const event = {
		id: 7,
		title: 'Chunklock',
		starts_at_unix_ms: start,
		ends_at_unix_ms: end,
		criteria_type: 'joined_earning',
		criteria_json: JSON.stringify([
			{ id: 'chunklock:wood', name: 'Wood', amount: 10 },
			{ id: 'chunklock:dragon', name: 'Dragon', amount: 100 },
		]),
	};
	function service() {
		const rows = [
			[event],
			[{ player_uuid: uuid }],
			[
				{
					player_uuid: uuid,
					item_id: 'chunklock:dragon',
					completed_at_unix_ms: start + 1000,
				},
			],
			[
				{
					id: 4,
					minecraft_username: 'CommitteePlayer',
					email: 'committee@example.com',
					is_committee: 1,
				},
			],
			[{}],
		];
		let query = 0;
		return new SurprisingSaturdayService({
			connection: {
				select() {
					const result = rows[query++];
					return {
						from: () => ({
							where: () => ({ all: () => result, get: () => result[0] }),
						}),
					};
				},
			},
		});
	}
	assert.deepEqual(service().rewardsForPlayer(uuid, Date.parse('2026-10-24T22:59:59Z')), []);
	assert.deepEqual(service().rewardsForPlayer(uuid, Date.parse('2026-10-24T23:00:00Z')), [
		{ eventId: 7, title: 'Chunklock', place: 0, amount: 100 },
	]);
});
