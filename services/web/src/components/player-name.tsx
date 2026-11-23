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
	return (
		<span className="playerName" style={playerNameStyle(isBigBoss ? '#B35CFF' : color)}>
			{isBigBoss && <span className="bigBossNamePrefix">[The Big Boss] </span>}
			{!isBigBoss && (isCommittee || nameEmojis.length > 0) && (
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
