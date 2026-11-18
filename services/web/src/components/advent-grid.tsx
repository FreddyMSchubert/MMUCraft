'use client';

import { useState } from 'react';
import type { AdventLayout } from '@/lib/advent';

export function AdventGrid({
	layout,
	today,
	claimedDays = [],
	mode = 'play',
	selected,
	onSelect,
	onGift,
}: {
	layout: AdventLayout;
	today: number;
	claimedDays?: number[];
	mode?: 'play' | 'simulate' | 'edit';
	selected?: { x: number; y: number } | null;
	onSelect?: (x: number, y: number) => void;
	onGift?: (day: number) => void;
}) {
	const [opened, setOpened] = useState<number[]>([]);
	const [cracked, setCracked] = useState<string | null>(null);
	const [hint, setHint] = useState('');
	const claimed = new Set(claimedDays);
	const open = new Set([...opened, ...claimedDays]);
	const dayByCell = new Map(
		layout.days.filter((day) => day.x >= 0).map((day) => [`${day.x},${day.y}`, day]),
	);

	function clickCell(x: number, y: number) {
		if (mode === 'edit') {
			onSelect?.(x, y);
			return;
		}
		const day = dayByCell.get(`${x},${y}`);
		if (day && mode === 'play' && open.has(day.day) && !claimed.has(day.day)) {
			onGift?.(day.day);
			return;
		}
		if (!day || day.day > today || today > day.day + day.lookbackDays) {
			const key = `${x},${y}`;
			setCracked(key);
			setHint(
				today < 1
					? 'The first door opens on 1 December.'
					: today <= 24
						? `Nice try. Block ${today} has today's present.`
						: 'That present is no longer available.',
			);
			window.setTimeout(() => {
				setCracked((current) => (current === key ? null : current));
			}, 650);
			return;
		}
		setOpened((days) => (days.includes(day.day) ? days : [...days, day.day]));
		setHint('');
	}

	return (
		<div className="adventScene">
			<div className="adventSky" aria-hidden="true">
				<span className="adventCloud cloudOne" />
				<span className="adventCloud cloudTwo" />
				{Array.from({ length: 30 }, (_, index) => (
					<span
						key={index}
						className="adventFlake"
						style={{
							left: `${(index * 37) % 100}%`,
							animationDelay: `${-(index * 0.73)}s`,
							animationDuration: `${6 + (index % 5)}s`,
						}}
					/>
				))}
			</div>
			<div
				className="adventWorld"
				style={{ gridTemplateColumns: `repeat(${layout.rows[0]?.length ?? 12}, 1fr)` }}
			>
				{layout.rows.flatMap((row, y) =>
					row.map((cell, x) => {
						const key = `${x},${y}`;
						const day = dayByCell.get(key);
						const isOpen = !!day && open.has(day.day);
						const isClaimed = !!day && claimed.has(day.day);
						return (
							<button
								key={key}
								type="button"
								className={`adventTile ${cell ? '' : 'empty'} ${day ? 'numbered' : ''} ${isOpen ? 'open' : ''} ${isClaimed ? 'claimed' : ''} ${cracked === key ? 'cracked' : ''} ${selected?.x === x && selected.y === y ? 'selected' : ''}`}
								aria-label={
									mode === 'edit'
										? `Edit block at column ${x + 1}, row ${y + 1}${day ? `, day ${day.day}` : ''}`
										: day
											? isClaimed
												? `Advent day ${day.day}, claimed`
												: isOpen && mode === 'play'
													? `Take present from Advent day ${day.day}`
													: `Advent day ${day.day}`
											: `Block at column ${x + 1}, row ${y + 1}`
								}
								aria-expanded={day ? isOpen : undefined}
								onClick={() => {
									clickCell(x, y);
								}}
							>
								<span className="adventTileBackground">
									{cell?.background.map((layer, index) => (
										<span
											key={index}
											className={`adventTexture ${layer.url.includes('leaves') ? 'leaves' : ''} ${layer.url.includes('grass_block_top') ? 'grass' : ''}`}
											style={{
												backgroundImage: `url("${layer.url}")`,
												transform: `rotate(${layer.rotation}deg)`,
											}}
										/>
									))}
								</span>
								{day && (
									<span className="adventPresent" aria-hidden="true">
										{isClaimed ? '✓' : '🎁'}
									</span>
								)}
								<span className="adventTileDoor">
									{cell?.foreground.toReversed().map((layer, index) => (
										<span
											key={index}
											className={`adventTexture ${layer.url.includes('leaves') ? 'leaves' : ''} ${layer.url.includes('grass_block_top') ? 'grass' : ''}`}
											style={{
												backgroundImage: `url("${layer.url}")`,
												transform: `rotate(${layer.rotation}deg)`,
											}}
										/>
									))}
									{day && <strong className="adventNumber">{day.day}</strong>}
									<span className="adventCracks" />
								</span>
							</button>
						);
					}),
				)}
			</div>
			{hint && (
				<p className="adventHint" role="status">
					{hint}
				</p>
			)}
		</div>
	);
}
