const assert = require('node:assert/strict');
const { test } = require('node:test');
const { eventRewardAmount } = require('../services/api/dist/velocity/surprising-saturday.service');

test('Surprising Saturday rewards use separate places and a completion threshold for the rest', () => {
	const rewards = {
		first: 50,
		second: 40,
		third: 30,
		fourth: 20,
		fifth: 10,
		participation: 5,
		participationMinItems: 2,
	};
	assert.deepEqual(
		Array.from({ length: 5 }, (_, place) => eventRewardAmount(rewards, place, 0)),
		[50, 40, 30, 20, 10],
	);
	assert.equal(eventRewardAmount(rewards, 5, 1), 0);
	assert.equal(eventRewardAmount(rewards, 5, 2), 5);
	assert.equal(eventRewardAmount(rewards, 8, 3), 5);
});
