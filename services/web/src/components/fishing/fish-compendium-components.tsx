import Image from 'next/image';
import type React from 'react';
import { PlayerName } from '@/components/player-name';
import {
	conditionSentence,
	formatDate,
	formatLength,
	isLuckRarity,
	positionTooltip,
	rarityInfo,
	tagLines,
} from './fish-compendium-format';
import {
	GROUPS,
	GROUP_LABELS,
	RARITIES,
	TAGS,
	type CatchPoint,
	type CompendiumFish,
	type PlayerOption,
	type Rarity,
	type ServerRecord,
} from './fish-compendium.types';

export function FishGrid({
	fish,
	compact,
	player,
}: {
	fish: CompendiumFish[];
	compact: boolean;
	player?: PlayerOption;
}) {
	return (
		<div className={compact ? 'fishGrid compact' : 'fishGrid'}>
			{fish.map((entry) => (
				<FishCard key={entry.id} fish={entry} compact={compact} player={player} />
			))}
		</div>
	);
}

export function FishCard({
	fish,
	compact,
	player,
}: {
	fish: CompendiumFish;
	compact: boolean;
	player?: PlayerOption;
}) {
	const caught = fish.catch !== null;
	const rarity = rarityInfo(fish.rarity);
	return (
		<article
			className={`fishCard rarity-${fish.rarity} ${caught ? 'caught' : 'unknown'}`}
			style={{ '--fish-rarity': rarity.color } as React.CSSProperties}
			tabIndex={0}
			onPointerEnter={positionTooltip}
			onFocus={positionTooltip}
		>
			<div className="fishTagLines">
				{tagLines(fish.tags).map((line, index) => (
					<span key={index}>{line}</span>
				))}
			</div>
			{caught ? (
				<Image unoptimized src={fish.iconUrl} alt={fish.title} width={96} height={96} />
			) : (
				<span className="fishQuestion" aria-label="Uncaught fish">
					?
				</span>
			)}
			<div className="fishCardName">{caught ? fish.title : '???'}</div>
			<div className="fishTooltip" role="tooltip">
				<strong>{caught ? fish.title : '???'}</strong>
				{compact ? (
					<CompactRarity rarity={rarity} />
				) : (
					<ConditionGuide fish={fish} rarity={rarity} />
				)}
				{caught && !compact && fish.facts.length > 0 && (
					<div className="fishFacts">
						{fish.facts.map((fact) => (
							<span key={fact}>{fact}</span>
						))}
					</div>
				)}
				{fish.catch && (
					<div className="fishPersonalRecords">
						<RecordLine label="First caught" point={fish.catch.first} player={player} />
						<RecordLine
							label="Personal largest"
							point={fish.catch.largest}
							player={player}
						/>
						<RecordLine
							label="Personal smallest"
							point={fish.catch.smallest}
							player={player}
						/>
					</div>
				)}
				{!fish.catch && <span>Not caught yet.</span>}
				{!compact && (
					<div className="fishServerRecords">
						<ServerRecordLine label="Server largest" record={fish.serverLargest} />
						<ServerRecordLine label="Server smallest" record={fish.serverSmallest} />
					</div>
				)}
			</div>
		</article>
	);
}

export function CompendiumStats({
	fish,
	caughtTotal,
}: {
	fish: CompendiumFish[];
	caughtTotal: number;
}) {
	const percentage = fish.length ? Math.round((caughtTotal / fish.length) * 100) : 0;
	return (
		<section className="fishStats">
			<div className="fishStatsHeading">
				<div>
					<span>Your collection</span>
					<strong>
						{caughtTotal} / {fish.length} caught
					</strong>
				</div>
				<b>{percentage}%</b>
			</div>
			<progress value={caughtTotal} max={fish.length || 1}>
				{percentage}%
			</progress>
			<div className="fishRarityBars">
				{RARITIES.map((rarity) => {
					const entries = fish.filter((entry) => entry.rarity === rarity.id);
					const caught = entries.filter((entry) => entry.catch).length;
					const width = entries.length ? (caught / entries.length) * 100 : 0;
					return (
						<div className="fishRarityProgress" key={rarity.id}>
							<b style={{ color: rarity.color }}>{rarity.label}</b>
							<span>
								<i style={{ width: `${width}%`, background: rarity.color }} />
							</span>
							<small>
								{caught}/{entries.length}
							</small>
						</div>
					);
				})}
			</div>
		</section>
	);
}

export function CompendiumGuide({
	rarityFilters,
	tagFilters,
	onToggleRarity,
	onToggleTag,
	onReset,
}: {
	rarityFilters: Set<Rarity>;
	tagFilters: Set<string>;
	onToggleRarity: (rarity: Rarity) => void;
	onToggleTag: (tag: string) => void;
	onReset: () => void;
}) {
	const filtered = rarityFilters.size > 0 || tagFilters.size > 0;
	return (
		<div className="fishGuide">
			<button
				type="button"
				className={!filtered ? 'active fishAllFilter' : 'fishAllFilter'}
				aria-pressed={!filtered}
				onClick={onReset}
			>
				All
			</button>
			<div className="fishFilterGroups">
				<FilterGroup label="Rarity">
					{RARITIES.map((rarity) => (
						<button
							type="button"
							key={rarity.id}
							className={rarityFilters.has(rarity.id) ? 'active' : ''}
							aria-pressed={rarityFilters.has(rarity.id)}
							onClick={() => {
								onToggleRarity(rarity.id);
							}}
						>
							<i style={{ background: rarity.color }} />
							{rarity.label}
						</button>
					))}
				</FilterGroup>
				{GROUPS.map((group) => (
					<FilterGroup key={group} label={GROUP_LABELS[group]}>
						{Object.entries(TAGS)
							.filter(([, info]) => info.group === group)
							.map(([tag, info]) => (
								<button
									type="button"
									key={tag}
									className={tagFilters.has(tag) ? 'active' : ''}
									aria-pressed={tagFilters.has(tag)}
									onClick={() => {
										onToggleTag(tag);
									}}
								>
									<b>{info.emoji}</b>
									{info.label}
								</button>
							))}
					</FilterGroup>
				))}
			</div>
		</div>
	);
}

export function FilterGroup({ label, children }: { label: string; children: React.ReactNode }) {
	return (
		<div className="fishFilterGroup">
			<span>{label}</span>
			<div>{children}</div>
		</div>
	);
}

export function ConditionGuide({
	fish,
	rarity,
}: {
	fish: CompendiumFish;
	rarity: (typeof RARITIES)[number];
}) {
	return (
		<div className="fishConditions">
			<span>
				The {fish.catch ? fish.title : '???'} is a{' '}
				<b style={{ color: rarity.color }}>{rarity.label}</b> fish catchable{' '}
				{conditionSentence(fish.tags)}.
			</span>
			{isLuckRarity(rarity.id) && (
				<span className="fishLuckWarning">
					Highly unlikely to be caught without a high Luck level.
				</span>
			)}
		</div>
	);
}

export function CompactRarity({ rarity }: { rarity: (typeof RARITIES)[number] }) {
	return (
		<div className="fishConditions">
			<span>
				Rarity: <b style={{ color: rarity.color }}>{rarity.label}</b>
			</span>
			{isLuckRarity(rarity.id) && (
				<span className="fishLuckWarning">
					Highly unlikely to be caught without a high Luck level.
				</span>
			)}
		</div>
	);
}

export function RecordLine({
	label,
	point,
	player,
}: {
	label: string;
	point: CatchPoint;
	player?: PlayerOption;
}) {
	return (
		<span className="fishOwnRecord">
			{player?.avatarUrl && (
				<Image unoptimized src={player.avatarUrl} alt="" width={21} height={21} />
			)}
			<span>
				<b>{label}:</b> {formatLength(point.lengthCm)} · {formatDate(point.caughtAtUnixMs)}
			</span>
		</span>
	);
}

export function ServerRecordLine({
	label,
	record,
}: {
	label: string;
	record: ServerRecord | null;
}) {
	if (!record)
		return (
			<span>
				<b>{label}:</b> No catches yet
			</span>
		);
	return (
		<span className="fishServerRecord">
			{record.player.avatarUrl && (
				<Image unoptimized src={record.player.avatarUrl} alt="" width={21} height={21} />
			)}
			<b>{label}:</b>{' '}
			<PlayerName name={record.player.minecraftUsername} color={record.player.color} /> ·{' '}
			{formatLength(record.lengthCm)}
		</span>
	);
}
