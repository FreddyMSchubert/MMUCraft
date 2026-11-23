const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');

process.env.DATABASE_URL = ':memory:';
process.env.DATABASE_MIGRATIONS_PATH = path.resolve(__dirname, '../services/api/drizzle');

const {
	DatabaseService,
	SUPER_ADMIN_MINECRAFT_UUID,
	users,
} = require('../services/api/dist/database/database.service');
const { PresentsService } = require('../services/api/dist/presents/presents.service');

test('Christmas letters enforce the shared daily limit and deliver one stack once', async () => {
	const database = new DatabaseService();
	for (const [name, id] of [
		['Alice', '1'.repeat(32)],
		['Bob', '2'.repeat(32)],
		['MMUadmin', SUPER_ADMIN_MINECRAFT_UUID],
	]) {
		database.connection
			.insert(users)
			.values({
				email: `${name.toLowerCase()}@example.test`,
				minecraft_uuid: id,
				minecraft_username: name,
				whitelisted_at_unix_ms: 0,
				rules_accepted_at_unix_ms: 0,
				created_at_unix_ms: 0,
			})
			.run();
	}
	const alice = { id: 1, minecraftUsername: 'Alice' };
	const bob = { id: 2, minecraftUsername: 'Bob' };
	const receipts = new Set();
	const deliveries = [];
	let captures = 0;
	const service = new PresentsService(database, {
		gameplay: async (method, request) => {
			if (method === 'CapturePresentHand') {
				captures++;
				return {
					success: true,
					item_data: Buffer.from(`stack-${captures}`),
					item_name: 'Gift box',
					item_count: 4,
				};
			}
			if (method === 'DeliverPresentItem') {
				deliveries.push(request);
				const already_delivered = receipts.has(request.receipt_id);
				receipts.add(request.receipt_id);
				return { delivered: !already_delivered, already_delivered };
			}
			throw new Error(method);
		},
	});
	try {
		service.setClock('2026-12-01T12:00');
		for (let i = 0; i < 3; i++) {
			service.saveDraft(alice, { recipient: 'Bob', anonymous: i === 0, title: '' });
			await service.capture(alice);
			service.send(alice);
		}
		assert.equal(service.get(alice).remainingToday, 0);
		service.saveDraft(alice, { recipient: 'Bob', anonymous: false, title: '' });
		await service.capture(alice);
		assert.throws(() => service.send(alice), /three letters/);
		await service.takeOut(alice);
		assert.equal(receipts.size, 1);
		service.setClock('2026-12-25T08:59');
		assert.equal(service.get(bob).incoming.length, 0);
		service.setClock('2026-12-25T09:00');
		const incoming = service.get(bob).incoming;
		assert.equal(incoming.length, 3);
		assert.equal(incoming[0].from, null);
		assert.equal(incoming[0].title, 'A Christmas letter');
		assert.equal(incoming[0].itemName, null);
		assert.equal(service.open(bob, incoming[0].id).firstOpen, true);
		assert.equal(service.open(bob, incoming[0].id).firstOpen, false);
		await service.claim(bob, incoming[0].id);
		await assert.rejects(service.claim(bob, incoming[0].id), /already received/);
		assert.equal(receipts.size, 2);
		const second = incoming[1];
		await service.adminAction(second.id, 'copy');
		assert.equal(deliveries.at(-1).minecraft_username, 'MMUadmin');
		assert.equal(
			service.admin().presents.find((present) => present.id === second.id).adminCopyAt > 0,
			true,
		);
		await assert.rejects(service.adminAction(second.id, 'copy'), /already has a copy/);
		await service.adminAction(second.id, 'delete');
		assert.equal(service.get(bob).incoming.length, 2);
		await service.adminAction(second.id, 'undo');
		assert.equal(service.get(bob).incoming.length, 3);
		await service.adminAction(incoming[2].id, 'return', 'Unsafe item');
		assert.equal(service.get(bob).incoming.length, 2);
		assert.equal(service.get(alice).returned.at(-1).reason, 'Unsafe item');
	} finally {
		database.onModuleDestroy();
	}
});

test('an unsent captured stack returns after the cutoff', async () => {
	const database = new DatabaseService();
	database.connection
		.insert(users)
		.values({
			email: 'sender@example.test',
			minecraft_uuid: '3'.repeat(32),
			minecraft_username: 'Sender',
			whitelisted_at_unix_ms: 0,
			rules_accepted_at_unix_ms: 0,
			created_at_unix_ms: 0,
		})
		.run();
	const sender = { id: 1, minecraftUsername: 'Sender' };
	const deliveries = [];
	const service = new PresentsService(database, {
		gameplay: async (method, request) => {
			if (method === 'CapturePresentHand')
				return {
					success: true,
					item_data: Buffer.from('exact-stack'),
					item_name: 'Signed book',
					item_count: 1,
				};
			if (method === 'DeliverPresentItem') {
				deliveries.push(request);
				return { delivered: true, already_delivered: false };
			}
			throw new Error(method);
		},
	});
	try {
		service.setClock('2026-12-24T23:59');
		await service.capture(sender);
		assert.equal(service.get(sender).draft.itemName, 'Signed book');
		service.setClock('2026-12-25T09:00');
		await new Promise(setImmediate);
		assert.equal(service.get(sender).draft, null);
		assert.equal(deliveries.length, 1);
		assert.equal(deliveries[0].minecraft_username, 'Sender');
		assert.equal(Buffer.from(deliveries[0].item_data).toString(), 'exact-stack');
	} finally {
		database.onModuleDestroy();
	}
});
