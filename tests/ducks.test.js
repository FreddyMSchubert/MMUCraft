const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const path = require('node:path');

process.env.DATABASE_URL = ':memory:';
process.env.DATABASE_MIGRATIONS_PATH = path.resolve(__dirname, '../services/api/drizzle');

const {
	DatabaseService,
	duckCommitteeKills,
	users,
} = require('../services/api/dist/database/database.service');
const {
	MinecraftIdentityService,
} = require('../services/api/dist/database/minecraft-identity.service');
const { DucksService } = require('../services/api/dist/ducks/ducks.service');
const { GameplayGrpcService } = require('../services/api/dist/gameplay/gameplay-grpc.service');
const {
	ShopItemCatalogService,
} = require('../services/api/dist/gameplay/shop/shop-item-catalog.service');
const { DUCKS } = require('../services/api/dist/ducks/duck-catalog');

test('duck titles come from item definitions', () => {
	const catalog = new ShopItemCatalogService();
	for (const duck of DUCKS) {
		const file = path.resolve(
			__dirname,
			'../minecraft/main/data/data/items/ducks',
			duck.itemId,
			'item.json',
		);
		assert.equal(catalog.itemTitle(duck.itemId), JSON.parse(readFileSync(file, 'utf8')).title);
	}
});

test('Discord join webhook completes before queued duck announcements', async () => {
	const order = [];
	const service = {
		playerPresence: { recordPresenceEvent: () => {} },
		discord: {
			publish: async () => {
				order.push('join');
				return true;
			},
		},
		ducks: { afterDiscordJoin: async () => order.push('duck') },
	};
	await new Promise((resolve, reject) => {
		GameplayGrpcService.prototype.publishDiscordEvent.call(
			service,
			{ request: { type: 'join', minecraft_uuid: '11111111111111111111111111111111' } },
			(error) => (error ? reject(error) : resolve()),
		);
	});
	assert.deepEqual(order, ['join', 'duck']);
});

test('Hellish toggle blocks duck access and awards before the drop', async () => {
	let netherOpen = false;
	const service = new DucksService(null, null, null, null, null, {
		enabledKeys: () => new Set(netherOpen ? ['nether'] : []),
	});
	assert.equal(service.isOpen(), false);
	assert.deepEqual(service.onPlayerSync('uuid', 'Player', 0, true, true), {
		count: 0,
		newAwards: [],
		pending: [],
	});
	assert.equal((await service.recordEvent('uuid', 'Player', 4)).count, 0);
	assert.throws(() => service.getForUser(1));
	await assert.rejects(() => service.setAward(1, 3, true));
	await assert.rejects(() => service.purchase({ id: 1 }, 1));
	netherOpen = true;
	assert.equal(service.isOpen(), true);
	await assert.rejects(() => service.recordEvent('uuid', 'Player', 12));
	await assert.rejects(() => service.recordEvent('uuid', 'Player', 6));
});

test('duck awards, delivery, revocation and final completion stay consistent', async () => {
	const database = new DatabaseService();
	const uuid = '1234567890abcdef1234567890abcdef';
	database.connection
		.insert(users)
		.values({
			email: 'duck@example.test',
			minecraft_uuid: uuid,
			minecraft_username: 'DuckTester',
			is_member: 1,
			whitelisted_at_unix_ms: 0,
			rules_accepted_at_unix_ms: 0,
			created_at_unix_ms: 0,
		})
		.run();
	const player = database.connection.select().from(users).get();
	const pushes = [];
	const discordEvents = [];
	let netherOpen = false;
	const service = new DucksService(
		database,
		{ resolveAndRefresh: () => player },
		{
			gameplay: async (method, request) => {
				pushes.push({ method, request });
				return method === 'PurchaseShopItem' ? { purchased: true } : { applied: true };
			},
		},
		{
			publish: async (event) => {
				discordEvents.push(event);
				return true;
			},
		},
		{ itemAsset: () => null, itemTitle: (id) => id },
		{ enabledKeys: () => new Set(netherOpen ? ['nether'] : []) },
	);
	try {
		assert.equal(service.isOpen(), false);
		assert.deepEqual(service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, true, true), {
			count: 0,
			newAwards: [],
			pending: [],
		});
		assert.equal((await service.recordEvent(uuid, 'DuckTester', 4)).count, 0);
		assert.throws(() => service.getForUser(player.id));
		await assert.rejects(() => service.setAward(player.id, 3, true));
		assert.equal(discordEvents.length, 0);
		assert.equal(pushes.length, 0);
		netherOpen = true;
		assert.equal(service.isOpen(), true);
		assert.deepEqual(
			service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20 - 1, false, false)
				.newAwards,
			[],
		);
		const online = service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, false, false);
		assert.deepEqual(
			online.newAwards.map((award) => award.duck_id),
			[7],
		);
		const login = service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, true, true);
		assert.deepEqual(
			login.newAwards.map((award) => award.duck_id),
			[1, 2],
		);
		assert.equal(login.count, 3);
		assert.equal(login.pending.length, 3);
		assert.equal(
			service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, false, false).newAwards
				.length,
			0,
		);
		assert.equal(
			service.markDelivered(uuid, 'DuckTester', 1, login.newAwards[0].awarded_at_unix_ms),
			true,
		);
		assert.equal(service.getForUser(player.id).count, 3);
		await service.setAward(player.id, 1, false);
		assert.equal(
			service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, false, false).newAwards
				.length,
			0,
		);
		assert.deepEqual(
			service
				.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, true, false)
				.newAwards.map((award) => award.duck_id),
			[1],
		);

		for (const id of [3, 4, 5, 6, 8, 9, 10, 11, 12]) {
			await service.setAward(player.id, id, true);
		}
		assert.equal(service.getForUser(player.id).count, 13);
		assert.equal(service.getForUser(player.id).rank, 'Duckiversal Grandmaster');
		await service.purchase({ id: player.id, minecraftUsername: 'DuckTester' }, 1);
		await service.purchase({ id: player.id, minecraftUsername: 'DuckTester' }, 13);
		assert.deepEqual(
			pushes
				.filter(({ method }) => method === 'PurchaseShopItem')
				.map(({ request }) => request.price_dabloons),
			[5, 10],
		);
		assert.ok(
			pushes.some(({ request }) => request.new_awards?.some((award) => award.duck_id === 13)),
		);

		await service.setAward(player.id, 3, false);
		assert.equal(service.getForUser(player.id).count, 12);
		assert.equal(service.getForUser(player.id).ducks[12].unlocked, true);
		await service.setAward(player.id, 3, true);
		assert.equal(service.getForUser(player.id).count, 13);
		netherOpen = false;
		assert.equal(
			service.onPlayerSync(uuid, 'DuckTester', 168 * 60 * 60 * 20, true, false).count,
			0,
		);
		assert.equal((await service.recordEvent(uuid, 'DuckTester', 4)).count, 0);
	} finally {
		database.onModuleDestroy();
	}
});

test('only Sunday committee kills count, including advancement backfill across Sundays', async () => {
	const database = new DatabaseService();
	const sunday = Date.parse('2026-07-04T23:30:00Z');
	const monday = Date.parse('2026-07-05T23:30:00Z');
	const nextSunday = Date.parse('2026-07-12T12:00:00Z');
	const players = [
		{
			email: 'hunter@example.test',
			name: 'DuckHunter',
			uuid: '11111111111111111111111111111111',
		},
		{
			email: 'a@example.test',
			name: 'CommitteeA',
			uuid: '22222222222222222222222222222222',
			committee: 1,
		},
		{
			email: 'b@example.test',
			name: 'CommitteeB',
			uuid: '33333333333333333333333333333333',
			committee: 1,
		},
	];
	for (const player of players) {
		database.connection
			.insert(users)
			.values({
				email: player.email,
				minecraft_username: player.name,
				minecraft_uuid: player.uuid,
				is_committee: player.committee ?? 0,
				whitelisted_at_unix_ms: 0,
				rules_accepted_at_unix_ms: 0,
				created_at_unix_ms: 0,
			})
			.run();
	}
	const records = database.connection.select().from(users).all();
	const hunter = records.find((row) => row.minecraft_username === players[0].name);
	const committeeB = records.find((row) => row.minecraft_username === players[2].name);
	database.connection
		.insert(duckCommitteeKills)
		.values({
			killer_user_id: hunter.id,
			victim_user_id: committeeB.id,
			killed_at_unix_ms: monday,
		})
		.run();
	const discordEvents = [];
	const service = new DucksService(
		database,
		new MinecraftIdentityService(database),
		{ gameplay: async () => ({ applied: true }) },
		{
			publish: async (event) => {
				discordEvents.push(event.type);
				return true;
			},
		},
		{
			itemAsset: (id) => ({ modelUrl: id, textureUrl: id }),
			itemTitle: (id) => id,
		},
		{ enabledKeys: () => new Set(['nether']) },
	);
	try {
		const login = service.onPlayerSync(players[0].uuid, players[0].name, 0, true, false, [
			{ username: players[1].name, obtainedAtUnixMs: sunday },
			{ username: players[2].name, obtainedAtUnixMs: monday },
		]);
		assert.deepEqual(
			login.newAwards.map((award) => award.duck_id),
			[1],
		);
		assert.deepEqual(discordEvents, []);
		discordEvents.push('join');
		await service.afterDiscordJoin(players[0].uuid);
		assert.deepEqual(discordEvents, ['join', 'duck']);
		assert.equal(
			service.getForUser(database.connection.select().from(users).get().id).ducks[3].modelUrl,
			'duck-rubber',
		);

		assert.deepEqual(
			(
				await service.recordEvent(
					players[0].uuid,
					players[0].name,
					4,
					players[1].uuid,
					sunday,
				)
			).newAwards,
			[],
		);
		assert.deepEqual(
			(
				await service.recordEvent(
					players[0].uuid,
					players[0].name,
					4,
					players[2].uuid,
					monday,
				)
			).newAwards,
			[],
		);
		await assert.rejects(() =>
			service.recordEvent(players[0].uuid, players[0].name, 4, players[2].uuid),
		);
		const finalKill = await service.recordEvent(
			players[0].uuid,
			players[0].name,
			4,
			players[2].uuid,
			nextSunday,
		);
		assert.deepEqual(
			finalKill.newAwards.map((award) => award.duck_id),
			[4],
		);
		assert.equal(finalKill.count, 2);
		await assert.rejects(() => service.recordEvent(players[0].uuid, players[0].name, 12));
		await assert.rejects(() => service.recordEvent(players[0].uuid, players[0].name, 6));
	} finally {
		database.onModuleDestroy();
	}
});
