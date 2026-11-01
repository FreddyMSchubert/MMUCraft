import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { and, eq, isNotNull, ne, or } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import { ADMIN_SIGNUP_EMAIL } from '../auth/auth.util';
import {
	DatabaseService,
	duckAwards,
	duckCommitteeKills,
	duckNotes,
	playerProfiles,
	playerStats,
	users,
} from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { effectivePlayerColor } from '../players/player-color';
import { normalizeStatsJson } from '../players/player-statistics';
import { DiscordService } from '../discord/discord.service';
import { ShopItemCatalogService } from '../gameplay/shop/shop-item-catalog.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { FeatureTogglesService } from '../toggles/feature-toggles.service';
import { DUCKS, duckById, duckRank } from './duck-catalog';

type AwardRow = typeof duckAwards.$inferSelect;
type UserRow = typeof users.$inferSelect;
const londonWeekday = new Intl.DateTimeFormat('en-GB', {
	timeZone: 'Europe/London',
	weekday: 'long',
});

function isSundayInLondon(unixMs: number) {
	return Number.isSafeInteger(unixMs) && unixMs > 0 && londonWeekday.format(unixMs) === 'Sunday';
}

@Injectable()
export class DucksService {
	private readonly logger = new Logger(DucksService.name);
	private readonly waitingForDiscordJoin = new Map<
		number,
		ReturnType<DucksService['toMessage']>[]
	>();

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
				const asset = this.shopItems.itemAsset(award ? duck.itemId : 'duck-rubber');
				return {
					...duck,
					name: this.duckName(duck.itemId),
					priceDabloons: duck.id === 13 ? 10 : 5,
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
			price_dabloons: duck.id === 13 ? 10 : 5,
			delivery_kind: 'fake_item',
			unix_ms: Date.now(),
			display_name: this.duckName(duck.itemId),
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
		const fresh = this.grant(user, [2]);
		const stats = this.database.connection
			.select({ json: playerStats.stats_json })
			.from(playerStats)
			.where(eq(playerStats.user_id, userId))
			.get();
		const announced =
			stats && normalizeStatsJson(stats.json).minecraft.lastPlayedAtUnixMs
				? fresh
				: fresh.filter((award) => award.duck_id !== 2);
		if (announced.length) {
			await this.notify(user, announced);
		} else if (fresh.length) {
			await this.pushCount(user);
		}
	}

	// Called by the existing player sync after the current Minecraft statistics have been stored.
	onPlayerSync(
		minecraftUuid: string,
		minecraftUsername: string,
		playTimeTicks: number,
		isLogin: boolean,
		firstJoin: boolean,
		committeeKillAdvancements: { username: string; obtainedAtUnixMs: number }[] = [],
	) {
		if (!this.isOpen()) return { count: 0, newAwards: [], pending: [] };
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) return { count: 0, newAwards: [], pending: [] };
		if (isLogin && !this.waitingForDiscordJoin.has(user.id))
			this.waitingForDiscordJoin.set(user.id, []);
		const earned = [
			...(isLogin ? [1, ...(user.is_member === 1 ? [2] : [])] : []),
			...(playTimeTicks >= 168 * 60 * 60 * 20 ? [7] : []),
		];
		const fresh = [
			...this.grant(user, earned),
			...this.creditCommitteeAdvancements(user, committeeKillAdvancements),
		];
		void this.publishDiscord(
			user,
			firstJoin ? fresh.filter((award) => award.duck_id !== 1 && award.duck_id !== 2) : fresh,
		);
		return {
			count: this.awards(user.id).length,
			newAwards: fresh,
			pending: this.pending(user.id),
		};
	}

	async recordEvent(
		minecraftUuid: string,
		minecraftUsername: string,
		duckId: number,
		victimMinecraftUuid = '',
		eventUnixMs = 0,
	) {
		if (!this.isOpen()) return { accountLinked: false, count: 0, newAwards: [] };
		// Ducks 6 and 12 wait for their full collections to exist.
		if (![4, 5, 7, 8, 9].includes(duckId))
			throw new BadRequestException('Invalid automatic duck event.');
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		if (!user) return { accountLinked: false, count: 0, newAwards: [] };
		if (duckId === 4 && (!Number.isSafeInteger(eventUnixMs) || eventUnixMs <= 0))
			throw new BadRequestException('Committee kill event time is required.');
		if (duckId === 4 && !isSundayInLondon(eventUnixMs))
			return { accountLinked: true, count: this.awards(user.id).length, newAwards: [] };
		const fresh =
			duckId === 4
				? this.recordCommitteeKill(user, victimMinecraftUuid, eventUnixMs)
				: this.grant(user, [duckId]);
		await this.notify(user, fresh);
		return { accountLinked: true, count: this.awards(user.id).length, newAwards: fresh };
	}

	private recordCommitteeKill(
		killer: UserRow,
		victimMinecraftUuid: string,
		killedAtUnixMs: number,
	) {
		const victim = this.identities.findByUuid(victimMinecraftUuid);
		if (
			!victim ||
			victim.email === ADMIN_SIGNUP_EMAIL ||
			(victim.is_committee !== 1 && victim.is_super_admin !== 1)
		)
			return [];
		this.database.connection
			.insert(duckCommitteeKills)
			.values({
				killer_user_id: killer.id,
				victim_user_id: victim.id,
				killed_at_unix_ms: killedAtUnixMs,
			})
			.onConflictDoUpdate({
				target: [duckCommitteeKills.killer_user_id, duckCommitteeKills.victim_user_id],
				set: { killed_at_unix_ms: killedAtUnixMs },
			})
			.run();
		const committee = this.committeeMembers();
		const killed = new Set(
			this.database.connection
				.select({
					id: duckCommitteeKills.victim_user_id,
					killedAtUnixMs: duckCommitteeKills.killed_at_unix_ms,
				})
				.from(duckCommitteeKills)
				.where(eq(duckCommitteeKills.killer_user_id, killer.id))
				.all()
				.filter((row) => isSundayInLondon(row.killedAtUnixMs))
				.map((row) => row.id),
		);
		return committee.length > 0 && committee.every(({ id }) => killed.has(id))
			? this.grant(killer, [4])
			: [];
	}

	private creditCommitteeAdvancements(
		killer: UserRow,
		advancements: { username: string; obtainedAtUnixMs: number }[],
	) {
		const completed = new Map(
			advancements
				.filter((entry) => isSundayInLondon(entry.obtainedAtUnixMs))
				.map((entry) => [entry.username.toLowerCase(), entry.obtainedAtUnixMs]),
		);
		return this.committeeMembers().flatMap((member) => {
			const obtainedAtUnixMs = completed.get(member.minecraft_username.toLowerCase());
			return member.minecraft_uuid && obtainedAtUnixMs
				? this.recordCommitteeKill(killer, member.minecraft_uuid, obtainedAtUnixMs)
				: [];
		});
	}

	private committeeMembers() {
		return this.database.connection
			.select()
			.from(users)
			.where(
				and(
					or(eq(users.is_committee, 1), eq(users.is_super_admin, 1)),
					isNotNull(users.minecraft_uuid),
					ne(users.email, ADMIN_SIGNUP_EMAIL),
				),
			)
			.all();
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

	async afterDiscordJoin(minecraftUuid: string) {
		const user = this.identities.findByUuid(minecraftUuid);
		if (!user) return;
		const awards = this.waitingForDiscordJoin.get(user.id);
		if (!awards) return;
		this.waitingForDiscordJoin.delete(user.id);
		await this.publishDiscord(user, awards);
	}

	private async publishDiscord(user: UserRow, fresh: ReturnType<DucksService['toMessage']>[]) {
		const waiting = this.waitingForDiscordJoin.get(user.id);
		if (waiting) {
			waiting.push(...fresh);
			return;
		}
		for (const award of fresh) {
			if (!this.isOpen()) return;
			const content = `earned ${award.name} for ${award.announcement_reason}! 🎉 (${award.count}/${DUCKS.length} ducks collected)`;
			try {
				await this.discord.publish({
					type: award.duck_id === 13 ? 'duck_final' : 'duck',
					minecraft_username: user.minecraft_username,
					minecraft_uuid: user.minecraft_uuid ?? '',
					content,
					role:
						user.is_committee || user.is_super_admin
							? 'Committee'
							: user.responsible_user_id !== null
								? 'External'
								: user.is_member
									? 'Member'
									: '',
					nickname: '',
					pronouns: '',
					color_hex: award.color_hex,
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
			name: this.duckName(duck.itemId),
			condition: duck.condition,
			announcement_reason: duck.announcementReason,
			awarded_to: user.minecraft_username,
			color_hex: this.userColor(user),
			awarded_at_unix_ms: row.awarded_at_unix_ms,
			count,
		};
	}

	private userColor(user: UserRow) {
		const customColor = this.database.connection
			.select({ color: playerProfiles.color_hex })
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, user.id))
			.get()?.color;
		return effectivePlayerColor(user.minecraft_uuid, customColor);
	}

	private duckName(itemId: string) {
		return this.shopItems.itemTitle(itemId) ?? itemId;
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
