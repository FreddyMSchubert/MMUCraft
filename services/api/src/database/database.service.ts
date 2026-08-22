import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { Injectable, OnModuleDestroy } from '@nestjs/common';
import Database from 'better-sqlite3';
import { sql } from 'drizzle-orm';
import { drizzle, BetterSQLite3Database } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import { adoptLegacyDatabase } from './legacy-database';
import { schema, users } from './schema';

export * from './schema';

export const SUPER_ADMIN_MINECRAFT_UUID = '8580f9f830c44b83a66cac52ac6d5b0b';

@Injectable()
export class DatabaseService implements OnModuleDestroy {
	private readonly client: Database.Database;
	readonly connection: BetterSQLite3Database<typeof schema>;

	constructor() {
		const databaseUrl = process.env.DATABASE_URL ?? join(process.cwd(), 'data', 'app.sqlite');
		const migrationsFolder =
			process.env.DATABASE_MIGRATIONS_PATH ?? join(process.cwd(), 'drizzle');

		if (databaseUrl !== ':memory:') {
			mkdirSync(dirname(databaseUrl), { recursive: true });
			adoptLegacyDatabase(databaseUrl, migrationsFolder);
		}

		this.client = new Database(databaseUrl);
		this.client.pragma('journal_mode = WAL');
		this.client.pragma('foreign_keys = ON');
		this.connection = drizzle(this.client, { schema });

		migrate(this.connection, { migrationsFolder });
		this.promoteSuperAdmin();
	}

	onModuleDestroy() {
		this.client.close();
	}

	private promoteSuperAdmin() {
		this.connection.transaction((tx) => {
			tx.update(users).set({ is_super_admin: 0 }).run();
			tx.update(users)
				.set({ is_committee: 1, is_super_admin: 1 })
				.where(
					sql`lower(replace(${users.minecraft_uuid}, '-', '')) = ${SUPER_ADMIN_MINECRAFT_UUID}`,
				)
				.run();
		});
	}
}
