const assert = require('node:assert/strict');
const { test } = require('node:test');
const {
	calculateLoginStreak,
	rewardForStreak,
} = require('../services/api/dist/gameplay/dailies/daily-task-rules');

test('login rewards keep growing from the existing day 7 increase', () => {
	for (const [day, reward] of [
		[1, 3],
		[2, 5],
		[6, 9],
		[7, 10],
		[8, 10],
		[9, 11],
		[10, 11],
		[11, 11],
		[12, 12],
		[15, 12],
		[16, 13],
		[20, 13],
		[21, 14],
		[26, 14],
		[27, 15],
		[33, 15],
		[34, 16],
		[41, 16],
		[42, 17],
		[100, 22],
	]) {
		assert.equal(rewardForStreak(day), reward, `day ${day}`);
	}
});

test('existing claims count toward the new reward immediately', () => {
	const days = Array.from(
		{ length: 11 },
		(_, index) => `2026-10-${String(index + 1).padStart(2, '0')}`,
	);
	const streak = calculateLoginStreak(days, '2026-10-12', false);
	assert.equal(streak, 11);
	assert.equal(rewardForStreak(streak + 1), 12);
});
