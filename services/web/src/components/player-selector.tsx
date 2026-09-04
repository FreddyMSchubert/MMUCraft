'use client';

import Fuse, { type IFuseOptions } from 'fuse.js';
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
	showSuggestions = true,
	ariaLabel,
	required = false,
}: {
	options: readonly PlayerSelectorOption[];
	value: string;
	onChange: (value: string) => void;
	datalistId: string;
	placeholder?: string;
	disabled?: boolean;
	showSuggestions?: boolean;
	ariaLabel?: string;
	required?: boolean;
}) {
	const suggestions = fuzzyFilter(options, value, ['minecraftUsername']);
	return (
		<>
			<input
				list={showSuggestions ? datalistId : undefined}
				value={value}
				onChange={(event) => {
					onChange(event.target.value);
				}}
				placeholder={placeholder}
				disabled={disabled}
				aria-label={ariaLabel}
				required={required}
			/>
			{showSuggestions && (
				<datalist id={datalistId}>
					{suggestions.map((player) => (
						<option
							className="playerName"
							style={playerNameStyle(player.color)}
							key={player.id}
							value={player.minecraftUsername}
							label={
								value.trim()
									? `${value.trim()} → ${player.minecraftUsername}`
									: undefined
							}
						/>
					))}
				</datalist>
			)}
		</>
	);
}

export function fuzzyFilter<T>(items: readonly T[], query: string, keys: IFuseOptions<T>['keys']) {
	const search = query.trim();
	return search
		? new Fuse(items, { keys, threshold: 0.35, ignoreLocation: true })
				.search(search)
				.map(({ item }) => item)
		: [...items];
}
