import { existsSync, renameSync, statSync } from 'node:fs';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import { integer, sqliteTable, text } from 'drizzle-orm/sqlite-core';
import {
	dailyAdvancementTargets,
	dailyClaims,
	dailyTasks,
	giftCodeRedemptions,
	giftCodes,
	knowledgeUnlocks,
	playerMoneyEvents,
	playerProfiles,
	playerStats,
	schema,
	sessions,
	shopUnlocks,
	users,
} from './schema';

const drizzleMigrations = sqliteTable('__drizzle_migrations', {
	id: integer('id').primaryKey(),
	hash: text('hash').notNull(),
	created_at: integer('created_at'),
});

export function adoptLegacyDatabase(databasePath: string, migrationsFolder: string) {
	if (
		!existsSync(databasePath) ||
		statSync(databasePath).size === 0 ||
		isDrizzleDatabase(databasePath)
	) {
		return;
	}

	const backupPath = `${databasePath}.pre-drizzle-${Date.now()}`;
	moveDatabaseFiles(databasePath, backupPath);

	try {
		const targetClient = new Database(databasePath);
		try {
			targetClient.pragma('journal_mode = WAL');
			targetClient.pragma('foreign_keys = ON');
			const target = drizzle(targetClient, { schema });
			migrate(target, { migrationsFolder });
			copyLegacyRows(backupPath, target);
		} finally {
			targetClient.close();
		}
	} catch (error) {
		const failedPath = `${databasePath}.failed-drizzle-${Date.now()}`;
		if (existsSync(databasePath)) {
			moveDatabaseFiles(databasePath, failedPath);
		}
		moveDatabaseFiles(backupPath, databasePath);
		throw error;
	}
}

function isDrizzleDatabase(databasePath: string) {
	const client = new Database(databasePath, { readonly: true, fileMustExist: true });
	try {
		const database = drizzle(client);
		try {
			const appliedMigration = database
				.select({ id: drizzleMigrations.id })
				.from(drizzleMigrations)
				.get();
			if (appliedMigration) {
				return true;
			}
		} catch {
			// The journal does not exist on databases created before Drizzle.
		}

		try {
			database.select({ id: users.id }).from(users).get();
			return false;
		} catch {
			return true;
		}
	} finally {
		client.close();
	}
}

function copyLegacyRows(legacyPath: string, target: ReturnType<typeof drizzle<typeof schema>>) {
	const legacyClient = new Database(legacyPath, { readonly: true, fileMustExist: true });
	try {
		const legacy = drizzle(legacyClient, { schema });
		const userRows = readWithFallback(
			() => legacy.select().from(users).all(),
			() =>
				legacy
					.select({
						id: users.id,
						email: users.email,
						minecraft_username: users.minecraft_username,
						whitelisted_at_unix_ms: users.whitelisted_at_unix_ms,
						rules_accepted_at_unix_ms: users.rules_accepted_at_unix_ms,
						created_at_unix_ms: users.created_at_unix_ms,
					})
					.from(users)
					.all()
					.map((row) => ({ ...row, is_member: 0, is_committee: 0 })),
		);

		const profileRows = readOptional(() =>
			readWithFallback(
				() => legacy.select().from(playerProfiles).all(),
				() =>
					readWithFallback(
						() =>
							legacy
								.select({
									user_id: playerProfiles.user_id,
									preferred_name: playerProfiles.preferred_name,
									pronouns: playerProfiles.pronouns,
									course_year: playerProfiles.course_year,
									discord_username: playerProfiles.discord_username,
									base_x: playerProfiles.base_x,
									base_y: playerProfiles.base_y,
									base_z: playerProfiles.base_z,
									bio: playerProfiles.bio,
									color_hex: playerProfiles.color_hex,
									updated_at_unix_ms: playerProfiles.updated_at_unix_ms,
								})
								.from(playerProfiles)
								.all()
								.map((row) => ({ ...row, show_death_counter: 1 })),
						() =>
							legacy
								.select({
									user_id: playerProfiles.user_id,
									preferred_name: playerProfiles.preferred_name,
									course_year: playerProfiles.course_year,
									discord_username: playerProfiles.discord_username,
									base_x: playerProfiles.base_x,
									base_y: playerProfiles.base_y,
									base_z: playerProfiles.base_z,
									bio: playerProfiles.bio,
									updated_at_unix_ms: playerProfiles.updated_at_unix_ms,
								})
								.from(playerProfiles)
								.all()
								.map((row) => ({
									...row,
									pronouns: '',
									color_hex: null,
									show_death_counter: 1,
								})),
					),
			),
		);

		const giftRows = readOptional(() =>
			readWithFallback(
				() => legacy.select().from(giftCodes).all(),
				() =>
					legacy
						.select({
							code: giftCodes.code,
							amount_dabloons: giftCodes.amount_dabloons,
							created_by_user_id: giftCodes.created_by_user_id,
							created_at_unix_ms: giftCodes.created_at_unix_ms,
							redeemed_by_user_id: giftCodes.redeemed_by_user_id,
							redeemed_at_unix_ms: giftCodes.redeemed_at_unix_ms,
						})
						.from(giftCodes)
						.all()
						.map((row) => ({
							...row,
							redemption_mode: 'single' as const,
							members_only: 0,
							expires_at_unix_ms: null,
						})),
			),
		).map((row) => ({ ...row, code: row.code.toLowerCase() }));

		const redemptionRows = readOptional(() =>
			legacy.select().from(giftCodeRedemptions).all(),
		).map((row) => ({ ...row, code: row.code.toLowerCase() }));

		target.transaction((tx) => {
			insertRows(tx, users, userRows);
			insertRows(
				tx,
				sessions,
				readOptional(() => legacy.select().from(sessions).all()),
			);
			insertRows(tx, playerProfiles, profileRows);
			insertRows(
				tx,
				playerStats,
				readOptional(() => legacy.select().from(playerStats).all()),
			);
			insertRows(
				tx,
				playerMoneyEvents,
				readOptional(() => legacy.select().from(playerMoneyEvents).all()),
			);
			insertRows(
				tx,
				knowledgeUnlocks,
				readOptional(() => legacy.select().from(knowledgeUnlocks).all()),
			);
			insertRows(
				tx,
				shopUnlocks,
				readOptional(() => legacy.select().from(shopUnlocks).all()),
			);
			insertRows(
				tx,
				dailyClaims,
				readOptional(() => legacy.select().from(dailyClaims).all()),
			);
			insertRows(
				tx,
				dailyAdvancementTargets,
				readOptional(() => legacy.select().from(dailyAdvancementTargets).all()),
			);
			insertRows(
				tx,
				dailyTasks,
				readOptional(() => legacy.select().from(dailyTasks).all()),
			);
			insertRows(tx, giftCodes, giftRows);
			insertRows(tx, giftCodeRedemptions, redemptionRows);

			for (const gift of giftRows) {
				if (gift.redeemed_by_user_id !== null && gift.redeemed_at_unix_ms !== null) {
					tx.insert(giftCodeRedemptions)
						.values({
							code: gift.code,
							user_id: gift.redeemed_by_user_id,
							redeemed_at_unix_ms: gift.redeemed_at_unix_ms,
						})
						.onConflictDoNothing()
						.run();
				}
			}
		});
	} finally {
		legacyClient.close();
	}
}

function insertRows(
	database: Parameters<
		Parameters<ReturnType<typeof drizzle<typeof schema>>['transaction']>[0]
	>[0],
	table: Parameters<typeof database.insert>[0],
	rows: Record<string, unknown>[],
) {
	for (const row of rows) {
		database.insert(table).values(row).run();
	}
}

function readWithFallback<T>(read: () => T[], fallback: () => T[]): T[] {
	try {
		return read();
	} catch {
		return fallback();
	}
}

function readOptional<T>(read: () => T[]): T[] {
	try {
		return read();
	} catch {
		return [];
	}
}

function moveDatabaseFiles(from: string, to: string) {
	renameSync(from, to);
	for (const suffix of ['-wal', '-shm']) {
		if (existsSync(`${from}${suffix}`)) {
			renameSync(`${from}${suffix}`, `${to}${suffix}`);
		}
	}
}
