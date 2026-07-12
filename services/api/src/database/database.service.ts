import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { Injectable, OnModuleDestroy } from '@nestjs/common'
import Database from 'better-sqlite3'
import { eq } from 'drizzle-orm'
import { drizzle, BetterSQLite3Database } from 'drizzle-orm/better-sqlite3'
import { migrate } from 'drizzle-orm/better-sqlite3/migrator'
import { adoptLegacyDatabase } from './legacy-database'
import { schema, users } from './schema'

export * from './schema'

@Injectable()
export class DatabaseService implements OnModuleDestroy {
	private readonly client: Database.Database
	readonly connection: BetterSQLite3Database<typeof schema>

	constructor() {
		const databaseUrl = process.env.DATABASE_URL ?? join(process.cwd(), 'data', 'app.sqlite')
		const migrationsFolder = process.env.DATABASE_MIGRATIONS_PATH ?? join(process.cwd(), 'drizzle')

		if (databaseUrl !== ':memory:') {
			mkdirSync(dirname(databaseUrl), { recursive: true })
			adoptLegacyDatabase(databaseUrl, migrationsFolder)
		}

		this.client = new Database(databaseUrl)
		this.client.pragma('journal_mode = WAL')
		this.client.pragma('foreign_keys = ON')
		this.connection = drizzle(this.client, { schema })

		migrate(this.connection, { migrationsFolder })
		this.promoteSuperAdmin()
	}

	onModuleDestroy() {
		this.client.close()
	}

	private promoteSuperAdmin() {
		const superAdmin = this.connection.select().from(users).all()
			.find((user) => user.minecraft_username.localeCompare('MerlinSpace', 'en', { sensitivity: 'base' }) === 0)

		if (superAdmin) {
			this.connection.update(users)
				.set({ is_committee: 1, is_super_admin: 1 })
				.where(eq(users.id, superAdmin.id))
				.run()
		}
	}
}
