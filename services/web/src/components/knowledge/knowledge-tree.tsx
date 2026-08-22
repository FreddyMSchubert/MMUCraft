export type KnowledgeTreeEntry = KnowledgeFolder | KnowledgePage;

export interface KnowledgeFolder {
	type: 'folder';
	name: string;
	children: KnowledgeTreeEntry[];
}

export interface KnowledgePage {
	type: 'page';
	id: string;
	path: string;
	sidebarTitle: string;
	unlockOrder: number | null;
	chatMessage: string;
	unlockedByDefault: boolean;
	unlocked?: boolean;
}

export interface KnowledgeResponse {
	contentVersion: number;
	lastUnlockedKnowledgeId: string | null;
	unlockedKnowledgeIds: string[];
	readKnowledgeIds: string[];
	tree: KnowledgeTreeEntry[];
}
export function KnowledgeTreeNode({
	entry,
	activePageId,
	readPageIds,
	onSelectPage,
	depth,
}: {
	entry: KnowledgeTreeEntry;
	activePageId: string;
	readPageIds: Set<string>;
	onSelectPage: (pageId: string) => void;
	depth: number;
}) {
	if (entry.type === 'folder') {
		return (
			<div className="knowledgeTreeFolder">
				<div
					className="knowledgeTreeHeading"
					style={{ paddingLeft: `${depth * 16 + 12}px` }}
				>
					{entry.name}
				</div>
				<div>
					{entry.children.map((child) => (
						<KnowledgeTreeNode
							key={
								child.type === 'folder'
									? `folder-${entry.name}-${child.name}`
									: child.id
							}
							entry={child}
							activePageId={activePageId}
							readPageIds={readPageIds}
							onSelectPage={onSelectPage}
							depth={depth + 1}
						/>
					))}
				</div>
			</div>
		);
	}

	return (
		<button
			type="button"
			className={['knowledgeTreePage', entry.id === activePageId ? 'active' : '']
				.filter(Boolean)
				.join(' ')}
			style={{ paddingLeft: `${depth * 16 + 12}px` }}
			onClick={() => {
				onSelectPage(entry.id);
			}}
		>
			<span>{entry.sidebarTitle}</span>
			{!readPageIds.has(entry.id) && (
				<span className="knowledgeTreeNew" aria-label="Not read yet">
					!
				</span>
			)}
		</button>
	);
}

export function filterUnlockedTree(entries: KnowledgeTreeEntry[]): KnowledgeTreeEntry[] {
	const visible: KnowledgeTreeEntry[] = [];

	for (const entry of entries) {
		if (entry.type === 'page') {
			if (entry.unlocked) {
				visible.push(entry);
			}
			continue;
		}

		const children = filterUnlockedTree(entry.children);
		if (children.length > 0) {
			visible.push({
				...entry,
				children,
			});
		}
	}

	return visible;
}

export function flattenPages(entries: KnowledgeTreeEntry[]): KnowledgePage[] {
	const pages: KnowledgePage[] = [];

	for (const entry of entries) {
		if (entry.type === 'folder') {
			pages.push(...flattenPages(entry.children));
		} else {
			pages.push(entry);
		}
	}

	return pages;
}
