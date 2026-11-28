const assert = require('node:assert/strict');
const { test } = require('node:test');
const { londonDate } = require('../services/api/dist/advent/advent.service');
const { londonAdventDay } = require('../services/web/src/lib/advent.ts');

test('Advent day changes at 4am in London', () => {
	for (const [instant, expectedDate, expectedDay] of [
		['2026-12-01T03:59:59Z', { year: 2026, month: 11, day: 30 }, 0],
		['2026-12-01T04:00:00Z', { year: 2026, month: 12, day: 1 }, 1],
		['2026-12-02T03:59:59Z', { year: 2026, month: 12, day: 1 }, 1],
		['2026-12-02T04:00:00Z', { year: 2026, month: 12, day: 2 }, 2],
		['2026-12-26T03:59:59Z', { year: 2026, month: 12, day: 25 }, 25],
		['2026-07-02T02:59:59Z', { year: 2026, month: 7, day: 1 }, 0],
		['2026-07-02T03:00:00Z', { year: 2026, month: 7, day: 2 }, 0],
	]) {
		const now = new Date(instant);
		assert.deepEqual(londonDate(now), expectedDate, instant);
		assert.equal(londonAdventDay(now), expectedDay, instant);
	}
});
