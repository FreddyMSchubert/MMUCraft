import type { CSSProperties, ReactNode } from 'react';

export interface PlayerEmoji {
	emoji: string;
	explanation: string;
}

export function PlayerName({
	name,
	color,
	emojis = [],
	children,
}: {
	name: string;
	color: string;
	emojis?: readonly PlayerEmoji[];
	children?: ReactNode;
}) {
	return (
		<span className="playerName" style={playerNameStyle(color)}>
			{emojis.length > 0 && <>{emojis.map(({ emoji }) => emoji).join('')} </>}
			{children ?? name}
		</span>
	);
}

export function playerNameStyle(color: string) {
	return { '--player-color': color } as CSSProperties;
}
