'use client';

import { useEffect, useRef, useState } from 'react';
import { Snowfall } from '@/components/snowfall';
import type { AdventLayout } from '@/lib/advent';

export interface MinedBlock {
	secret: boolean;
	claimed?: boolean;
	amountDabloons?: number;
	arrow?: string | null;
}

export function AdventGrid({
	layout,
	today,
	claimedDays = [],
	secretClaimed = false,
	mode = 'play',
	selected,
	onSelect,
	onGift,
	onSecretGift,
	onFutureDay,
	onFullyMined,
}: {
	layout: AdventLayout;
	today: number;
	claimedDays?: number[];
	secretClaimed?: boolean;
	mode?: 'play' | 'simulate' | 'edit';
	selected?: { x: number; y: number } | null;
	onSelect?: (x: number, y: number) => void;
	onGift?: (day: number) => void;
	onSecretGift?: (x: number, y: number) => void;
	onFutureDay?: (day: number) => Promise<void>;
	onFullyMined?: (x: number, y: number) => Promise<MinedBlock>;
}) {
	const [mined, setMined] = useState<Record<string, number>>({});
	const [revealed, setRevealed] = useState<Record<string, MinedBlock | undefined>>({});
	const miningLocks = useRef(new Set<string>());
	const timers = useRef<number[]>([]);
	const [mining, setMining] = useState<Set<string>>(new Set());
	const claimed = new Set(claimedDays);
	const dayByCell = new Map(
		layout.days.filter((day) => day.x >= 0).map((day) => [`${day.x},${day.y}`, day]),
	);
	useEffect(() => {
		const activeTimers = timers.current;
		return () => {
			for (const timer of activeTimers) window.clearTimeout(timer);
		};
	}, []);

	function clickCell(x: number, y: number) {
		if (mode === 'edit') {
			onSelect?.(x, y);
			return;
		}
		const key = `${x},${y}`;
		if (miningLocks.current.has(key)) return;
		const cell = layout.rows[y]?.[x];
		const day = dayByCell.get(key);
		if (!cell?.foreground.length) return;
		const count = mined[key] ?? 0;
		if (count >= cell.foreground.length) {
			if (revealed[key]?.secret && !revealed[key].claimed && !secretClaimed)
				onSecretGift?.(x, y);
			else if (day && !claimed.has(day.day)) onGift?.(day.day);
			return;
		}
		if (cell.foreground[count].url.endsWith('/bedrock.png')) return;
		if (day && day.day > today && count === 0) void onFutureDay?.(day.day);
		miningLocks.current.add(key);
		setMining((current) => new Set(current).add(key));
		const timer = window.setTimeout(() => {
			setMined((current) => ({ ...current, [key]: count + 1 }));
			miningLocks.current.delete(key);
			setMining((current) => {
				const next = new Set(current);
				next.delete(key);
				return next;
			});
			if (count + 1 === cell.foreground.length && onFullyMined) {
				void onFullyMined(x, y).then((result) => {
					setRevealed((current) => ({ ...current, [key]: result }));
				});
			}
		}, 650);
		timers.current.push(timer);
	}

	return (
		<div className={`adventScene ${mode === 'edit' ? 'adventEditScene' : ''}`}>
			<div className="adventSky" aria-hidden="true">
				<span className="adventCloud cloudOne" />
				<span className="adventCloud cloudTwo" />
				<Snowfall small />
			</div>
			<div
				className="adventWorld"
				style={{ gridTemplateColumns: `repeat(${layout.rows[0]?.length ?? 12}, 1fr)` }}
			>
				{layout.rows.flatMap((row, y) =>
					row.map((cell, x) => {
						const key = `${x},${y}`;
						const day = dayByCell.get(key);
						const remaining = cell?.foreground.slice(mined[key] ?? 0) ?? [];
						const isOpen = !!cell?.foreground.length && remaining.length === 0;
						const isClaimed = !!day && claimed.has(day.day);
						const discovery = revealed[key];
						const isSecretClaimed = discovery?.claimed === true || secretClaimed;
						const gift = day && isOpen;
						const bedrock =
							!!cell?.foreground.length &&
							(remaining[0]?.url ?? '').endsWith('/bedrock.png');
						return (
							<button
								key={key}
								type="button"
								className={`adventTile ${cell ? '' : 'empty'} ${day ? 'numbered' : ''} ${isOpen ? 'open' : ''} ${isClaimed ? 'claimed' : ''} ${mining.has(key) && remaining.length ? 'cracked' : ''} ${bedrock ? 'bedrock' : ''} ${selected?.x === x && selected.y === y ? 'selected' : ''}`}
								aria-label={
									mode === 'edit'
										? `Edit block at column ${x + 1}, row ${y + 1}${day ? `, day ${day.day}` : ''}`
										: discovery?.secret && isOpen
											? isSecretClaimed
												? 'Hidden present, claimed'
												: `Claim hidden present for ${discovery.amountDabloons} Dabloons`
											: gift
												? isClaimed
													? `Advent day ${day.day}, claimed`
													: `Take present from Advent day ${day.day}`
												: day
													? `Advent day ${day.day}`
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
								{gift && (
									<span className="adventPresent" aria-hidden="true">
										{isClaimed ? '✓' : '🎁'}
									</span>
								)}
								{discovery?.secret && isOpen && (
									<span className="adventSecret" aria-hidden="true">
										{isSecretClaimed ? '✓' : '🎁'}
									</span>
								)}
								{discovery?.arrow && isOpen && (
									<span className="adventArrow" aria-hidden="true">
										{discovery.arrow}
									</span>
								)}
								{remaining.length > 0 && (
									<span className="adventTileDoor">
										{remaining.toReversed().map((layer, index) => (
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
								)}
							</button>
						);
					}),
				)}
			</div>
		</div>
	);
}
