import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import { DatabaseService, duckAwards, duckNotes, users } from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { DiscordService } from '../discord/discord.service';
import { ShopItemCatalogService } from '../gameplay/shop/shop-item-catalog.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { FeatureTogglesService } from '../toggles/feature-toggles.service';
import { DUCKS, duckById, duckRank } from './duck-catalog';

type AwardRow = typeof duckAwards.$inferSelect;
type UserRow = typeof users.$inferSelect;

@Injectable()
export class DucksService {
	private readonly logger = new Logger(DucksService.name);

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly discord: DiscordService,
		private readonly shopItems: ShopItemCatalogService,
		private readonly featureToggles: FeatureTogglesService,
	) {}

	isOpen() {
		return this.featureToggles.enabledKeys().has('nether');
	}

	private requireOpen() {
		if (!this.isOpen()) throw new NotFoundException('Ducks unlock with the Hellish drop.');
	}

	getForUser(userId: number) {
		this.requireOpen();
		const awards = this.awards(userId);
		const count = awards.length;
		return {
			count,
			total: DUCKS.length,
			rank: duckRank(count),
			ducks: DUCKS.map((duck) => {
				const award = awards.find((row) => row.duck_id === duck.id);
				const asset = this.shopItems.itemAsset(duck.itemId);
				return {
					...duck,
					unlocked: Boolean(award),
					awardedAtUnixMs: award?.awarded_at_unix_ms ?? null,
					modelUrl: asset?.modelUrl ?? null,
					textureUrl: asset?.textureUrl ?? null,
				};
			}),
		};
	}

	async purchase(user: AuthenticatedUser, duckId: number) {
		this.requireOpen();
		const duck = duckById(duckId);
		if (!duck) throw new NotFoundException('Duck not found');
		const award = this.awards(user.id).find((row) => row.duck_id === duckId);
		if (!award) throw new BadRequestException('Earn this duck before buying another copy.');
		const result = await this.minecraft.gameplay<{
			purchased: boolean;
			message: string;
			balance_dabloons: number;
		}>('PurchaseShopItem', {
			minecraft_username: user.minecraftUsername,
			item_id: duck.itemId,
			price_dabloons: 5,
			delivery_kind: 'fake_item',
			unix_ms: Date.now(),
			display_name: duck.name,
			item_type: 'Duck',
			rarity: duck.id === 13 ? 'mythical' : 'rare',
			duck_awarded_to: user.minecraftUsername,
			duck_awarded_at_unix_ms: award.awarded_at_unix_ms,
			duck_condition: duck.condition,
		});
		if (!result.purchased)
			throw new BadRequestException(result.message || 'Could not buy duck.');
		return result;
	}

	listPlayers(searchInput = '') {
		this.requireOpen();
		const search = searchInput.trim().toLowerCase();
		if (search.length > 100) throw new BadRequestException('Search is too long.');
		return this.database.connection
			.select({ id: users.id, minecraftUsername: users.minecraft_username })
			.from(users)
			.all()
			.filter((user) => user.minecraftUsername.toLowerCase().includes(search))
			.sort((a, b) => a.minecraftUsername.localeCompare(b.minecraftUsername))
			.slice(0, 50);
	}

	getAdminPlayer(userId: number) {
		this.requireOpen();
		const user = this.user(userId);
		const note =
			this.database.connection
				.select({ note: duckNotes.note })
				.from(duckNotes)
				.where(eq(duckNotes.user_id, userId))
				.get()?.note ?? '';
		return {
			userId,
			minecraftUsername: user.minecraft_username,
			note,
			...this.getForUser(userId),
		};
	}

	setNote(userId: number, input: unknown) {
		this.requireOpen();
		this.user(userId);
		if (typeof input !== 'string' || input.length > 4000)
			throw new BadRequestException('Duck note must be at most 4000 characters.');
		this.database.connection
			.insert(duckNotes)
			.values({ user_id: userId, note: input, updated_at_unix_ms: Date.now() })
			.onConflictDoUpdate({
				target: duckNotes.user_id,
				set: { note: input, updated_at_unix_ms: Date.now() },
			})
			.run();
		return this.getAdminPlayer(userId);
	}

	async setAward(userId: number, duckId: number, awarded: unknown) {
		this.requireOpen();
		const user = this.user(userId);
		if (!duckById(duckId)) throw new NotFoundException('Duck not found');
		if (typeof awarded !== 'boolean')
			throw new BadRequestException('awarded must be a boolean');
		if (awarded) {
			const fresh = this.grant(user, [duckId]);
			await this.notify(user, fresh);
		} else {
			this.database.connection
				.delete(duckAwards)
				.where(and(eq(duckAwards.user_id, userId), eq(duckAwards.duck_id, duckId)))
				.run();
			await this.pushCount(user);
		}
		return this.getAdminPlayer(userId);
	}

	async awardMembership(userId: number) {
		if (!this.isOpen()) return;
		const user = this.user(userId);
		if (user.is_member !== 1) return;
		await this.notify(user, this.grant(user, [2]));
	}

	// Called by the existing player sync after the current Minecraft statistics have been stored.
	onPlayerSync(
		minecraftUuid: string,
		minecraftUsername: string,
		playTimeTicks: number,
		isLogin: boolean,
	) {
		if (!this.isOpen()) return { count: 0, newAwards: [], pending: [] };
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) return { count: 0, newAwards: [], pending: [] };
		const earned = isLogin
			? [
					1,
					...(user.is_member === 1 ? [2] : []),
					...(playTimeTicks >= 100 * 60 * 60 * 20 ? [7] : []),
				]
			: [];
		const fresh = this.grant(user, earned);
		void this.publishDiscord(user, fresh);
		return {
			count: this.awards(user.id).length,
			newAwards: fresh,
			pending: this.pending(user.id),
		};
	}

	async recordEvent(minecraftUuid: string, minecraftUsername: string, duckId: number) {
		if (!this.isOpen()) return { accountLinked: false, count: 0, newAwards: [] };
		if (![4, 5, 6, 7, 8, 9, 12].includes(duckId))
			throw new BadRequestException('Invalid automatic duck event.');
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) return { accountLinked: false, count: 0, newAwards: [] };
		const fresh = this.grant(user, [duckId]);
		await this.notify(user, fresh);
		return { accountLinked: true, count: this.awards(user.id).length, newAwards: fresh };
	}

	markDelivered(
		minecraftUuid: string,
		minecraftUsername: string,
		duckId: number,
		awardedAtUnixMs: number,
	) {
		if (!this.isOpen()) return false;
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) return false;
		return (
			this.database.connection
				.update(duckAwards)
				.set({ free_delivered_at_unix_ms: Date.now() })
				.where(
					and(
						eq(duckAwards.user_id, user.id),
						eq(duckAwards.duck_id, duckId),
						eq(duckAwards.awarded_at_unix_ms, awardedAtUnixMs),
					),
				)
				.run().changes > 0
		);
	}

	private grant(user: UserRow, ids: number[]) {
		if (!this.isOpen()) return [];
		const now = Date.now();
		const fresh = this.database.connection.transaction((tx) => {
			const added: AwardRow[] = [];
			for (const id of ids) {
				if (!duckById(id)) continue;
				const inserted = tx
					.insert(duckAwards)
					.values({ user_id: user.id, duck_id: id, awarded_at_unix_ms: now })
					.onConflictDoNothing()
					.run();
				if (inserted.changes)
					added.push({
						user_id: user.id,
						duck_id: id,
						awarded_at_unix_ms: now,
						free_delivered_at_unix_ms: null,
					});
			}
			const all = tx.select().from(duckAwards).where(eq(duckAwards.user_id, user.id)).all();
			if (all.length === 12 && all.every((row) => row.duck_id !== 13)) {
				tx.insert(duckAwards)
					.values({ user_id: user.id, duck_id: 13, awarded_at_unix_ms: now })
					.run();
				added.push({
					user_id: user.id,
					duck_id: 13,
					awarded_at_unix_ms: now,
					free_delivered_at_unix_ms: null,
				});
			}
			return added;
		});
		const count = this.awards(user.id).length;
		return fresh.map((row, index) =>
			this.toMessage(user, row, count - fresh.length + index + 1),
		);
	}

	private async notify(user: UserRow, fresh: ReturnType<DucksService['toMessage']>[]) {
		if (!fresh.length) return;
		await Promise.allSettled([this.publishDiscord(user, fresh), this.pushCount(user, fresh)]);
	}

	private async publishDiscord(user: UserRow, fresh: ReturnType<DucksService['toMessage']>[]) {
		for (const award of fresh) {
			if (!this.isOpen()) return;
			const content =
				award.duck_id === 13
					? `completed the entire duck collection! All 13 ducks are theirs. The pond has a new legend.`
					: `earned ${award.name}! ${award.count}/13 ducks collected.`;
			try {
				await this.discord.publish({
					type: award.duck_id === 13 ? 'duck_final' : 'duck',
					minecraft_username: user.minecraft_username,
					minecraft_uuid: user.minecraft_uuid ?? '',
					content,
					role: user.is_committee ? 'Committee' : user.is_member ? 'Member' : '',
					nickname: '',
					pronouns: '',
					color_hex: '',
					online_players: [],
				});
			} catch (error) {
				this.logger.warn(
					`Could not announce duck ${award.duck_id} in Discord: ${String(error)}`,
				);
			}
		}
	}

	private async pushCount(user: UserRow, fresh: ReturnType<DucksService['toMessage']>[] = []) {
		if (!this.isOpen()) return;
		try {
			await this.minecraft.gameplay('ApplyDuckUpdate', {
				minecraft_uuid: user.minecraft_uuid ?? '',
				minecraft_username: user.minecraft_username,
				duck_count: this.awards(user.id).length,
				new_awards: fresh,
			});
		} catch (error) {
			this.logger.warn(
				`Could not push duck update for ${user.minecraft_username}: ${String(error)}`,
			);
		}
	}

	private pending(userId: number) {
		const user = this.user(userId);
		return this.awards(userId)
			.filter((row) => row.free_delivered_at_unix_ms === null)
			.map((row) => this.toMessage(user, row, 0));
	}

	private toMessage(user: UserRow, row: AwardRow, count: number) {
		const duck = duckById(row.duck_id);
		if (!duck) throw new Error(`Unknown duck ${row.duck_id}`);
		return {
			duck_id: duck.id,
			item_id: duck.itemId,
			name: duck.name,
			condition: duck.condition,
			awarded_to: user.minecraft_username,
			awarded_at_unix_ms: row.awarded_at_unix_ms,
			count,
		};
	}

	private awards(userId: number) {
		return this.database.connection
			.select()
			.from(duckAwards)
			.where(eq(duckAwards.user_id, userId))
			.all();
	}

	private user(userId: number) {
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!user) throw new NotFoundException('Player not found');
		return user;
	}
}
