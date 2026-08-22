import { Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, type UserRow, emailWhitelist, users } from '../database/database.service';

@Injectable()
export class AuthUserLookupService {
	constructor(private readonly database: DatabaseService) {}

	byEmail(email: string): UserRow | null {
		return (
			this.database.connection.select().from(users).where(eq(users.email, email)).get() ??
			null
		);
	}

	byId(userId: number): UserRow | null {
		return (
			this.database.connection.select().from(users).where(eq(users.id, userId)).get() ?? null
		);
	}

	isEmailWhitelisted(email: string): boolean {
		return Boolean(
			this.database.connection
				.select({ email: emailWhitelist.email })
				.from(emailWhitelist)
				.where(eq(emailWhitelist.email, email))
				.get(),
		);
	}

	byMinecraftUsername(minecraftUsername: string): UserRow | null {
		return (
			this.database.connection
				.select()
				.from(users)
				.all()
				.find(
					(user) =>
						user.minecraft_username.localeCompare(minecraftUsername, 'en', {
							sensitivity: 'base',
						}) === 0,
				) ?? null
		);
	}

	byMinecraftUuid(minecraftUuid: string): UserRow | null {
		return (
			this.database.connection
				.select()
				.from(users)
				.where(eq(users.minecraft_uuid, minecraftUuid))
				.get() ?? null
		);
	}
}
