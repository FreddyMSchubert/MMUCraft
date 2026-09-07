import type { CSSProperties, ReactNode } from 'react';

export interface PlayerEmoji {
	emoji: string;
	explanation: string;
}

export function PlayerName({
	name,
	color,
	isCommittee = false,
	customEmojis = [],
	children,
}: {
	name: string;
	color: string;
	isCommittee?: boolean;
	customEmojis?: readonly PlayerEmoji[];
	children?: ReactNode;
}) {
	return (
		<span className="playerName" style={playerNameStyle(color)}>
			{(isCommittee || customEmojis.length > 0) && (
				<>
					{isCommittee ? '🛠️' : ''}
					{customEmojis.map(({ emoji }) => emoji).join('')}{' '}
				</>
			)}
			{children ?? name}
		</span>
	);
}

export function playerNameStyle(color: string) {
	return { '--player-color': color } as CSSProperties;
}
