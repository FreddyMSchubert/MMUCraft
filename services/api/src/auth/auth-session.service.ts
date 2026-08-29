import { ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { and, eq, gt } from 'drizzle-orm';
import { DatabaseService, playerProfiles, sessions, users } from '../database/database.service';
import { effectivePlayerColor, playerSkinUrl } from '../players/player-color';
import { createOpaqueToken, hashSecret } from './auth.util';

const SESSION_TTL_MS = 60 * 24 * 60 * 60 * 1000;

export interface AuthenticatedUser {
	id: number;
	minecraftUsername: string;
	skinUrl: string | null;
	color: string;
	isMember: boolean;
	isCommittee: boolean;
	isSuperAdmin: boolean;
}

@Injectable()
export class AuthSessionService {
	constructor(private readonly database: DatabaseService) {}

	createForUser(userId: number) {
		const now = Date.now();
		const token = createOpaqueToken();
		this.database.connection
			.insert(sessions)
			.values({
				id: randomUUID(),
				user_id: userId,
				token_hash: hashSecret(token),
				expires_at_unix_ms: now + SESSION_TTL_MS,
				created_at_unix_ms: now,
			})
			.run();
		return { token, maxAgeSeconds: Math.floor(SESSION_TTL_MS / 1000) };
	}

	requireSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.getSession(rawCookieHeader);
		if (!user) throw new UnauthorizedException('Not signed in');
		return user;
	}

	requireCommitteeSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader);
		if (!user.isCommittee) throw new ForbiddenException('Committee access is required');
		return user;
	}

	requireSuperAdminSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader);
		if (!user.isSuperAdmin) throw new ForbiddenException('Super-admin access is required');
		return user;
	}

	getSession(rawCookieHeader: string | undefined): AuthenticatedUser | null {
		const token = readCookie(rawCookieHeader, 'mcstack_session');
		if (!token) return null;
		const row = this.database.connection
			.select({ user: users })
			.from(sessions)
			.innerJoin(users, eq(users.id, sessions.user_id))
			.where(
				and(
					eq(sessions.token_hash, hashSecret(token)),
					gt(sessions.expires_at_unix_ms, Date.now()),
				),
			)
			.get()?.user;
		if (!row) return null;

		const isSuperAdmin = row.is_super_admin === 1;
		const profile = this.database.connection
			.select()
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, row.id))
			.get();
		return {
			id: row.id,
			minecraftUsername: row.minecraft_username,
			skinUrl: playerSkinUrl(row.minecraft_uuid),
			color: effectivePlayerColor(row.minecraft_uuid, profile?.color_hex),
			isMember: row.is_member === 1,
			isCommittee: isSuperAdmin || row.is_committee === 1,
			isSuperAdmin,
		};
	}

	deleteSession(rawCookieHeader: string | undefined) {
		const token = readCookie(rawCookieHeader, 'mcstack_session');
		if (!token) return;
		this.database.connection
			.delete(sessions)
			.where(eq(sessions.token_hash, hashSecret(token)))
			.run();
	}
}

function readCookie(rawCookieHeader: string | undefined, name: string): string | null {
	if (!rawCookieHeader) return null;
	const match = rawCookieHeader
		.split(';')
		.map((cookie) => cookie.trim())
		.find((cookie) => cookie.startsWith(`${name}=`));
	return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}
