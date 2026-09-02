import { Injectable } from '@nestjs/common';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join, relative, sep } from 'node:path';
import type {
	KnowledgeDocument,
	KnowledgePage,
	KnowledgePageMetadata,
	KnowledgeSearchPage,
	KnowledgeTreeEntry,
} from './knowledge-document.types';

const DEFAULT_KNOWLEDGE_ROOTS = [
	join(process.cwd(), 'content', 'knowledge'),
	join(process.cwd(), '..', 'web', 'public', 'knowledge'),
] as const;

@Injectable()
export class KnowledgeDocumentCatalogService {
	private cached: KnowledgeDocument | null = null;

	loadDocument(): KnowledgeDocument {
		if (this.cached) return this.cached;

		const root =
			process.env.KNOWLEDGE_ROOT ??
			DEFAULT_KNOWLEDGE_ROOTS.find((candidate) => existsSync(candidate)) ??
			DEFAULT_KNOWLEDGE_ROOTS[0];

		if (!existsSync(root)) {
			return (this.cached = {
				root,
				mtimeMs: 0,
				pages: [],
				tree: [],
				unlockable: [],
				searchPages: [],
			});
		}

		const mtimeMs = this.getTreeMtimeMs(root);
		return (this.cached = this.parseKnowledgeRoot(root, mtimeMs));
	}

	private parseKnowledgeRoot(root: string, mtimeMs: number): KnowledgeDocument {
		const tree = this.readDirectory(root, root);
		const pages = this.flattenPages(tree);
		const unlockable = pages.filter((page) => !page.unlockedByDefault);
		const searchPages = pages.map((page) => this.toSearchPage(root, page));
		const seen = new Set<string>();
		for (const page of pages) {
			if (seen.has(page.id)) throw new Error(`Duplicate knowledge id: ${page.id}`);
			seen.add(page.id);
		}
		return { root, mtimeMs, pages, tree, unlockable, searchPages };
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
			if (!child.isFile() || !child.name.endsWith('.md')) continue;

			const metadata = this.parseMetadata(readFileSync(childPath, 'utf8'), childPath);
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
		const tips = this.parseList(metadata, 'tips');
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
		if (!unlockOrderValue) {
			throw new Error(`Knowledge markdown file is missing unlockOrder: ${filePath}`);
		}
		if (!sidebarTitle) {
			throw new Error(`Knowledge markdown file is missing sidebarTitle: ${filePath}`);
		}
		if (!this.parseList(metadata, 'tags').length) {
			throw new Error(`Knowledge markdown file is missing tags: ${filePath}`);
		}
		if (!tips.length) throw new Error(`Knowledge markdown file is missing tips: ${filePath}`);

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
			tips,
		};
	}

	private toSearchPage(root: string, page: KnowledgePage): KnowledgeSearchPage {
		const source = readFileSync(join(root, page.path), 'utf8');
		const metadata = /^====\r?\n([\s\S]*?)\r?\n====/.exec(source)?.[1] ?? '';
		return {
			id: page.id,
			title: page.sidebarTitle,
			folders: page.folders.join(' '),
			tags: this.parseList(metadata, 'tags').join(' '),
			content: source.replace(/^====\r?\n[\s\S]*?\r?\n====\r?\n?/, ''),
		};
	}

	private parseList(metadata: string, key: string): string[] {
		return (
			new RegExp(`^${key}:\\s*\\n((?:- .+(?:\\n|$))+)`, 'm')
				.exec(metadata.replace(/\r\n/g, '\n'))?.[1]
				?.trimEnd()
				.split('\n')
				.map((line) => line.slice(2).trim())
				.filter(Boolean) ?? []
		);
	}

	private flattenPages(entries: KnowledgeTreeEntry[]): KnowledgePage[] {
		return entries.flatMap((entry) =>
			entry.type === 'folder' ? this.flattenPages(entry.children) : [entry],
		);
	}

	private getTreeMtimeMs(path: string): number {
		const stats = statSync(path);
		if (!stats.isDirectory()) return stats.mtimeMs;

		let mtimeMs = stats.mtimeMs;
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
}
