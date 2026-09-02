import { BadRequestException, Injectable } from '@nestjs/common';
import MiniSearch from 'minisearch';
import { randomInt } from 'node:crypto';
import { and, desc, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, knowledgeReads, knowledgeUnlocks } from '../../database/database.service';
import { MinecraftIdentityService } from '../../database/minecraft-identity.service';
import { PlayerMoneyHistoryService } from '../../players/player-money-history.service';
import { KnowledgeDocumentCatalogService } from './knowledge-document-catalog.service';
import type { KnowledgePage, KnowledgeTreeEntry } from './knowledge-document.types';

const KNOWLEDGE_READ_REWARD_DABLOONS = 3;
const SEARCH_CACHE_MS = 60_000;
const SEARCH_CACHE_SIZE = 100;
const SEARCH_RESULT_LIMIT = 50;

interface KnowledgeUnlockResponse {
	unlocked: boolean;
	all_unlocked: boolean;
	knowledge_id: string;
	priority: number;
	topic: string;
	message: string;
}

interface KnowledgeTipResponse {
	found: boolean;
	knowledge_id: string;
	tip: string;
	has_unread_knowledge: boolean;
}

type KnowledgeSearchResult =
	{ locked: true } | { locked: false; id: string; title: string; folders: string[] };

@Injectable()
export class KnowledgeService {
	private searchIndex: { version: number; value: MiniSearch } | null = null;
	private readonly searchCache = new Map<string, { expiresAt: number; ids: string[] }>();

	constructor(
		private readonly database: DatabaseService,
		private readonly documents: KnowledgeDocumentCatalogService,
		private readonly identities: MinecraftIdentityService,
		private readonly playerMoneyHistory: PlayerMoneyHistoryService,
	) {}

	getKnowledgeForUser(userId: number) {
		const document = this.documents.loadDocument();
		const unlockedIds = this.getUnlockedIds(userId);
		const readKnowledgeIds = this.getReadIds(userId);
		const lastUnlockedKnowledgeId = this.getLastUnlockedKnowledgeId(
			userId,
			document.unlockable,
		);

		return {
			contentVersion: document.mtimeMs,
			lastUnlockedKnowledgeId,
			unlockedKnowledgeIds: [...unlockedIds].filter((id) =>
				document.unlockable.some((page) => page.id === id),
			),
			readKnowledgeIds: [...readKnowledgeIds],
			tree: this.applyUnlockState(document.tree, unlockedIds),
		};
	}

	searchForUser(userId: number, queryInput: string | undefined) {
		const query = queryInput?.trim() ?? '';
		if (query.length > 100) throw new BadRequestException('Search query is too long.');
		if (!query) return { query, results: [] };

		const document = this.documents.loadDocument();
		const ids = this.searchIds(document.mtimeMs, document.searchPages, query);
		const pagesById = new Map(document.pages.map((page) => [page.id, page]));
		const unlockedIds = this.getUnlockedIds(userId);
		return {
			query,
			results: ids.flatMap<KnowledgeSearchResult>((id) => {
				const page = pagesById.get(id);
				if (!page) return [];
				if (!page.unlockedByDefault && !unlockedIds.has(page.id)) {
					return [{ locked: true as const }];
				}
				return [
					{
						locked: false as const,
						id: page.id,
						title: page.sidebarTitle,
						folders: page.folders,
					},
				];
			}),
		};
	}

	private searchIds(
		version: number,
		pages: { id: string; title: string; folders: string; tags: string; content: string }[],
		query: string,
	): string[] {
		if (this.searchIndex?.version !== version) {
			const value = new MiniSearch({
				fields: ['title', 'folders', 'tags', 'content'],
				searchOptions: {
					boost: { title: 4, tags: 3, folders: 2 },
					combineWith: 'AND',
					fuzzy: 0.2,
					prefix: true,
				},
			});
			value.addAll(pages);
			this.searchIndex = { version, value };
			this.searchCache.clear();
		}

		const key = query.toLocaleLowerCase('en');
		const cached = this.searchCache.get(key);
		if (cached?.expiresAt && cached.expiresAt > Date.now()) return cached.ids;
		if (cached) this.searchCache.delete(key);

		const ids = this.searchIndex.value
			.search(query)
			.slice(0, SEARCH_RESULT_LIMIT)
			.map((result) => String(result.id));
		if (this.searchCache.size >= SEARCH_CACHE_SIZE) {
			const oldestKey = this.searchCache.keys().next().value;
			if (oldestKey !== undefined) this.searchCache.delete(oldestKey);
		}
		this.searchCache.set(key, { expiresAt: Date.now() + SEARCH_CACHE_MS, ids });
		return ids;
	}

	getRandomTipForMinecraftPlayer(
		minecraftUuid: string,
		minecraftUsername: string,
	): KnowledgeTipResponse {
		const document = this.documents.loadDocument();
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		const unlockedIds = user ? this.getUnlockedIds(user.id) : new Set<string>();
		const readIds = user ? this.getReadIds(user.id) : new Set<string>();
		const hasUnreadKnowledge = Boolean(
			user &&
			document.pages.some(
				(page) =>
					(page.unlockedByDefault || unlockedIds.has(page.id)) && !readIds.has(page.id),
			),
		);
		const tips = document.pages
			.filter((page) => page.unlockedByDefault || unlockedIds.has(page.id))
			.flatMap((page) => page.tips.map((tip) => ({ knowledge_id: page.id, tip })));
		const picked = tips.length ? tips[randomInt(tips.length)] : undefined;

		return picked
			? { found: true, ...picked, has_unread_knowledge: hasUnreadKnowledge }
			: {
					found: false,
					knowledge_id: '',
					tip: '',
					has_unread_knowledge: hasUnreadKnowledge,
				};
	}

	async markRead(user: AuthenticatedUser, knowledgeIdInput: string | undefined) {
		const knowledgeId = typeof knowledgeIdInput === 'string' ? knowledgeIdInput.trim() : '';
		const page = this.documents
			.loadDocument()
			.pages.find((candidate) => candidate.id === knowledgeId);
		if (!page) throw new BadRequestException('Knowledge page not found.');
		if (!page.unlockedByDefault && !this.getUnlockedIds(user.id).has(page.id)) {
			throw new BadRequestException('That knowledge page is locked.');
		}

		const now = Date.now();
		const reserved = this.database.connection
			.insert(knowledgeReads)
			.values({
				user_id: user.id,
				knowledge_id: page.id,
				read_at_unix_ms: now,
			})
			.onConflictDoNothing()
			.run();
		if (reserved.changes !== 1) return { read: true, rewarded: false, amountDabloons: 0 };

		let moneyGranted = false;
		try {
			const result = await this.playerMoneyHistory.grantKnowledgeReadMoney(
				user.minecraftUsername,
				KNOWLEDGE_READ_REWARD_DABLOONS,
			);
			if (!result.granted)
				throw new BadRequestException(
					result.message || 'You have to be online on the server to receive Dabloons.',
				);
			moneyGranted = true;
			this.playerMoneyHistory.recordForUser(
				user.id,
				'knowledge_read',
				KNOWLEDGE_READ_REWARD_DABLOONS,
				result.balance_dabloons,
				`knowledge-read:${user.id}:${page.id}`,
				now,
			);
			return { read: true, rewarded: true, amountDabloons: KNOWLEDGE_READ_REWARD_DABLOONS };
		} catch (error) {
			if (!moneyGranted) {
				this.database.connection
					.delete(knowledgeReads)
					.where(
						and(
							eq(knowledgeReads.user_id, user.id),
							eq(knowledgeReads.knowledge_id, page.id),
						),
					)
					.run();
			}
			throw error;
		}
	}

	hasRemainingForUser(userId: number): boolean {
		const document = this.documents.loadDocument();
		if (document.unlockable.length === 0) {
			return false;
		}

		const unlockedIds = this.getUnlockedIds(userId);
		return document.unlockable.some((page) => !unlockedIds.has(page.id));
	}

	unlockNextForMinecraftUsername(
		minecraftUuidInput: string,
		minecraftUsernameInput: string,
		sourceInput: string,
	): KnowledgeUnlockResponse {
		const minecraftUsername = minecraftUsernameInput.trim();
		const source = sourceInput.trim() || 'knowledge_book';

		if (!minecraftUsername) {
			return this.noUnlock('No Minecraft username was provided.');
		}

		const user = this.identities.resolveAndRefresh(minecraftUuidInput, minecraftUsername);

		if (!user) {
			return this.noUnlock('No website account is linked to this Minecraft username yet.');
		}

		const document = this.documents.loadDocument();

		if (document.unlockable.length === 0) {
			return {
				unlocked: false,
				all_unlocked: true,
				knowledge_id: '',
				priority: 0,
				topic: '',
				message: 'There is no unlockable knowledge configured yet.',
			};
		}

		for (let attempt = 0; attempt < 5; attempt++) {
			const picked = this.database.connection.transaction((tx) => {
				const unlockedIds = this.getUnlockedIds(user.id);
				const remaining = document.unlockable.filter((page) => !unlockedIds.has(page.id));

				if (remaining.length === 0) {
					return 'all-unlocked' as const;
				}

				const chosen = this.pickRandomLowestOrderPage(remaining);

				const result = tx
					.insert(knowledgeUnlocks)
					.values({
						user_id: user.id,
						knowledge_id: chosen.id,
						unlocked_at_unix_ms: Date.now(),
						source,
					})
					.onConflictDoNothing()
					.run();

				return result.changes === 1 ? chosen : null;
			});

			if (picked === 'all-unlocked') {
				return {
					unlocked: false,
					all_unlocked: true,
					knowledge_id: '',
					priority: 0,
					topic: '',
					message: 'You have already unlocked all available knowledge.',
				};
			}

			if (picked) {
				return {
					unlocked: true,
					all_unlocked: false,
					knowledge_id: picked.id,
					priority: picked.unlockOrder ?? 0,
					topic: picked.sidebarTitle,
					message: picked.chatMessage,
				};
			}
		}

		return this.noUnlock('Knowledge unlock was busy. Try again.');
	}

	private applyUnlockState(
		entries: KnowledgeTreeEntry[],
		unlockedIds: Set<string>,
	): KnowledgeTreeEntry[] {
		return entries.map((entry): KnowledgeTreeEntry => {
			if (entry.type === 'folder') {
				return {
					...entry,
					children: this.applyUnlockState(entry.children, unlockedIds),
				};
			}

			return {
				...entry,
				unlocked: entry.unlockedByDefault || unlockedIds.has(entry.id),
			};
		});
	}

	private getUnlockedIds(userId: number): Set<string> {
		const rows = this.database.connection
			.select({ knowledge_id: knowledgeUnlocks.knowledge_id })
			.from(knowledgeUnlocks)
			.where(eq(knowledgeUnlocks.user_id, userId))
			.all();

		return new Set(rows.map((row) => row.knowledge_id));
	}

	private getReadIds(userId: number): Set<string> {
		return new Set(
			this.database.connection
				.select({ knowledge_id: knowledgeReads.knowledge_id })
				.from(knowledgeReads)
				.where(eq(knowledgeReads.user_id, userId))
				.all()
				.map((row) => row.knowledge_id),
		);
	}

	private pickRandomLowestOrderPage(pages: KnowledgePage[]): KnowledgePage {
		const lowestOrder = Math.min(...pages.map((page) => page.unlockOrder ?? 0));
		const candidates = pages.filter((page) => page.unlockOrder === lowestOrder);

		const selected = candidates[randomInt(candidates.length)];
		if (!selected) throw new Error('No knowledge page is available.');
		return selected;
	}

	private getLastUnlockedKnowledgeId(userId: number, unlockable: KnowledgePage[]): string | null {
		const configuredIds = new Set(unlockable.map((page) => page.id));

		const rows = this.database.connection
			.select({ knowledge_id: knowledgeUnlocks.knowledge_id })
			.from(knowledgeUnlocks)
			.where(eq(knowledgeUnlocks.user_id, userId))
			.orderBy(desc(knowledgeUnlocks.unlocked_at_unix_ms))
			.all();

		return rows.find((row) => configuredIds.has(row.knowledge_id))?.knowledge_id ?? null;
	}
	private noUnlock(message: string): KnowledgeUnlockResponse {
		return {
			unlocked: false,
			all_unlocked: false,
			knowledge_id: '',
			priority: 0,
			topic: '',
			message,
		};
	}
}
