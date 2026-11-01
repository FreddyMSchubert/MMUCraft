const assert = require('node:assert/strict');
const { test } = require('node:test');
const { join } = require('node:path');
const { eq } = require('../services/api/node_modules/drizzle-orm');

process.env.DATABASE_URL = ':memory:';
process.env.DATABASE_MIGRATIONS_PATH = join(__dirname, '../services/api/drizzle');

const {
	DatabaseService,
	externalMembershipPurchases,
	users,
} = require('../services/api/dist/database/database.service');
const { AuthSessionService } = require('../services/api/dist/auth/auth-session.service');
const {
	ExternalMembershipService,
} = require('../services/api/dist/gifts/external-membership.service');
const {
	PlayerRoleAdministrationService,
} = require('../services/api/dist/gifts/player-role-administration.service');

test('only an external invited by a current member can buy membership once', async () => {
	const database = new DatabaseService();
	try {
		const now = Date.now();
		for (const [id, member, inviter] of [
			[1, 1, null],
			[2, 0, 1],
			[3, 0, null],
			[4, 0, 3],
			[5, 0, 1],
			[6, 0, 1],
		]) {
			database.connection
				.insert(users)
				.values({
					id,
					email: `player${id}@example.com`,
					minecraft_username: `Player${id}`,
					responsible_user_id: inviter,
					is_member: member,
					whitelisted_at_unix_ms: now,
					rules_accepted_at_unix_ms: now,
					created_at_unix_ms: now,
				})
				.run();
		}
		const sessions = new AuthSessionService(database);
		const session = (id) =>
			sessions.requireSession(`mcstack_session=${sessions.createForUser(id).token}`);
		const invited = session(2);
		assert.equal(invited.canBecomeMember, true);
		assert.equal(session(3).canBecomeMember, false);
		assert.equal(session(4).canBecomeMember, false);

		let purchases = 0;
		let release;
		const gate = new Promise((resolve) => {
			release = resolve;
		});
		const grants = [];
		const minecraft = {
			async gameplay(method, request) {
				assert.equal(method, 'PurchaseExternalMembership');
				if (request.minecraft_username === 'Player5')
					return {
						purchased: false,
						message: 'You need 300 Dabloons to buy membership.',
					};
				if (request.minecraft_username === 'Player6') throw new Error('Lost reply');
				assert.equal(request.minecraft_username, 'Player2');
				purchases++;
				await gate;
				return { purchased: true, balance_dabloons: 25 };
			},
			tryGrantAdvancement: async (...args) => {
				grants.push(args);
			},
		};
		const ducks = {
			awardMembership: async (id) => {
				grants.push(['duck', id]);
			},
		};
		const players = {
			synchronizePlayerPresentation: async (id) => {
				grants.push(['presentation', id]);
			},
		};
		const roles = new PlayerRoleAdministrationService(database, {}, players, minecraft, ducks);
		const membership = new ExternalMembershipService(database, minecraft, roles);
		await assert.rejects(membership.purchase(session(4)), { status: 403 });
		database.connection.update(users).set({ is_member: 0 }).where(eq(users.id, 1)).run();
		await assert.rejects(membership.purchase(invited), { status: 403 });
		database.connection.update(users).set({ is_member: 1 }).where(eq(users.id, 1)).run();
		await assert.rejects(membership.purchase(session(5)), { status: 400 });
		await assert.rejects(membership.purchase(session(5)), { status: 400 });
		await assert.rejects(membership.purchase(session(6)), { status: 503 });
		await assert.rejects(
			new ExternalMembershipService(database, minecraft, roles).purchase(session(6)),
			{ status: 409 },
		);
		assert.equal(
			database.connection.select().from(externalMembershipPurchases).all().length,
			1,
		);
		assert.equal(
			database.connection.select().from(users).where(eq(users.id, 5)).get().is_member,
			0,
		);
		const first = membership.purchase(invited);
		await assert.rejects(membership.purchase(invited), { status: 409 });
		release();
		assert.deepEqual(await first, { ok: true, balanceDabloons: 25 });
		assert.equal(purchases, 1);
		const updated = database.connection.select().from(users).where(eq(users.id, 2)).get();
		assert.equal(updated.is_member, 1);
		assert.equal(updated.responsible_user_id, 1);
		assert.equal(session(2).canBecomeMember, false);
		await assert.rejects(membership.purchase(invited), { status: 403 });
		assert.equal(purchases, 1);
		assert.ok(grants.some(([kind]) => kind === 'duck'));
		assert.ok(grants.some(([kind]) => kind === 'presentation'));
		assert.ok(
			grants.some(
				([, username, path]) => username === 'Player2' && path === 'social/pay_to_win',
			),
		);
	} finally {
		database.onModuleDestroy();
	}
});
