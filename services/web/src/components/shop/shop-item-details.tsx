'use client';

import { type CSSProperties, useEffect } from 'react';
import { createPortal } from 'react-dom';
import type { CosmeticPreviewView } from '@/lib/site-settings';
import { ShopMetaIcons, ShopPreview } from './shop-item-preview';
import {
	effectivePrice,
	formatDabloons,
	formatIngredient,
	formatOption,
	isSoldOut,
	type ShopItem,
} from './shop-catalog.types';

export function FilterRow({
	label,
	options,
	selected,
	onSelect,
}: {
	label: string;
	options: { value: string; label: string }[];
	selected: string;
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
						className={`${selected === option.value ? 'active' : ''} filter-${option.value}`}
						aria-pressed={selected === option.value}
						onClick={() => {
							onSelect(option.value);
						}}
					>
						{option.value === 'animated' ? <AnimatedLabel /> : option.label}
					</button>
				))}
			</div>
		</div>
	);
}

export function ShopCard({
	item,
	hovered,
	hidePreview,
	allow3d,
	onHover,
	onOpen,
}: {
	item: ShopItem;
	hovered: boolean;
	hidePreview: boolean;
	allow3d: boolean;
	onHover: (id: string | null) => void;
	onOpen: (item: ShopItem) => void;
}) {
	return (
		<article
			className={`shopCard shopCard-${item.type} rarity-${item.rarity} ${!item.available ? 'unavailable' : ''}`}
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
				<ShopMetaIcons item={item} />
				{item.isDailyDeal && (
					<span className="shopDealBadge">−{item.discountPercent}% today</span>
				)}
			</div>
			<div className="shopCardBody">
				<ItemBadges item={item} />
				<h4>{item.title}</h4>
				<Price item={item} />
			</div>
			<button
				type="button"
				className="shopCardFoot"
				disabled={isSoldOut(item)}
				onClick={(event) => {
					event.stopPropagation();
					onOpen(item);
				}}
			>
				{isSoldOut(item) ? 'Sold out' : 'Buy now'}
			</button>
		</article>
	);
}

export function ShopDetails({
	item,
	buying,
	hidePreview,
	previewView,
	skinUrl,
	onSelectPreviewView,
	onClose,
	onBuy,
}: {
	item: ShopItem;
	buying: boolean;
	hidePreview: boolean;
	previewView: CosmeticPreviewView;
	skinUrl: string | null;
	onSelectPreviewView: (view: CosmeticPreviewView) => void;
	onClose: () => void;
	onBuy: (item: ShopItem) => Promise<void>;
}) {
	const effectivePreviewView = previewView === 'player' && !skinUrl ? 'cosmetic' : previewView;

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
						<div className="shopDetailsPreviewEmbed">
							<ShopPreview
								key={effectivePreviewView}
								item={item}
								hovered={false}
								interactive
								hidden={hidePreview}
								view={effectivePreviewView}
								skinUrl={skinUrl}
							/>
							{item.renderMode === 'model' && !hidePreview && (
								<span>Hover to pause · drag to rotate</span>
							)}
						</div>
						{item.type === 'cosmetic' &&
							item.renderMode === 'model' &&
							!hidePreview && (
								<CosmeticViewControl
									selected={effectivePreviewView}
									skinAvailable={Boolean(skinUrl)}
									onSelect={onSelectPreviewView}
								/>
							)}
					</div>
					<div className="shopDetailsSummary">
						<ItemBadges item={item} />
						<h2 id="shop-detail-title">{item.title}</h2>
						{item.description && (
							<div className="shopItemEffect">
								<strong>{item.type === 'charm' ? 'Effect' : 'Description'}</strong>
								<p>{item.description}</p>
							</div>
						)}
						{item.tooltips.length > 0 && (
							<div className="shopItemTooltips">
								{item.tooltips.map((tooltip) => (
									<p key={tooltip}>{tooltip}</p>
								))}
							</div>
						)}
						<Price item={item} />
						<button
							type="button"
							className="shopDetailsBuy"
							disabled={!item.available || buying}
							onClick={() => void onBuy(item)}
						>
							{item.available
								? buying
									? 'Buying…'
									: `Buy for ${formatDabloons(effectivePrice(item))} dabloons`
								: 'Sold out'}
						</button>
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
	{ value: 'cosmetic', label: 'Cosmetic', icon: '◇' },
	{ value: 'player', label: 'Player', icon: '👤' },
	{ value: 'item-frame', label: 'Item frame', icon: '▣' },
];

function CosmeticViewControl({
	selected,
	skinAvailable,
	onSelect,
}: {
	selected: CosmeticPreviewView;
	skinAvailable: boolean;
	onSelect: (view: CosmeticPreviewView) => void;
}) {
	return (
		<div className="shopCosmeticViewControl" role="group" aria-label="Cosmetic preview view">
			{COSMETIC_VIEW_OPTIONS.map((option) => (
				<button
					type="button"
					key={option.value}
					aria-pressed={selected === option.value}
					disabled={option.value === 'player' && !skinAvailable}
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
									<IngredientList ingredients={level.upgradeIngredients} />
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</section>
	);
}

function ItemBadges({ item }: { item: ShopItem }) {
	const tags = [
		item.dyeable ? (
			<span key="dyeable" className="shopTag dyeable">
				Dyeable
			</span>
		) : null,
		item.animated ? (
			<span key="animated" className="shopTag animated">
				<AnimatedLabel />
			</span>
		) : null,
		item.isDailyDeal ? (
			<span key="discounted" className="shopTag discounted">
				Discounted −{item.discountPercent}%
			</span>
		) : null,
		isSoldOut(item) ? (
			<span key="sold-out" className="shopTag soldOut">
				Sold out
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
		</div>
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
			<del>{formatDabloons(item.originalPriceDabloons)}</del>
			<strong>{formatDabloons(item.discountedPriceDabloons)}</strong> dabloons
		</p>
	) : (
		<p className="shopPrice">
			<strong>{formatDabloons(item.priceDabloons)}</strong> dabloons
		</p>
	);
}
