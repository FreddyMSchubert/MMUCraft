import Image from 'next/image';
import { PlayerName } from '@/components/player-name';
import type { PlayerSummary } from './player-data.types';

export function PlayerCell({ player }: { player: PlayerSummary }) {
	return (
		<div className="playerCell">
			<PlayerHead player={player} size="small" />
			<span className="playerCellName">
				<PlayerName name={player.minecraftUsername} color={player.profile.color} />
				{player.profile.pronouns && (
					<span className="playerCellPronouns"> ({player.profile.pronouns})</span>
				)}
			</span>
		</div>
	);
}

export function PlayerHead({ player, size }: { player: PlayerSummary; size: 'small' | 'large' }) {
	const label = `${player.minecraftUsername} head`;

	if (!player.avatarUrl) {
		return (
			<span
				className={`playerHead playerHead-${size} playerHeadFallback`}
				role="img"
				aria-label={label}
			>
				{player.minecraftUsername.charAt(0).toUpperCase()}
			</span>
		);
	}

	const pixels = size === 'small' ? 42 : 82;
	return (
		<Image
			unoptimized
			className={`playerHead playerHead-${size}`}
			src={player.avatarUrl}
			alt={label}
			width={pixels}
			height={pixels}
		/>
	);
}
