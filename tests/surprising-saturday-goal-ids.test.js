const assert = require('node:assert/strict');
const { test } = require('node:test');
const {
	SurprisingSaturdayService,
} = require('../services/api/dist/velocity/surprising-saturday.service');

test('list completion events create and record plain goal IDs', () => {
	let saved;
	const connection = {
		select: () => ({ from: () => ({ where: () => ({ get: () => null }) }) }),
		insert: () => ({
			values: (values) => {
				saved = values;
				return { returning: () => ({ get: () => ({ id: 1 }) }) };
			},
		}),
	};
	const service = new SurprisingSaturdayService({ connection });
	const start = Date.now() + 60_000;
	const result = service.create({
		title: 'Goals',
		shortDescription: 'Complete goals',
		startsAtUnixMs: start,
		endsAtUnixMs: start + 60_000,
		criteriaType: 'list_completion',
		items: [
			{ id: 'a', name: 'First goal', url: '', points: 1 },
			{ id: 'b', name: 'Second goal', url: '', points: 1 },
		],
		rewards: {
			first: 0,
			second: 0,
			third: 0,
			fourth: 0,
			fifth: 0,
			participation: 0,
			participationMinItems: 1,
		},
	});
	assert.deepEqual(result, { ok: true, eventId: 1 });
	assert.deepEqual(
		JSON.parse(saved.criteria_json).map(({ id }) => id),
		['a', 'b'],
	);

	connection.transaction = (callback) => callback();
	connection.insert = () => ({
		values: (values) => ({
			onConflictDoNothing: () => ({
				returning: () => ({ all: () => [{ itemId: values.item_id }] }),
			}),
		}),
	});
	service.active = () => ({
		id: 1,
		criteria_type: 'list_completion',
		criteria_json: saved.criteria_json,
	});
	service.podium = () => [];
	service.recordParticipants = () => {};
	service.score = () => ({ score: 1 });
	assert.equal(
		service.recordCompletion({
			playerUuid: '00000000000000000000000000000001',
			itemId: 'a',
			occurredAtUnixMs: Date.now(),
		}).accepted,
		true,
	);
});
