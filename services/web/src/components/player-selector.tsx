'use client';

import { playerNameStyle } from '@/components/player-name';

export interface PlayerSelectorOption {
	id: number;
	minecraftUsername: string;
	color: string;
}

export function PlayerSelector({
	options,
	value,
	onChange,
	datalistId,
	placeholder = 'Search server players',
	disabled = false,
}: {
	options: readonly PlayerSelectorOption[];
	value: string;
	onChange: (value: string) => void;
	datalistId: string;
	placeholder?: string;
	disabled?: boolean;
}) {
	return (
		<>
			<input
				list={datalistId}
				value={value}
				onChange={(event) => {
					onChange(event.target.value);
				}}
				placeholder={placeholder}
				disabled={disabled}
			/>
			<datalist id={datalistId}>
				{options.map((player) => (
					<option
						className="playerName"
						style={playerNameStyle(player.color)}
						key={player.id}
						value={player.minecraftUsername}
					/>
				))}
			</datalist>
		</>
	);
}
