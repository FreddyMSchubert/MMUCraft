import type { CSSProperties, ReactNode } from 'react';

export interface PlayerEmoji {
	emoji: string;
	explanation: string;
	showInName: boolean;
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
	const nameEmojis = customEmojis.filter(({ showInName }) => showInName);
	return (
		<span className="playerName" style={playerNameStyle(color)}>
			{(isCommittee || nameEmojis.length > 0) && (
				<>
					{isCommittee ? '🛠️' : ''}
					{nameEmojis.map(({ emoji }) => emoji).join('')}{' '}
				</>
			)}
			{children ?? name}
		</span>
	);
}

export function playerNameStyle(color: string) {
	return { '--player-color': color } as CSSProperties;
}
