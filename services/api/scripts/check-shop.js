const assert = require('node:assert/strict')
const { isBritishAnniversary, shopDiscountPercent } = require('../dist/gameplay/shop/shop.service')

assert.equal(isBritishAnniversary(Date.UTC(2024, 7, 16), Date.UTC(2026, 7, 16, 22, 59)), true)
assert.equal(isBritishAnniversary(Date.UTC(2024, 7, 16), Date.UTC(2026, 7, 16, 23)), false)
assert.equal(isBritishAnniversary(Date.UTC(2024, 7, 16, 23, 30), Date.UTC(2026, 7, 16, 23)), true)

assert.equal(shopDiscountPercent('charm-wallet', true, 0), 42)
assert.equal(shopDiscountPercent('charm-wallet', false, 25), 25)
assert.equal(shopDiscountPercent('some-other-item', true, 25), 25)
