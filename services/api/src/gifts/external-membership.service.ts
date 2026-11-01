import {
	BadRequestException,
	ConflictException,
	ForbiddenException,
	Injectable,
	ServiceUnavailableException,
} from '@nestjs/common';
import { eq } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import { DatabaseService, externalMembershipPurchases, users } from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { PlayerRoleAdministrationService } from './player-role-administration.service';

@Injectable()
export class ExternalMembershipService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly roles: PlayerRoleAdministrationService,
	) {}

	async purchase(user: AuthenticatedUser) {
		if (!user.canBecomeMember || !this.isEligible(user.id))
			throw new ForbiddenException(
				'Membership is available to external players invited by a member.',
			);
		const reservation = this.database.connection
			.insert(externalMembershipPurchases)
			.values({ user_id: user.id, started_at_unix_ms: Date.now() })
			.onConflictDoNothing()
			.run();
		if (reservation.changes !== 1)
			throw new ConflictException(
				'A membership payment is already pending. Contact committee before trying again.',
			);
		const result = await this.minecraft
			.gameplay<{
				purchased: boolean;
				balance_dabloons: number;
				message: string;
			}>('PurchaseExternalMembership', { minecraft_username: user.minecraftUsername })
			.catch(() => {
				throw new ServiceUnavailableException(
					'Could not confirm the payment. Check your balance and membership status before trying again. Contact committee if 300 Dabloons were taken.',
				);
			});
		if (!result.purchased) {
			this.release(user.id);
			throw new BadRequestException(
				result.message || 'Join the Minecraft server with 300 Dabloons to buy membership.',
			);
		}
		try {
			await this.roles.setMembership(String(user.id), true);
		} catch (error) {
			if (!this.isMember(user.id))
				throw new ServiceUnavailableException(
					'The Dabloons were taken, but membership could not be activated. Contact committee before trying again.',
					{ cause: error },
				);
		}
		this.release(user.id);
		return { ok: true, balanceDabloons: result.balance_dabloons };
	}

	private release(userId: number) {
		this.database.connection
			.delete(externalMembershipPurchases)
			.where(eq(externalMembershipPurchases.user_id, userId))
			.run();
	}

	private isEligible(userId: number) {
		const account = this.database.connection
			.select({
				isMember: users.is_member,
				inviterId: users.responsible_user_id,
			})
			.from(users)
			.where(eq(users.id, userId))
			.get();
		return (
			account?.isMember === 0 &&
			account.inviterId !== null &&
			this.database.connection
				.select({ isMember: users.is_member })
				.from(users)
				.where(eq(users.id, account.inviterId))
				.get()?.isMember === 1
		);
	}

	private isMember(userId: number) {
		return (
			this.database.connection
				.select({ isMember: users.is_member })
				.from(users)
				.where(eq(users.id, userId))
				.get()?.isMember === 1
		);
	}
}
