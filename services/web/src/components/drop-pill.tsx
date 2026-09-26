import type { CSSProperties } from 'react';

export interface DropInfo {
	id: string;
	name: string;
	emoji: string;
	colors: { percentage: number; color: string }[];
}

export function dropGradient(drop: DropInfo) {
	return `linear-gradient(90deg, ${drop.colors.map((stop) => `${stop.color} ${stop.percentage}%`).join(', ')})`;
}

export function DropPill({ drop }: { drop: DropInfo }) {
	return (
		<span
			className="dropPill"
			style={{ '--drop-gradient': dropGradient(drop) } as CSSProperties}
		>
			<span className="dropPillEmoji" aria-hidden="true">
				{drop.emoji}
			</span>
			<span>{drop.name} Drop</span>
		</span>
	);
}

export function dropPillHtml(drop: DropInfo) {
	const escape = (value: string) =>
		value.replace(
			/[&<>"']/g,
			(char) =>
				({
					'&': '&amp;',
					'<': '&lt;',
					'>': '&gt;',
					'"': '&quot;',
					"'": '&#39;',
				})[char] ?? char,
		);
	return `<span class="dropPill" style="--drop-gradient:${escape(dropGradient(drop))}"><span class="dropPillEmoji" aria-hidden="true">${escape(drop.emoji)}</span><span>${escape(drop.name)} Drop</span></span>`;
}
