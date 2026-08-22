import type { StatOption } from './player-data.types';
import { groupStatOptions } from './player-display-format';

export function mergeStatOptions(current: StatOption[], next: StatOption[]) {
	return [...new Map([...current, ...next].map((option) => [option.key, option])).values()];
}

export function StatOptionGroups({ options }: { options: StatOption[] }) {
	const grouped = groupStatOptions(options);

	return (
		<>
			{grouped.map((group) => (
				<optgroup key={group.key} label={group.label}>
					{group.options.map((option) => (
						<option key={option.key} value={option.key}>
							{option.label}
						</option>
					))}
				</optgroup>
			))}
		</>
	);
}
