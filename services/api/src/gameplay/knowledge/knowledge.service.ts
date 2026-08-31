import { BadRequestException, Injectable } from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { and, desc, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, knowledgeReads, knowledgeUnlocks } from '../../database/database.service';
import { MinecraftIdentityService } from '../../database/minecraft-identity.service';
import { PlayerMoneyHistoryService } from '../../players/player-money-history.service';
import { KnowledgeDocumentCatalogService } from './knowledge-document-catalog.service';
import type { KnowledgePage, KnowledgeTreeEntry } from './knowledge-document.types';

const KNOWLEDGE_READ_REWARD_DABLOONS = 3;

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
}

@Injectable()
export class KnowledgeService {
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

	getRandomTipForMinecraftPlayer(
		minecraftUuid: string,
		minecraftUsername: string,
	): KnowledgeTipResponse {
		const document = this.documents.loadDocument();
		const user = this.identities.resolveAndRefresh(minecraftUuid, minecraftUsername);
		const unlockedIds = user ? this.getUnlockedIds(user.id) : new Set<string>();
		const tips = document.pages
			.filter((page) => page.unlockedByDefault || unlockedIds.has(page.id))
			.flatMap((page) => page.tips.map((tip) => ({ knowledge_id: page.id, tip })));
		const picked = tips.length ? tips[randomInt(tips.length)] : undefined;

		return picked ? { found: true, ...picked } : { found: false, knowledge_id: '', tip: '' };
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
					result.message || 'You have to be online on the server to receive dabloons.',
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
