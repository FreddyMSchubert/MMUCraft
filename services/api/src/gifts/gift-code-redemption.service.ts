import { BadRequestException, Injectable } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import {
	DatabaseService,
	GiftCodeRow,
	giftCodeRedemptions,
	giftCodes,
} from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { PlayerMoneyHistoryService } from '../players/player-money-history.service';
import { normalizeGiftCode } from './gift-code-validation';

@Injectable()
export class GiftCodeRedemptionService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly playerMoneyHistory: PlayerMoneyHistoryService,
	) {}

	async redeem(user: AuthenticatedUser, codeInput: string | undefined) {
		const code = normalizeGiftCode(codeInput);
		const giftCode = this.database.connection
			.select()
			.from(giftCodes)
			.where(eq(giftCodes.code, code))
			.get();

		if (!giftCode) {
			throw new BadRequestException(
				'That gift code does not exist. Check every character and try again.',
			);
		}
		const now = Date.now();
		if (giftCode.expires_at_unix_ms !== null && giftCode.expires_at_unix_ms <= now) {
			throw new BadRequestException(
				'That gift code has expired and can no longer be redeemed.',
			);
		}
		if (giftCode.members_only === 1 && !user.isMember) {
			throw new BadRequestException(
				'That gift code can only be redeemed by society members.',
			);
		}
		this.reserveRedemption(giftCode, user.id, now);
		let moneyGranted = false;

		try {
			const result = await this.minecraft.gameplay<{
				granted: boolean;
				online: boolean;
				balance_dabloons: number;
				message: string;
			}>('GrantGiftCodeMoney', {
				minecraft_username: user.minecraftUsername,
				amount_dabloons: giftCode.amount_dabloons,
				code: giftCode.code,
				unix_ms: now,
			});

			if (!result.granted) {
				this.releaseReservation(giftCode.code, user.id, now);
				throw new BadRequestException(
					result.message ||
						'Join the Minecraft server, then redeem the gift code again while you are online.',
				);
			}
			moneyGranted = true;

			this.playerMoneyHistory.recordForUser(
				user.id,
				'gift_code',
				giftCode.amount_dabloons,
				result.balance_dabloons,
				`gift:${giftCode.code.toLowerCase()}:${user.id}`,
				now,
			);

			return {
				redeemed: true,
				amountDabloons: giftCode.amount_dabloons,
				message:
					result.message ||
					`Gift code redeemed for ${giftCode.amount_dabloons} Dabloons.`,
			};
		} catch (error) {
			if (!moneyGranted && !(error instanceof BadRequestException)) {
				this.releaseReservation(giftCode.code, user.id, now);
				throw new BadRequestException(
					'The Minecraft server could not redeem the code. Make sure you are online and try again.',
				);
			}
			if (moneyGranted && !(error instanceof BadRequestException)) {
				throw new BadRequestException(
					'The Dabloons were granted, but the website record could not be updated. Contact committee if your balance looks wrong.',
				);
			}
			throw error;
		}
	}

	private releaseReservation(code: string, userId: number, redeemedAtUnixMs: number) {
		this.database.connection
			.delete(giftCodeRedemptions)
			.where(
				and(
					eq(giftCodeRedemptions.code, code),
					eq(giftCodeRedemptions.user_id, userId),
					eq(giftCodeRedemptions.redeemed_at_unix_ms, redeemedAtUnixMs),
				),
			)
			.run();
	}

	private reserveRedemption(giftCode: GiftCodeRow, userId: number, redeemedAtUnixMs: number) {
		this.database.connection.transaction((transaction) => {
			if (giftCode.redemption_mode === 'single') {
				const existing = transaction
					.select({ code: giftCodeRedemptions.code })
					.from(giftCodeRedemptions)
					.where(eq(giftCodeRedemptions.code, giftCode.code))
					.get();
				if (existing) {
					throw new BadRequestException('That gift code has already been redeemed');
				}
			}

			const inserted = transaction
				.insert(giftCodeRedemptions)
				.values({
					code: giftCode.code,
					user_id: userId,
					redeemed_at_unix_ms: redeemedAtUnixMs,
				})
				.onConflictDoNothing()
				.run();

			if (inserted.changes !== 1) {
				throw new BadRequestException('You have already redeemed that gift code');
			}
		});
	}
}
