import { BadRequestException, Injectable } from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join, relative, sep } from 'node:path';
import { and, desc, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth.service';
import { DatabaseService, knowledgeReads, knowledgeUnlocks } from '../../database/database.service';
import { MinecraftIdentityService } from '../../database/minecraft-identity.service';
import { PlayersService } from '../../players/players.service';

const DEFAULT_KNOWLEDGE_ROOTS = [
	join(process.cwd(), 'content', 'knowledge'),
	join(process.cwd(), '..', 'web', 'public', 'knowledge'),
] as const;
const KNOWLEDGE_READ_REWARD_DABLOONS = 3;

interface KnowledgePageMetadata {
	id: string;
	unlockOrder: number | null;
	chatMessage: string;
	sidebarTitle: string;
}

type KnowledgePage = KnowledgePageMetadata & {
	type: 'page';
	path: string;
	folders: string[];
	unlockedByDefault: boolean;
	unlocked?: boolean;
};

interface KnowledgeFolder {
	type: 'folder';
	name: string;
	children: KnowledgeTreeEntry[];
}

type KnowledgeTreeEntry = KnowledgeFolder | KnowledgePage;

interface KnowledgeDocument {
	root: string;
	mtimeMs: number;
	pages: KnowledgePage[];
	tree: KnowledgeTreeEntry[];
	unlockable: KnowledgePage[];
}

interface CachedKnowledgeDocument {
	root: string;
	mtimeMs: number;
	document: KnowledgeDocument;
}

interface KnowledgeUnlockResponse {
	unlocked: boolean;
	all_unlocked: boolean;
	knowledge_id: string;
	priority: number;
	topic: string;
	message: string;
}

@Injectable()
export class KnowledgeService {
	private cached: CachedKnowledgeDocument | null = null;

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly players: PlayersService,
	) {}

	getKnowledgeForUser(userId: number) {
		const document = this.loadDocument();
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

	async markRead(user: AuthenticatedUser, knowledgeIdInput: unknown) {
		const knowledgeId = typeof knowledgeIdInput === 'string' ? knowledgeIdInput.trim() : '';
		const page = this.loadDocument().pages.find((candidate) => candidate.id === knowledgeId);
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
			const result = await this.players.grantKnowledgeReadMoney(
				user.minecraftUsername,
				KNOWLEDGE_READ_REWARD_DABLOONS,
			);
			if (!result.granted)
				throw new BadRequestException(
					result.message || 'You have to be online on the server to receive dabloons.',
				);
			moneyGranted = true;
			this.players.recordMoneyForUser(
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
		const document = this.loadDocument();
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

		const document = this.loadDocument();

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

	private loadDocument(): KnowledgeDocument {
		const root =
			process.env.KNOWLEDGE_ROOT ??
			DEFAULT_KNOWLEDGE_ROOTS.find((candidate) => existsSync(candidate)) ??
			DEFAULT_KNOWLEDGE_ROOTS[0];

		if (!existsSync(root)) {
			return {
				root,
				mtimeMs: 0,
				pages: [],
				tree: [],
				unlockable: [],
			};
		}

		const mtimeMs = this.getTreeMtimeMs(root);

		if (this.cached?.root === root && this.cached.mtimeMs === mtimeMs) {
			return this.cached.document;
		}

		const document = this.parseKnowledgeRoot(root, mtimeMs);

		this.cached = {
			root,
			mtimeMs,
			document,
		};

		return document;
	}

	private parseKnowledgeRoot(root: string, mtimeMs: number): KnowledgeDocument {
		const tree = this.readDirectory(root, root);
		const pages = this.flattenPages(tree);
		const unlockable = pages.filter((page) => !page.unlockedByDefault);

		const seen = new Set<string>();
		for (const page of pages) {
			if (seen.has(page.id)) {
				throw new Error(`Duplicate knowledge id: ${page.id}`);
			}

			seen.add(page.id);
		}

		return {
			root,
			mtimeMs,
			pages,
			tree,
			unlockable,
		};
	}

	private readDirectory(root: string, directory: string): KnowledgeTreeEntry[] {
		const children = readdirSync(directory, { withFileTypes: true })
			.filter((entry) => !entry.name.startsWith('.'))
			.sort((left, right) => left.name.localeCompare(right.name, 'en'));

		const entries: KnowledgeTreeEntry[] = [];

		for (const child of children) {
			const childPath = join(directory, child.name);

			if (child.isDirectory()) {
				entries.push({
					type: 'folder',
					name: this.displayName(child.name),
					children: this.readDirectory(root, childPath),
				});
				continue;
			}

			if (!child.isFile() || !child.name.endsWith('.md')) {
				continue;
			}

			const source = readFileSync(childPath, 'utf8');
			const metadata = this.parseMetadata(source, childPath);
			const relativePath = relative(root, childPath).split(sep).join('/');
			const folders = relative(root, directory)
				.split(sep)
				.filter((part) => part && part !== '.')
				.map((part) => this.displayName(part));

			entries.push({
				type: 'page',
				...metadata,
				path: relativePath,
				folders,
				unlockedByDefault: metadata.unlockOrder === null,
			});
		}

		return entries;
	}

	private parseMetadata(source: string, filePath: string): KnowledgePageMetadata {
		const match = /^====\r?\n([\s\S]*?)\r?\n====/.exec(source);

		if (!match) {
			throw new Error(`Knowledge markdown file is missing metadata block: ${filePath}`);
		}

		const values = new Map<string, string>();
		const metadata = match[1];
		if (metadata === undefined) throw new Error(`Knowledge metadata is invalid: ${filePath}`);
		for (const line of metadata.replace(/\r\n/g, '\n').split('\n')) {
			const parsed = /^([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.*)$/.exec(line);
			const key = parsed?.[1];
			const value = parsed?.[2];
			if (key && value !== undefined) values.set(key, value.trim());
		}

		const id = values.get('id');
		const unlockOrderValue = values.get('unlockOrder');
		const sidebarTitle = values.get('sidebarTitle');

		if (!id) throw new Error(`Knowledge markdown file is missing id: ${filePath}`);
		if (!unlockOrderValue)
			throw new Error(`Knowledge markdown file is missing unlockOrder: ${filePath}`);
		if (!sidebarTitle)
			throw new Error(`Knowledge markdown file is missing sidebarTitle: ${filePath}`);

		const unlockOrder = unlockOrderValue === 'public' ? null : Number(unlockOrderValue);

		if (unlockOrder !== null && !Number.isInteger(unlockOrder)) {
			throw new Error(`Knowledge unlockOrder must be an integer or public: ${filePath}`);
		}

		return {
			id,
			unlockOrder,
			chatMessage:
				values.get('chatMessage') ??
				`You've unlocked new knowledge about ${sidebarTitle}. Visit the website to learn more.`,
			sidebarTitle,
		};
	}

	private flattenPages(entries: KnowledgeTreeEntry[]): KnowledgePage[] {
		const pages: KnowledgePage[] = [];

		for (const entry of entries) {
			if (entry.type === 'folder') {
				pages.push(...this.flattenPages(entry.children));
			} else {
				pages.push(entry);
			}
		}

		return pages;
	}

	private getTreeMtimeMs(path: string): number {
		const stats = statSync(path);
		let mtimeMs = stats.mtimeMs;

		if (!stats.isDirectory()) {
			return mtimeMs;
		}

		for (const child of readdirSync(path, { withFileTypes: true })) {
			if (child.name.startsWith('.')) continue;
			mtimeMs = Math.max(mtimeMs, this.getTreeMtimeMs(join(path, child.name)));
		}

		return mtimeMs;
	}

	private displayName(name: string): string {
		return basename(name)
			.replace(/^\d+[-_]/, '')
			.replace(/[-_]+/g, ' ')
			.replace(/\b\w/g, (letter) => letter.toUpperCase());
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
