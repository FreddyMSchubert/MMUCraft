import type { CSSProperties, ReactNode } from 'react'

export function PlayerName({ name, color, children }: { name: string; color: string; children?: ReactNode }) {
	return <span className="playerName" style={playerNameStyle(color)}>{children ?? name}</span>
}

export function playerNameStyle(color: string) {
	return { '--player-color': color } as CSSProperties
}
