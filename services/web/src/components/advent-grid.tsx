'use client';

import { useState } from 'react';
import { ASSETS } from '@/lib/assets';
import type { AdventLayout } from '@/lib/advent';

export interface MinedBlock {
	secret: boolean;
	claimed?: boolean;
	amountDabloons?: number;
	arrow?: string | null;
}

function AdventSnowflake({ index }: { index: number }) {
	const [texture, setTexture] = useState((index * 5) % 8);
	const randomize = () => {
		setTexture(Math.floor(Math.random() * 8));
	};
	return (
		<span
			className="adventFlake"
			style={{
				left: `${(index * 37) % 100}%`,
				backgroundImage: `url("${ASSETS.minecraft.vanilla}/textures/particle/generic_${texture}.png")`,
				animationDelay: `${-(index * 0.73)}s`,
				animationDuration: `${6 + (index % 5)}s`,
				width: `${8 + (index % 4) * 2}px`,
				height: `${8 + (index % 4) * 2}px`,
			}}
			onAnimationStart={randomize}
			onAnimationIteration={randomize}
		/>
	);
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
	const [mining, setMining] = useState<string | null>(null);
	const [hint, setHint] = useState('');
	const claimed = new Set(claimedDays);
	const dayByCell = new Map(
		layout.days.filter((day) => day.x >= 0).map((day) => [`${day.x},${day.y}`, day]),
	);

	function clickCell(x: number, y: number) {
		if (mode === 'edit') {
			onSelect?.(x, y);
			return;
		}
		const key = `${x},${y}`;
		if (mining) return;
		const cell = layout.rows[y]?.[x];
		const day = dayByCell.get(key);
		if (!cell?.foreground.length) return;
		if (day && day.day > today) {
			if (mode === 'play' && onFutureDay) {
				setMining(key);
				void Promise.all([
					onFutureDay(day.day),
					new Promise<void>((resolve) => window.setTimeout(resolve, 650)),
				]).finally(() => {
					setMining(null);
				});
			} else setHint('This block is not open yet.');
			return;
		}
		const count = mined[key] ?? 0;
		if (count >= cell.foreground.length) {
			if (revealed[key]?.secret && !revealed[key].claimed && !secretClaimed)
				onSecretGift?.(x, y);
			else if (day && !claimed.has(day.day) && today <= day.day + day.lookbackDays)
				onGift?.(day.day);
			return;
		}
		setMining(key);
		setHint('');
		window.setTimeout(() => {
			setMined((current) => ({ ...current, [key]: count + 1 }));
			setMining(null);
			if (count + 1 === cell.foreground.length && mode === 'play' && onFullyMined) {
				void onFullyMined(x, y).then((result) => {
					setRevealed((current) => ({ ...current, [key]: result }));
				});
			}
		}, 650);
	}

	return (
		<div className="adventScene">
			<div className="adventSky" aria-hidden="true">
				<span className="adventCloud cloudOne" />
				<span className="adventCloud cloudTwo" />
				{Array.from({ length: 56 }, (_, index) => (
					<AdventSnowflake key={index} index={index} />
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
						const remaining = cell?.foreground.slice(mined[key] ?? 0) ?? [];
						const isOpen = !!cell?.foreground.length && remaining.length === 0;
						const isClaimed = !!day && claimed.has(day.day);
						const discovery = revealed[key];
						const isSecretClaimed = discovery?.claimed === true || secretClaimed;
						const gift = day && isOpen && today <= day.day + day.lookbackDays;
						return (
							<button
								key={key}
								type="button"
								className={`adventTile ${cell ? '' : 'empty'} ${day ? 'numbered' : ''} ${isOpen ? 'open' : ''} ${isClaimed ? 'claimed' : ''} ${mining === key && remaining.length ? 'cracked' : ''} ${selected?.x === x && selected.y === y ? 'selected' : ''}`}
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
			{hint && (
				<p className="adventHint" role="status">
					{hint}
				</p>
			)}
		</div>
	);
}
