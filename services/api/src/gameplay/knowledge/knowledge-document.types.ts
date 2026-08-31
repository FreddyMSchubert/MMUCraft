export interface KnowledgePageMetadata {
	id: string;
	unlockOrder: number | null;
	chatMessage: string;
	sidebarTitle: string;
	tips: string[];
}

export type KnowledgePage = KnowledgePageMetadata & {
	type: 'page';
	path: string;
	folders: string[];
	unlockedByDefault: boolean;
	unlocked?: boolean;
};

export interface KnowledgeFolder {
	type: 'folder';
	name: string;
	children: KnowledgeTreeEntry[];
}

export type KnowledgeTreeEntry = KnowledgeFolder | KnowledgePage;

export interface KnowledgeDocument {
	root: string;
	mtimeMs: number;
	pages: KnowledgePage[];
	tree: KnowledgeTreeEntry[];
	unlockable: KnowledgePage[];
}
