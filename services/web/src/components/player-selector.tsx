'use client';

import { useEffect, useRef, useState } from 'react';
import Fuse, { type IFuseOptions } from 'fuse.js';

export interface PlayerSelectorOption {
	id: number;
	minecraftUsername: string;
}

export function PlayerSelector({
	options,
	value,
	onChange,
	listboxId,
	placeholder = 'Search server players',
	disabled = false,
	showSuggestions = true,
	ariaLabel,
	required = false,
}: {
	options: readonly PlayerSelectorOption[];
	value: string;
	onChange: (value: string) => void;
	listboxId: string;
	placeholder?: string;
	disabled?: boolean;
	showSuggestions?: boolean;
	ariaLabel?: string;
	required?: boolean;
}) {
	const [open, setOpen] = useState(false);
	const [activeIndex, setActiveIndex] = useState(-1);
	const listbox = useRef<HTMLDivElement>(null);
	const suggestions = showSuggestions ? fuzzyFilter(options, value, ['minecraftUsername']) : [];
	const expanded = open && !disabled && suggestions.length > 0;
	useEffect(() => {
		const option = listbox.current?.children.item(activeIndex);
		if (expanded && option instanceof HTMLElement) option.scrollIntoView({ block: 'nearest' });
	}, [activeIndex, expanded]);
	const choose = (username: string) => {
		onChange(username);
		setOpen(false);
		setActiveIndex(-1);
	};
	const input = (
		<input
			value={value}
			onChange={(event) => {
				onChange(event.target.value);
				setOpen(true);
				setActiveIndex(-1);
			}}
			onFocus={() => {
				setOpen(true);
			}}
			onKeyDown={(event) => {
				if (!showSuggestions || disabled) return;
				if (event.key === 'ArrowDown' && suggestions.length) {
					event.preventDefault();
					setOpen(true);
					setActiveIndex((current) => Math.min(current + 1, suggestions.length - 1));
				} else if (event.key === 'ArrowUp' && suggestions.length) {
					event.preventDefault();
					setOpen(true);
					setActiveIndex((current) =>
						current < 0 ? suggestions.length - 1 : Math.max(current - 1, 0),
					);
				} else if (event.key === 'Enter' && expanded && suggestions[activeIndex]) {
					event.preventDefault();
					choose(suggestions[activeIndex].minecraftUsername);
				} else if (event.key === 'Escape' && open) {
					event.preventDefault();
					setOpen(false);
					setActiveIndex(-1);
				}
			}}
			placeholder={placeholder}
			disabled={disabled}
			aria-label={ariaLabel}
			aria-autocomplete={showSuggestions ? 'list' : undefined}
			aria-controls={showSuggestions ? listboxId : undefined}
			aria-expanded={showSuggestions ? expanded : undefined}
			aria-activedescendant={
				expanded && suggestions[activeIndex]
					? `${listboxId}-option-${suggestions[activeIndex].id}`
					: undefined
			}
			role={showSuggestions ? 'combobox' : undefined}
			autoComplete="off"
			required={required}
		/>
	);
	if (!showSuggestions) return input;
	return (
		<div
			className="playerSelector"
			onBlur={(event) => {
				if (!event.currentTarget.contains(event.relatedTarget)) {
					setOpen(false);
					setActiveIndex(-1);
				}
			}}
		>
			{input}
			<div
				ref={listbox}
				id={listboxId}
				className="playerSelectorList"
				role="listbox"
				hidden={!expanded}
			>
				{suggestions.map((player, index) => (
					<button
						key={player.id}
						id={`${listboxId}-option-${player.id}`}
						type="button"
						role="option"
						aria-selected={index === activeIndex}
						className="playerSelectorOption"
						onMouseEnter={() => {
							setActiveIndex(index);
						}}
						onMouseDown={(event) => {
							event.preventDefault();
						}}
						onClick={() => {
							choose(player.minecraftUsername);
						}}
					>
						{player.minecraftUsername}
					</button>
				))}
			</div>
		</div>
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
