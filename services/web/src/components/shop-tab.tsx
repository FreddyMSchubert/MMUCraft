'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import { useSiteSettings } from '@/lib/site-settings';
import { FilterRow, ShopCard, ShopDetails } from './shop/shop-item-details';
import { ShopPreview } from './shop/shop-item-preview';
import {
	compareTitles,
	effectivePrice,
	formatDabloons,
	formatOption,
	isSoldOut,
	ORDER_OPTIONS,
	RARITY_OPTIONS,
	rarityRank,
	seededRank,
	shouldHidePreview,
	type ShopItem,
	type ShopItemType,
	type ShopOrder,
	type ShopResponse,
	type ShopTagFilter,
	TAG_OPTIONS,
	TYPE_OPTIONS,
} from './shop/shop-catalog.types';

export function ShopTab({
	itemId,
	onSelectItem,
}: {
	itemId?: string;
	onSelectItem: (itemId: string | null, replace?: boolean) => void;
}) {
	const { settings } = useSiteSettings();
	const { confirm, showAlert } = useSiteAlert();
	const [data, setData] = useState<ShopResponse | null>(null);
	const [error, setError] = useState('');
	const [typeFilter, setTypeFilter] = useState<'all' | ShopItemType>('all');
	const [rarityFilter, setRarityFilter] = useState<(typeof RARITY_OPTIONS)[number]>('all');
	const [tagFilter, setTagFilter] = useState<ShopTagFilter>('all');
	const [order, setOrder] = useState<ShopOrder>('random');
	const [randomSeed, setRandomSeed] = useState(() => Math.random().toString(36));
	const [buyingItemId, setBuyingItemId] = useState<string | null>(null);
	const [hoveredItemId, setHoveredItemId] = useState<string | null>(null);
	const [featuredIndex, setFeaturedIndex] = useState(0);
	const [featuredHovered, setFeaturedHovered] = useState(false);

	const load = useCallback(async () => {
		const response = await fetch('/api/shop', { cache: 'no-store' });
		const body = await response.json().catch(() => null);
		if (!response.ok) throw new Error(apiMessage(body, 'Failed to load shop'));
		setData(body as ShopResponse);
	}, []);

	useEffect(() => {
		let cancelled = false;
		async function loadInitial() {
			try {
				await load();
			} catch (caught) {
				if (!cancelled)
					setError(caught instanceof Error ? caught.message : 'Failed to load shop');
			}
		}
		void loadInitial();
		return () => {
			cancelled = true;
		};
	}, [load]);

	const dailyDeals = useMemo(
		() => data?.items.filter((item) => item.isDailyDeal && item.available) ?? [],
		[data?.items],
	);
	const selectedItem = useMemo(
		() => (itemId ? (data?.items.find((item) => item.id === itemId) ?? null) : null),
		[data?.items, itemId],
	);

	useEffect(() => {
		if (data && itemId && !selectedItem) onSelectItem(null, true);
	}, [data, itemId, onSelectItem, selectedItem]);

	useEffect(() => {
		if (dailyDeals.length < 2 || featuredHovered) return;
		const timer = window.setInterval(() => {
			setFeaturedIndex((current) => (current + 1) % dailyDeals.length);
		}, 6500);
		return () => {
			window.clearInterval(timer);
		};
	}, [dailyDeals.length, featuredHovered]);

	const visibleItems = useMemo(() => {
		const filtered = (data?.items ?? []).filter((item) => {
			if (typeFilter !== 'all' && item.type !== typeFilter) return false;
			if (rarityFilter !== 'all' && item.rarity !== rarityFilter) return false;
			if (tagFilter === 'dyeable' && !item.dyeable) return false;
			if (tagFilter === 'animated' && !item.animated) return false;
			if (tagFilter === 'discounted' && !item.isDailyDeal) return false;
			if (tagFilter === 'sold-out' && !isSoldOut(item)) return false;
			return true;
		});

		return [...filtered].sort((left, right) => {
			if (order === 'price-desc')
				return effectivePrice(right) - effectivePrice(left) || compareTitles(left, right);
			if (order === 'price-asc')
				return effectivePrice(left) - effectivePrice(right) || compareTitles(left, right);
			if (order === 'alphabetical') return compareTitles(left, right);
			if (order === 'alphabetical-desc') return compareTitles(right, left);
			if (order === 'rarity')
				return (
					rarityRank(left) - rarityRank(right) ||
					effectivePrice(left) - effectivePrice(right) ||
					compareTitles(left, right)
				);
			if (order === 'rarity-desc')
				return (
					rarityRank(right) - rarityRank(left) ||
					effectivePrice(right) - effectivePrice(left) ||
					compareTitles(left, right)
				);
			return seededRank(`${randomSeed}:${left.id}`) - seededRank(`${randomSeed}:${right.id}`);
		});
	}, [data?.items, order, randomSeed, rarityFilter, tagFilter, typeFilter]);

	async function buy(item: ShopItem) {
		if (!item.available || buyingItemId) return;
		const price = effectivePrice(item);
		if (
			!(await confirm({
				title: `Buy ${item.title}?`,
				message: `${item.description}\n\nThis purchase costs ${formatDabloons(price)} dabloons. Stay online in Minecraft until it finishes so the item can be delivered.`,
				confirmLabel: `Buy for ${formatDabloons(price)}`,
			}))
		)
			return;
		setBuyingItemId(item.id);
		setError('');
		try {
			const response = await fetch('/api/shop/purchase', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ itemId: item.id }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Purchase failed.'));
			const text = apiMessage(body, `${item.title} purchased.`);
			onSelectItem(null, true);
			await load();
			await showAlert({
				title: `${item.title} purchased`,
				message: `${text} Check your Minecraft inventory for the item.`,
				tone: 'success',
			});
		} catch (caught) {
			const text = caught instanceof Error ? caught.message : 'Purchase failed';
			await showAlert({
				title: `Could not buy ${item.title}`,
				message: text,
				tone: 'danger',
			});
		} finally {
			setBuyingItemId(null);
		}
	}

	if (error && !data) return <p className="authError">{error}</p>;
	if (!data) return <p>Loading shop...</p>;

	const safeFeaturedIndex = dailyDeals.length ? featuredIndex % dailyDeals.length : 0;
	const featured = dailyDeals.at(safeFeaturedIndex);
	return (
		<div className="shopPanel">
			<div className="shopTop">
				<div>
					<h3>Shop</h3>
					<p className="tabSubtitle">
						The dabloon exchange — the best items for the best prices.
					</p>
				</div>
			</div>

			{featured && (
				<section
					className="shopDeals"
					aria-label="Today's deals"
					onPointerEnter={() => {
						setFeaturedHovered(true);
					}}
					onPointerLeave={() => {
						setFeaturedHovered(false);
					}}
				>
					{dailyDeals.length > 1 && (
						<button
							type="button"
							className="shopDealArrow previous"
							aria-label="Previous daily deal"
							onClick={() => {
								setFeaturedIndex(
									(current) =>
										(current - 1 + dailyDeals.length) % dailyDeals.length,
								);
							}}
						>
							‹
						</button>
					)}
					<div className="shopDealCopy">
						<div className="shopDealHeading">
							<span className="shopDealSpark">✦</span>
							<div>
								<p>{featured.dealMessage ?? 'Today’s find'}</p>
								<h4>{featured.title}</h4>
							</div>
							<strong>−{featured.discountPercent}%</strong>
						</div>
						<p>{featured.description}</p>
						<div className="shopDealActions">
							<span>
								<del>{formatDabloons(featured.originalPriceDabloons)}</del>{' '}
								{formatDabloons(featured.discountedPriceDabloons)} dabloons
							</span>
							<button
								type="button"
								onClick={() => {
									onSelectItem(featured.id);
								}}
							>
								See details
							</button>
						</div>
					</div>
					<div className="shopDealPreview" aria-hidden="true">
						<ShopPreview
							item={featured}
							hovered={featuredHovered}
							hidden={shouldHidePreview(featured, settings.arachnophobiaMode)}
							allow3d={!settings.reduce3dRendering}
						/>
					</div>
					{data.shoppingSunday && (
						<span className="shoppingSundayBadge">
							Shopping Sunday · tons of huge discounts
						</span>
					)}
					{dailyDeals.length > 1 && (
						<button
							type="button"
							className="shopDealArrow next"
							aria-label="Next daily deal"
							onClick={() => {
								setFeaturedIndex((current) => (current + 1) % dailyDeals.length);
							}}
						>
							›
						</button>
					)}
					{dailyDeals.length > 1 && (
						<div className="shopDealDots" aria-label="Choose daily deal">
							{dailyDeals.map((item, index) => (
								<button
									key={item.id}
									type="button"
									className={index === safeFeaturedIndex ? 'active' : ''}
									aria-label={`Show ${item.title}`}
									onClick={() => {
										setFeaturedIndex(index);
									}}
								/>
							))}
						</div>
					)}
				</section>
			)}

			<div className="shopCatalogToolbar">
				<span>
					{visibleItems.length} {visibleItems.length === 1 ? 'item' : 'items'}
				</span>
				<label className="shopOrderControl">
					<span>Order</span>
					<select
						value={order}
						onChange={(event) => {
							const nextOrder = event.target.value as ShopOrder;
							setOrder(nextOrder);
							if (nextOrder === 'random') setRandomSeed(Math.random().toString(36));
						}}
					>
						{ORDER_OPTIONS.map((option) => (
							<option key={option.value} value={option.value}>
								{option.label}
							</option>
						))}
					</select>
				</label>
			</div>

			<div className="shopFilterStack" aria-label="Shop filters">
				<FilterRow
					label="Type"
					options={TYPE_OPTIONS}
					selected={typeFilter}
					onSelect={(value) => {
						setTypeFilter(value as 'all' | ShopItemType);
					}}
				/>
				<FilterRow
					label="Rarity"
					options={RARITY_OPTIONS.map((value) => ({ value, label: formatOption(value) }))}
					selected={rarityFilter}
					onSelect={(value) => {
						setRarityFilter(value as (typeof RARITY_OPTIONS)[number]);
					}}
				/>
				<FilterRow
					label="Tags"
					options={TAG_OPTIONS}
					selected={tagFilter}
					onSelect={(value) => {
						setTagFilter(value as ShopTagFilter);
					}}
				/>
			</div>

			<div className="shopGrid">
				{visibleItems.map((item) => (
					<ShopCard
						key={item.id}
						item={item}
						hovered={hoveredItemId === item.id}
						hidePreview={shouldHidePreview(item, settings.arachnophobiaMode)}
						allow3d={!settings.reduce3dRendering}
						onHover={setHoveredItemId}
						onOpen={(selected) => {
							onSelectItem(selected.id);
						}}
					/>
				))}
			</div>
			{visibleItems.length === 0 && (
				<p className="shopEmptyState">No items match those filters.</p>
			)}

			{selectedItem && (
				<ShopDetails
					item={selectedItem}
					buying={buyingItemId === selectedItem.id}
					hidePreview={shouldHidePreview(selectedItem, settings.arachnophobiaMode)}
					onClose={() => {
						onSelectItem(null, true);
					}}
					onBuy={buy}
				/>
			)}
		</div>
	);
}
