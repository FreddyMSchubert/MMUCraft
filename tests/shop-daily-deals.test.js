const assert = require('node:assert/strict');
const { test } = require('node:test');
const {
	dailyDealDiscountPercent,
	dailyDealItemIds,
	shopDiscountPercent,
	stackedShopDiscountPercent,
} = require('../services/api/dist/gameplay/shop/shop-daily-deals');

test('automatic shop deals only apply on Sunday', () => {
	const items = Array.from({ length: 20 }, (_, index) => ({ id: `item-${index}` }));
	const weekday = '2026-11-25';
	const sunday = '2026-11-29';

	assert.equal(dailyDealItemIds(items, weekday).size, 0);
	assert.equal(dailyDealDiscountPercent('item-0', weekday), 0);
	assert.equal(dailyDealItemIds(items, sunday).size, 16);
	assert.equal(dailyDealDiscountPercent('item-0', sunday), 45);

	assert.equal(shopDiscountPercent('charm-wallet', true, 0), 42);
	assert.equal(stackedShopDiscountPercent(0, 20), 20);
});
