const assert = require('node:assert/strict');
const { test } = require('node:test');
const { join } = require('node:path');
const { eq } = require('../services/api/node_modules/drizzle-orm');

process.env.DATABASE_URL = ':memory:';
process.env.DATABASE_MIGRATIONS_PATH = join(__dirname, '../services/api/drizzle');

const { DatabaseService, users } = require('../services/api/dist/database/database.service');
const { MembershipImportService } = require('../services/api/dist/gifts/membership-import.service');
const {
	AuthSignupAccountRegistrationService,
} = require('../services/api/dist/auth/auth-signup-account-registration.service');

test('pending membership from CSV becomes membership on signup; existing accounts update now', async () => {
	const database = new DatabaseService();
	try {
		const roles = {
			async setMembership(id) {
				database.connection
					.update(users)
					.set({ is_member: 1 })
					.where(eq(users.id, Number(id)))
					.run();
			},
		};
		const importer = new MembershipImportService(
			database,
			{
				async membershipRoleContext() {
					throw new Error('Discord unavailable');
				},
			},
			roles,
		);
		const applied = await importer.apply(
			[{ email: 'future@mmu.ac.uk', discordName: '' }],
			[0],
			false,
		);
		assert.equal(applied.rows[0].apiStatus, 'pending signup');
		assert.deepEqual(
			importer.listPending().emails.map((entry) => entry.email),
			['future@mmu.ac.uk'],
		);

		const registration = new AuthSignupAccountRegistrationService(
			database,
			{ createForUser: (id) => ({ id }) },
			{ byEmail: () => null, byMinecraftUsername: () => null, byMinecraftUuid: () => null },
		);
		registration.register('test-flow', {
			step: 'rules',
			email: 'future@mmu.ac.uk',
			minecraftUsername: 'FuturePlayer',
			minecraftUuid: '0123456789abcdef0123456789abcdef',
		});
		assert.equal(
			database.connection
				.select()
				.from(users)
				.where(eq(users.email, 'future@mmu.ac.uk'))
				.get().is_member,
			1,
		);
		assert.equal(importer.listPending().emails.length, 0);

		const now = Date.now();
		database.connection
			.insert(users)
			.values({
				email: 'existing@mmu.ac.uk',
				minecraft_username: 'ExistingPlayer',
				whitelisted_at_unix_ms: now,
				rules_accepted_at_unix_ms: now,
				created_at_unix_ms: now,
			})
			.run();
		assert.equal((await importer.addEmail('EXISTING@MMU.AC.UK')).status, 'updated');
		assert.equal(
			database.connection
				.select()
				.from(users)
				.where(eq(users.email, 'existing@mmu.ac.uk'))
				.get().is_member,
			1,
		);
	} finally {
		database.onModuleDestroy();
	}
});
