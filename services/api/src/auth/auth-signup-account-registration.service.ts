import { BadRequestException, ForbiddenException, Injectable, Logger } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import {
	DatabaseService,
	SUPER_ADMIN_MINECRAFT_UUID,
	emailWhitelist,
	users,
} from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { AuthGrpcService } from './auth-grpc.service';
import { AuthSessionService } from './auth-session.service';
import { AuthUserLookupService } from './auth-user-lookup.service';
import { isAllowedEmail } from './auth.util';
import { SignupFlow, signupFlows } from './signup-flow';

@Injectable()
export class AuthSignupAccountRegistrationService {
	private readonly logger = new Logger(AuthSignupAccountRegistrationService.name);

	constructor(
		private readonly database: DatabaseService,
		private readonly grpc: AuthGrpcService,
		private readonly sessions: AuthSessionService,
		private readonly userLookup: AuthUserLookupService,
	) {}

	async register(flowId: string, flow: SignupFlow) {
		const now = Date.now();
		if (flow.step !== 'rules') {
			throw new BadRequestException('This signup flow is not waiting for rules acceptance');
		}
		if (!flow.minecraftUsername) {
			throw new BadRequestException(
				'Minecraft username is not available for this signup flow',
			);
		}

		const minecraftUsername = flow.minecraftUsername;
		const minecraftUuid = normalizeMinecraftUuid(flow.minecraftUuid ?? '');
		if (!minecraftUuid) {
			throw new BadRequestException(
				'Minecraft identity is not available for this signup flow',
			);
		}
		this.assertAccountDoesNotExist(flow.email, minecraftUsername, minecraftUuid);

		const externalInvitation = isAllowedEmail(flow.email)
			? null
			: this.database.connection
					.select({ responsibleUserId: emailWhitelist.responsible_user_id })
					.from(emailWhitelist)
					.where(eq(emailWhitelist.email, flow.email))
					.get();
		if (!isAllowedEmail(flow.email) && !externalInvitation?.responsibleUserId) {
			throw new ForbiddenException('This external player invitation is no longer active');
		}

		let accountCreated = false;
		try {
			await this.grpc.whitelistPlayer(minecraftUsername);
			const userId = this.database.connection.transaction((transaction) => {
				const created = transaction
					.insert(users)
					.values({
						email: flow.email,
						minecraft_uuid: minecraftUuid,
						minecraft_username: minecraftUsername,
						responsible_user_id: externalInvitation?.responsibleUserId ?? null,
						is_committee: minecraftUuid === SUPER_ADMIN_MINECRAFT_UUID ? 1 : 0,
						is_super_admin: minecraftUuid === SUPER_ADMIN_MINECRAFT_UUID ? 1 : 0,
						whitelisted_at_unix_ms: now,
						rules_accepted_at_unix_ms: now,
						created_at_unix_ms: now,
					})
					.returning({ id: users.id })
					.get();
				return created.id;
			});
			accountCreated = true;
			signupFlows.delete(flowId);
			return this.sessions.createForUser(userId);
		} catch (error) {
			if (!accountCreated) {
				await this.grpc
					.unwhitelistPlayer(minecraftUsername)
					.catch((cleanupError: unknown) => {
						this.logger.error(
							`Could not compensate the whitelist update for ${minecraftUsername}: ${String(cleanupError)}`,
						);
					});
			}
			throw error;
		} finally {
			await this.grpc.removePendingJoin(minecraftUsername).catch(() => undefined);
		}
	}

	private assertAccountDoesNotExist(
		email: string,
		minecraftUsername: string,
		minecraftUuid: string,
	) {
		if (this.userLookup.byEmail(email)) {
			throw new BadRequestException('An account with this email already exists');
		}
		if (this.userLookup.byMinecraftUsername(minecraftUsername)) {
			throw new BadRequestException('An account with this Minecraft username already exists');
		}
		if (this.userLookup.byMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException(
				'This Minecraft account is already linked to a website account',
			);
		}
	}
}
