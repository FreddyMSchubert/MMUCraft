'use client';

import { createContext, useContext, type CSSProperties, type ReactNode } from 'react';

export const BigBossNameContext = createContext('');
export const useBigBossName = () => useContext(BigBossNameContext);

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
	const isBigBoss = useBigBossName().toLowerCase() === name.toLowerCase();
	const nameEmojis = customEmojis.filter(({ showInName }) => showInName);
	const emojis = `${isBigBoss ? '👑' : isCommittee ? '🛠️' : ''}${nameEmojis.map(({ emoji }) => emoji).join('')}`;
	return (
		<span className="playerName" style={playerNameStyle(isBigBoss ? '#B35CFF' : color)}>
			{emojis && <>{emojis} </>}
			{children ?? name}
		</span>
	);
}

export function playerNameStyle(color: string) {
	return { '--player-color': color } as CSSProperties;
}
