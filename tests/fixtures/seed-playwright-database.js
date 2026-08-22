const { createHash } = require('node:crypto');
const Database = require('better-sqlite3');

const database = new Database(process.env.DATABASE_URL);
const now = Date.now();

// Match the production session hash without importing application internals.
function sessionHash(token) {
	return createHash('sha256').update(`${process.env.AUTH_CODE_SECRET}:${token}`).digest('hex');
}

// Keep stable tokens readable in tests. Never use these values outside this database.
const users = [
	{
		id: 1,
		email: 'admin@mmu.ac.uk',
		uuid: '8580f9f830c44b83a66cac52ac6d5b0b',
		username: 'PlaywrightAdmin',
		member: 1,
		committee: 1,
		superAdmin: 1,
	},
	{
		id: 2,
		email: '12345678@stu.mmu.ac.uk',
		uuid: '0123456789abcdef0123456789abcdef',
		username: 'PlaywrightMember',
		member: 1,
		committee: 0,
		superAdmin: 0,
	},
	{
		id: 3,
		email: 'guest@example.com',
		uuid: '1123456789abcdef0123456789abcdef',
		username: 'PlaywrightGuest',
		member: 0,
		committee: 0,
		superAdmin: 0,
	},
];

const seed = database.transaction(() => {
	// Remove mutable fixture rows so an interrupted local run cannot poison the next run.
	database.prepare("DELETE FROM claim_members WHERE claim_id LIKE 'playwright-%'").run();
	database.prepare("DELETE FROM claims WHERE id LIKE 'playwright-%'").run();
	database.prepare('DELETE FROM countdowns').run();

	// Recreate only the deterministic rows that the suite owns.
	for (const user of users) {
		database
			.prepare(
				`INSERT INTO users (
					id, email, minecraft_uuid, minecraft_username, is_member, is_committee,
					is_super_admin, whitelisted_at_unix_ms, rules_accepted_at_unix_ms,
					created_at_unix_ms
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
					email = excluded.email,
					minecraft_uuid = excluded.minecraft_uuid,
					minecraft_username = excluded.minecraft_username,
					is_member = excluded.is_member,
					is_committee = excluded.is_committee,
					is_super_admin = excluded.is_super_admin`,
			)
			.run(
				user.id,
				user.email,
				user.uuid,
				user.username,
				user.member,
				user.committee,
				user.superAdmin,
				now,
				now,
				now,
			);
	}

	for (const [userId, token] of [
		[1, 'playwright-admin'],
		[2, 'playwright-member'],
		[3, 'playwright-guest'],
	]) {
		database
			.prepare(
				`INSERT INTO sessions (
					id, user_id, token_hash, expires_at_unix_ms, created_at_unix_ms
				) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
					token_hash = excluded.token_hash,
					expires_at_unix_ms = excluded.expires_at_unix_ms`,
			)
			.run(`playwright-session-${userId}`, userId, sessionHash(token), now + 86_400_000, now);
	}

	// Give read endpoints enough real data to prove their joins and serialization.
	database
		.prepare(
			`INSERT INTO player_profiles (
				user_id, preferred_name, pronouns, course_year, discord_username,
				base_x, base_y, base_z, bio, color_hex, show_death_counter, updated_at_unix_ms
			) VALUES (2, 'PW Member', 'they/them', '2', 'pw_member', 10, 64, -10,
				'Fixture-backed member', '#336699', 1, ?)
			ON CONFLICT(user_id) DO UPDATE SET
				preferred_name = excluded.preferred_name,
				pronouns = excluded.pronouns,
				bio = excluded.bio,
				color_hex = excluded.color_hex,
				updated_at_unix_ms = excluded.updated_at_unix_ms`,
		)
		.run(now);

	database
		.prepare(
			`INSERT INTO claims (
				id, owner_user_id, dimension, chunk_x, chunk_z, claim_name, color_hex,
				created_at_unix_ms
			) VALUES ('playwright-claim', 2, 'minecraft:overworld', 12, -4,
				'Fixture claim', '#336699', ?)
			ON CONFLICT(id) DO UPDATE SET
				claim_name = excluded.claim_name,
				color_hex = excluded.color_hex`,
		)
		.run(now);

	database
		.prepare(
			`INSERT INTO player_stats (user_id, stats_json, updated_at_unix_ms)
			VALUES (2, '{"minecraft":{"stats":{}},"money":{"balanceDabloons":250}}', ?)
			ON CONFLICT(user_id) DO UPDATE SET
				stats_json = excluded.stats_json,
				updated_at_unix_ms = excluded.updated_at_unix_ms`,
		)
		.run(now);

	database
		.prepare(
			`INSERT INTO countdowns (
				id, heading, description, heading_color, description_color,
				background_color, background_alpha, background_image_url,
				target_at_unix_ms, visible_until_unix_ms, position
			) VALUES (1, 'Fixture countdown', 'Seeded for Playwright', '#ffffff', '#eeeeee',
				'#000000', 80, NULL, ?, ?, 0)
			ON CONFLICT(id) DO UPDATE SET
				heading = excluded.heading,
				description = excluded.description,
				target_at_unix_ms = excluded.target_at_unix_ms,
				visible_until_unix_ms = excluded.visible_until_unix_ms`,
		)
		.run(now + 3_600_000, now + 7_200_000);
});

try {
	seed();
} finally {
	database.close();
}
