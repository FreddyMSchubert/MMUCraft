'use client';

import { useEffect, useState, type RefObject } from 'react';

interface KnowledgeOutlineEntry {
	id: string;
	level: number;
	title: string;
}

export function KnowledgeOutline({
	articleRef,
	contentKey,
}: {
	articleRef: RefObject<HTMLElement | null>;
	contentKey: string;
}) {
	const [entries, setEntries] = useState<KnowledgeOutlineEntry[]>([]);
	const [activeId, setActiveId] = useState('');

	useEffect(() => {
		let cancelled = false;
		const headings = Array.from(
			articleRef.current?.querySelectorAll<HTMLHeadingElement>('h1, h2, h3, h4, h5, h6') ??
				[],
		);
		const usedIds = new Set<string>();
		const nextEntries: KnowledgeOutlineEntry[] = [];

		for (const [index, heading] of headings.entries()) {
			const title = heading.textContent.replace(/\s+/g, ' ').trim();
			if (!title) continue;

			const preferredId =
				heading.id || `knowledge-${headingSlug(title) || `section-${index + 1}`}`;
			const id = uniqueHeadingId(preferredId, usedIds, heading);
			heading.id = id;
			usedIds.add(id);
			nextEntries.push({
				id,
				level: Number(heading.tagName.slice(1)),
				title,
			});
		}

		const requestedId = readHashId();
		const requestedEntry = nextEntries.find((entry) => entry.id === requestedId);
		const initialFrame = window.requestAnimationFrame(() => {
			if (cancelled) return;
			setEntries(nextEntries);
			setActiveId(requestedEntry?.id ?? nextEntries.at(0)?.id ?? '');
			if (requestedEntry) document.getElementById(requestedEntry.id)?.scrollIntoView();
		});

		if (requestedEntry) {
			const scrollToRequestedHeading = () => {
				if (!cancelled) document.getElementById(requestedEntry.id)?.scrollIntoView();
			};

			const images = Array.from(articleRef.current?.querySelectorAll('img') ?? []);
			void Promise.all(
				images.map(
					(image) =>
						new Promise<void>((resolve) => {
							if (image.complete) {
								resolve();
								return;
							}
							image.addEventListener(
								'load',
								() => {
									resolve();
								},
								{ once: true },
							);
							image.addEventListener(
								'error',
								() => {
									resolve();
								},
								{ once: true },
							);
						}),
				),
			).then(scrollToRequestedHeading);
		}

		return () => {
			cancelled = true;
			window.cancelAnimationFrame(initialFrame);
		};
	}, [articleRef, contentKey]);

	useEffect(() => {
		if (entries.length === 0) return;
		let animationFrame = 0;

		function updateActiveHeading() {
			animationFrame = 0;
			const article = articleRef.current;
			if (!article) return;

			const headingElements = entries
				.map((entry) => article.querySelector<HTMLElement>(`#${CSS.escape(entry.id)}`))
				.filter((heading): heading is HTMLElement => heading !== null);
			if (headingElements.length === 0) return;

			const activationLine = Math.min(140, window.innerHeight * 0.25);
			let current = headingElements[0];
			for (const heading of headingElements) {
				if (heading.getBoundingClientRect().top > activationLine) break;
				current = heading;
			}

			const atPageEnd =
				window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
			setActiveId(atPageEnd ? (headingElements.at(-1)?.id ?? current.id) : current.id);
		}

		function scheduleUpdate() {
			if (animationFrame === 0) {
				animationFrame = window.requestAnimationFrame(updateActiveHeading);
			}
		}

		scheduleUpdate();
		window.addEventListener('scroll', scheduleUpdate, { passive: true });
		window.addEventListener('resize', scheduleUpdate);

		return () => {
			window.cancelAnimationFrame(animationFrame);
			window.removeEventListener('scroll', scheduleUpdate);
			window.removeEventListener('resize', scheduleUpdate);
		};
	}, [articleRef, entries]);

	if (entries.length <= 1) return null;
	const rootLevel = Math.min(...entries.map((entry) => entry.level));

	return (
		<aside className="knowledgeOutline" aria-label="On this page">
			<h2 className="knowledgeOutlineTitle">On this page</h2>
			<nav>
				{entries.map((entry) => (
					<a
						key={entry.id}
						className={entry.id === activeId ? 'active' : undefined}
						href={`#${entry.id}`}
						style={{
							paddingLeft: `${12 + Math.max(0, entry.level - rootLevel) * 16}px`,
						}}
						onClick={() => {
							setActiveId(entry.id);
						}}
					>
						{entry.title}
					</a>
				))}
			</nav>
		</aside>
	);
}

function headingSlug(title: string) {
	return title
		.normalize('NFKD')
		.replace(/[\u0300-\u036f]/g, '')
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-+|-+$/g, '');
}

function uniqueHeadingId(preferredId: string, usedIds: Set<string>, heading: HTMLHeadingElement) {
	let id = preferredId;
	let suffix = 2;
	let existingElement = document.getElementById(id);
	while (usedIds.has(id) || (existingElement !== null && existingElement !== heading)) {
		id = `${preferredId}-${suffix}`;
		suffix += 1;
		existingElement = document.getElementById(id);
	}
	return id;
}

function readHashId() {
	const hash = window.location.hash.slice(1);
	try {
		return decodeURIComponent(hash);
	} catch {
		return hash;
	}
}
