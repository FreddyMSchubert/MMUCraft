'use client';

import { type CSSProperties, useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { DabloonAmount, DabloonText } from '@/components/dabloon-amount';
import { DropPill, type DropInfo } from '@/components/drop-pill';
import type { CosmeticPreviewView } from '@/lib/site-settings';
import { ShopPreview } from './shop-item-preview';
import {
	effectivePrice,
	formatIngredient,
	formatOption,
	isSoldOut,
	type ShopItem,
} from './shop-catalog.types';

export function FilterRow({
	label,
	options,
	selected,
	membershipLocked = false,
	onSelect,
}: {
	label: string;
	options: { value: string; label: string; gradient?: string }[];
	selected: string;
	membershipLocked?: boolean;
	onSelect: (value: string) => void;
}) {
	return (
		<div className="shopFilterRow">
			<span>{label}</span>
			<div role="group" aria-label={`${label} filter`}>
				{options.map((option) => (
					<button
						type="button"
						key={option.value}
						className={`${selected === option.value ? 'active' : ''} filter-${option.value} ${option.gradient ? 'dropFilter' : ''}`}
						style={
							option.gradient
								? ({ '--drop-gradient': option.gradient } as CSSProperties)
								: undefined
						}
						aria-pressed={selected === option.value}
						onClick={() => {
							onSelect(option.value);
						}}
					>
						{membershipLocked &&
							['dyeable', 'animated', 'members-only'].includes(option.value) && (
								<ShopLock />
							)}
						{option.value === 'animated' ? <AnimatedLabel /> : option.label}
						{option.value === 'emissive' && <EmissiveSparks />}
					</button>
				))}
			</div>
		</div>
	);
}

export function ShopCard({
	item,
	drop,
	hovered,
	hidePreview,
	allow3d,
	onHover,
	onOpen,
}: {
	item: ShopItem;
	drop: DropInfo | null;
	hovered: boolean;
	hidePreview: boolean;
	allow3d: boolean;
	onHover: (id: string | null) => void;
	onOpen: (item: ShopItem) => void;
}) {
	return (
		<article
			className={`shopCard shopCard-${item.type} rarity-${item.rarity} ${!item.available && !item.membershipLocked ? 'unavailable' : ''}`}
			tabIndex={0}
			role="button"
			aria-label={`View ${item.title}`}
			onClick={() => {
				onOpen(item);
			}}
			onKeyDown={(event) => {
				if (event.key === 'Enter' || event.key === ' ') {
					event.preventDefault();
					onOpen(item);
				}
			}}
			onFocus={() => {
				onHover(item.id);
			}}
			onBlur={() => {
				onHover(null);
			}}
			onPointerEnter={() => {
				onHover(item.id);
			}}
			onPointerLeave={() => {
				onHover(null);
			}}
		>
			<div className="shopImageFrame" aria-hidden="true">
				<ShopPreview item={item} hovered={hovered} hidden={hidePreview} allow3d={allow3d} />
				{item.isDailyDeal && (
					<span className="shopDealBadge">−{item.discountPercent}% today</span>
				)}
			</div>
			<div className="shopCardBody">
				<ItemBadges item={item} drop={drop} />
				<h4>
					<DabloonText>{item.title}</DabloonText>
				</h4>
				<Price item={item} />
			</div>
			<button
				type="button"
				className="shopCardFoot"
				disabled={isSoldOut(item) || item.membershipLocked}
				onClick={(event) => {
					event.stopPropagation();
					onOpen(item);
				}}
			>
				{item.membershipLocked ? (
					<>
						<ShopLock />
						Members only
					</>
				) : isSoldOut(item) ? (
					soldOutLabel(item)
				) : (
					'Buy now'
				)}
			</button>
		</article>
	);
}

export function ShopDetails({
	item,
	drop,
	buying,
	hidePreview,
	previewView,
	skinUrl,
	onSelectPreviewView,
	onClose,
	onBuy,
}: {
	item: ShopItem;
	drop: DropInfo | null;
	buying: boolean;
	hidePreview: boolean;
	previewView: CosmeticPreviewView;
	skinUrl: string | null;
	onSelectPreviewView: (view: CosmeticPreviewView) => void;
	onClose: () => void;
	onBuy: (item: ShopItem) => Promise<void>;
}) {
	const [nightSelection, setNightSelection] = useState({ itemId: item.id, enabled: false });
	const nightMode = nightSelection.itemId === item.id && nightSelection.enabled;
	const [emissionSupport, setEmissionSupport] = useState<{
		modelUrl: string;
		supported: boolean;
	} | null>(null);
	const supportsEmission =
		emissionSupport?.modelUrl === item.modelUrl && emissionSupport.supported;
	const effectiveNightMode = supportsEmission && nightMode;
	const hasViewControls = item.type === 'cosmetic' || item.decoBlock;
	const onEmissionSupport = useCallback((modelUrl: string, supported: boolean) => {
		setEmissionSupport({ modelUrl, supported });
	}, []);
	const effectivePreviewView =
		(previewView === 'player' && (item.type !== 'cosmetic' || !skinUrl)) ||
		(previewView === 'item-frame' && !item.decoBlock)
			? 'cosmetic'
			: previewView;

	useEffect(() => {
		const close = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onClose();
		};
		window.addEventListener('keydown', close);
		document.body.classList.add('shopModalOpen');
		return () => {
			window.removeEventListener('keydown', close);
			document.body.classList.remove('shopModalOpen');
		};
	}, [onClose]);

	return createPortal(
		<div
			className="shopDetailsBackdrop"
			role="presentation"
			onMouseDown={(event) => {
				if (event.target === event.currentTarget) onClose();
			}}
		>
			<section
				className={`shopDetails shopCard-${item.type} rarity-${item.rarity}`}
				role="dialog"
				aria-modal="true"
				aria-labelledby="shop-detail-title"
			>
				<button
					type="button"
					className="shopDetailsClose"
					aria-label="Close details"
					onClick={onClose}
				>
					×
				</button>
				<div className="shopDetailsHero">
					<div className="shopDetailsPreview">
						<div
							className={`shopDetailsPreviewEmbed ${effectiveNightMode ? 'night' : ''}`}
						>
							<ShopPreview
								key={effectivePreviewView}
								item={item}
								hovered={false}
								interactive
								hidden={hidePreview}
								view={effectivePreviewView}
								skinUrl={skinUrl}
								nightMode={effectiveNightMode}
								onEmissionSupport={onEmissionSupport}
							/>
							{item.renderMode === 'model' && !hidePreview && (
								<span>Hover to pause · drag to rotate</span>
							)}
						</div>
						{item.renderMode === 'model' &&
							!hidePreview &&
							(hasViewControls || supportsEmission) && (
								<div className="shopPreviewControls">
									{hasViewControls && (
										<CosmeticViewControl
											selected={effectivePreviewView}
											cosmetic={item.type === 'cosmetic'}
											decoBlock={item.decoBlock}
											skinAvailable={Boolean(skinUrl)}
											onSelect={onSelectPreviewView}
										/>
									)}
									{supportsEmission && (
										<button
											type="button"
											className="shopNightToggle"
											aria-label="Night preview"
											aria-pressed={nightMode}
											title={nightMode ? 'Switch to day' : 'Switch to night'}
											onClick={() => {
												setNightSelection({
													itemId: item.id,
													enabled: !nightMode,
												});
											}}
										>
											<span aria-hidden="true">{nightMode ? '☾' : '☀'}</span>
										</button>
									)}
								</div>
							)}
					</div>
					<div className="shopDetailsSummary">
						<ItemBadges item={item} drop={drop} />
						<h2 id="shop-detail-title">
							<DabloonText>{item.title}</DabloonText>
						</h2>
						<Price item={item} />
						<button
							type="button"
							className="shopDetailsBuy"
							disabled={!item.available || buying}
							onClick={() => void onBuy(item)}
						>
							{item.membershipLocked ? (
								<>
									<ShopLock />
									Members only
								</>
							) : item.available ? (
								buying ? (
									'Buying…'
								) : (
									<>
										Buy now ·{' '}
										<DabloonAmount
											amount={effectivePrice(item)}
											tone="inherit"
										/>
									</>
								)
							) : (
								soldOutLabel(item)
							)}
						</button>
						{item.description && (
							<div className="shopItemEffect">
								<strong>{item.type === 'charm' ? 'Effect' : 'Description'}</strong>
								<p>
									<DabloonText>{item.description}</DabloonText>
								</p>
							</div>
						)}
						{item.tooltips.length > 0 && (
							<div className="shopItemTooltips">
								{item.tooltips.map((tooltip) => (
									<p key={tooltip}>
										<DabloonText>{tooltip}</DabloonText>
									</p>
								))}
							</div>
						)}
					</div>
				</div>
				{item.type === 'charm' && item.charmDetails && (
					<CharmProgression details={item.charmDetails} />
				)}
			</section>
		</div>,
		document.body,
	);
}

const COSMETIC_VIEW_OPTIONS: {
	value: CosmeticPreviewView;
	label: string;
	icon: string;
}[] = [
	{ value: 'cosmetic', label: 'Detail', icon: '🔍' },
	{ value: 'player', label: 'Cosmetic', icon: '👒' },
	{ value: 'item-frame', label: 'Block', icon: '🧊' },
];

function CosmeticViewControl({
	selected,
	cosmetic,
	decoBlock,
	skinAvailable,
	onSelect,
}: {
	selected: CosmeticPreviewView;
	cosmetic: boolean;
	decoBlock: boolean;
	skinAvailable: boolean;
	onSelect: (view: CosmeticPreviewView) => void;
}) {
	return (
		<div className="shopCosmeticViewControl" role="group" aria-label="Preview view">
			{COSMETIC_VIEW_OPTIONS.filter(
				(option) =>
					(option.value !== 'player' || (cosmetic && skinAvailable)) &&
					(option.value !== 'item-frame' || decoBlock),
			).map((option) => (
				<button
					type="button"
					key={option.value}
					aria-pressed={selected === option.value}
					title={option.label}
					onClick={() => {
						onSelect(option.value);
					}}
				>
					<span aria-hidden="true">{option.icon}</span>
					<small>{option.label}</small>
				</button>
			))}
		</div>
	);
}

function CharmProgression({ details }: { details: NonNullable<ShopItem['charmDetails']> }) {
	const receivedBroken = details.minLevel === 0;
	return (
		<section className="charmProgression">
			<div className="charmProgressionHeading">
				<div>
					<p>Charm progression</p>
					<h3>Levels & upgrade costs</h3>
				</div>
				<div className={`charmReceiveLevel ${receivedBroken ? 'broken' : ''}`}>
					<span>You receive</span>
					<strong>{receivedBroken ? 'Broken' : `Level ${details.minLevel}`}</strong>
					<small>
						{receivedBroken
							? `Repair it by upgrading to level 1 · Maximum level ${details.maxLevel}`
							: `Maximum level ${details.maxLevel}`}
					</small>
				</div>
			</div>
			<div className="charmTableWrap">
				<table>
					<thead>
						<tr>
							<th>State</th>
							<th>Effect</th>
							<th>Cost to reach state</th>
						</tr>
					</thead>
					<tbody>
						{receivedBroken && (
							<tr className="received broken">
								<th>Broken</th>
								<td>
									No effect — upgrade it to level 1 to repair and activate it.
								</td>
								<td>Received in this state</td>
							</tr>
						)}
						{details.levels.map((level) => (
							<tr
								key={level.level}
								className={level.level === details.minLevel ? 'received' : ''}
							>
								<th>Lv. {level.level}</th>
								<td>{level.abilityStatusCurrent || '—'}</td>
								<td>
									{level.level === details.minLevel && !receivedBroken ? (
										'Received in this state'
									) : (
										<div className="charmLevelCost">
											{level.dabloons > 0 && (
												<DabloonAmount
													amount={level.dabloons}
													format="full"
												/>
											)}
											{level.upgradeIngredients.length > 0 && (
												<IngredientList
													ingredients={level.upgradeIngredients}
												/>
											)}
											{level.dabloons === 0 &&
												level.upgradeIngredients.length === 0 &&
												'Free'}
										</div>
									)}
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</section>
	);
}

function ItemBadges({ item, drop }: { item: ShopItem; drop: DropInfo | null }) {
	const tags = [
		item.dyeable ? (
			<span key="dyeable" className="shopTag dyeable">
				{item.membershipLocked && <ShopLock />}
				Dyeable
			</span>
		) : null,
		item.animated ? (
			<span key="animated" className="shopTag animated">
				{item.membershipLocked && <ShopLock />}
				<AnimatedLabel />
			</span>
		) : null,
		item.luminous ? (
			<span key="luminous" className="shopTag luminous">
				{item.membershipLocked && <ShopLock />}
				Luminous
			</span>
		) : null,
		item.emissive ? (
			<span
				key="emissive"
				className="shopTag emissive"
				title="Emits particles when worn or placed"
			>
				{item.membershipLocked && <ShopLock />}
				Emissive
				<EmissiveSparks />
			</span>
		) : null,
		item.membersOnly ? (
			<span key="members-only" className="shopTag membersOnly">
				{item.membershipLocked && <ShopLock />}
				Members-only
			</span>
		) : null,
		item.isDailyDeal ? (
			<span key="discounted" className="shopTag discounted">
				Discounted −{item.discountPercent}%
			</span>
		) : null,
		isSoldOut(item) ? (
			<span key="sold-out" className="shopTag soldOut">
				{soldOutLabel(item)}
			</span>
		) : null,
	].filter(Boolean);
	return (
		<div className="shopBadgeLines">
			<div className="shopBadges">
				<span>{formatOption(item.type)}</span>
				<span>{formatOption(item.rarity)}</span>
			</div>
			{tags.length > 0 && <div className="shopTagBadges">{tags}</div>}
			{drop && (
				<div className="shopDropBadge">
					<DropPill drop={drop} />
				</div>
			)}
		</div>
	);
}

function soldOutLabel(item: ShopItem) {
	return item.dailyLimitReached ? 'Sold out for the day' : 'Sold out';
}

function ShopLock() {
	return <span aria-hidden="true">🔒 </span>;
}

const EMISSIVE_SPARKS = Array.from({ length: 14 }, (_, index) => {
	const offset = 9 + ((index * 47 + 19) % 82);
	const side = index % 4;
	return {
		left: side === 0 ? `${offset}%` : side === 1 ? '100%' : side === 2 ? `${offset}%` : '0%',
		top: side === 0 ? '0%' : side === 1 ? `${offset}%` : side === 2 ? '100%' : `${offset}%`,
		color: `hsl(${126 + ((index * 31) % 34)} 95% ${60 + ((index * 17) % 21)}%)`,
		delay: `${-((index * 0.37) % 2.8)}s`,
		duration: `${1.5 + ((index * 7) % 9) / 10}s`,
		driftX: `${((index * 13) % 7) - 3}px`,
		driftY: `${((index * 11) % 7) - 3}px`,
	};
});

function EmissiveSparks() {
	return (
		<span className="emissiveSparks" aria-hidden="true">
			{EMISSIVE_SPARKS.map((spark, index) => (
				<span
					key={index}
					className="emissiveSpark"
					style={
						{
							'--spark-left': spark.left,
							'--spark-top': spark.top,
							'--spark-color': spark.color,
							'--spark-delay': spark.delay,
							'--spark-duration': spark.duration,
							'--spark-drift-x': spark.driftX,
							'--spark-drift-y': spark.driftY,
						} as CSSProperties
					}
				/>
			))}
		</span>
	);
}

function AnimatedLabel() {
	return (
		<span className="animatedWave" aria-label="Animated">
			{'Animated'.split('').map((letter, index) => (
				<span
					key={`${letter}-${index}`}
					aria-hidden="true"
					style={{ '--wave-index': index } as CSSProperties}
				>
					{letter}
				</span>
			))}
		</span>
	);
}

function IngredientList({ ingredients }: { ingredients: string[] }) {
	const counts = new Map<string, number>();
	for (const ingredient of ingredients) counts.set(ingredient, (counts.get(ingredient) ?? 0) + 1);
	if (!counts.size) return <>—</>;
	return (
		<div className="charmIngredients">
			{[...counts].map(([ingredient, count]) => (
				<span key={ingredient}>
					{count > 1 && <strong>{count}×</strong>} {formatIngredient(ingredient)}
				</span>
			))}
		</div>
	);
}

function Price({ item }: { item: ShopItem }) {
	return item.isDailyDeal ? (
		<p className="shopPrice deal">
			<del>
				<DabloonAmount amount={item.originalPriceDabloons} tone="inherit" />
			</del>
			<strong>
				<DabloonAmount amount={item.discountedPriceDabloons} />
			</strong>
		</p>
	) : (
		<p className="shopPrice">
			<strong>
				<DabloonAmount amount={item.priceDabloons} />
			</strong>
		</p>
	);
}
