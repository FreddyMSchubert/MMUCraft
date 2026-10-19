'use client';

import { useEffect, useState } from 'react';
import { ShopPreview } from './shop/shop-item-preview';
import { ObfuscatedEventName } from './surprising-saturday-tab';

interface Duck {
	id: number;
	itemId: string;
	name: string;
	condition: string;
	unlocked: boolean;
	awardedAtUnixMs: number | null;
	modelUrl: string | null;
	textureUrl: string | null;
}

interface DucksResponse {
	count: number;
	total: number;
	rank: string;
	ducks: Duck[];
}

export function DucksTab() {
	const [data, setData] = useState<DucksResponse | null>(null);
	const [error, setError] = useState('');
	const [buying, setBuying] = useState<number | null>(null);
	const [notice, setNotice] = useState('');

	useEffect(() => {
		const controller = new AbortController();
		void fetch('/api/ducks', { cache: 'no-store', signal: controller.signal })
			.then(async (response) => {
				if (!response.ok) throw new Error('Could not load your ducks.');
				setData((await response.json()) as DucksResponse);
			})
			.catch((caught: unknown) => {
				if (!controller.signal.aborted)
					setError(
						caught instanceof Error ? caught.message : 'Could not load your ducks.',
					);
			});
		return () => {
			controller.abort();
		};
	}, []);

	async function buy(duck: Duck) {
		setBuying(duck.id);
		setNotice('');
		try {
			const response = await fetch(`/api/ducks/${duck.id}/purchase`, { method: 'POST' });
			const body = (await response.json().catch(() => null)) as { message?: string } | null;
			if (!response.ok) throw new Error(body?.message ?? 'Could not buy another duck.');
			setNotice(`Another ${duck.name} is yours. Find it in your Minecraft inventory.`);
		} catch (caught) {
			setNotice(caught instanceof Error ? caught.message : 'Could not buy another duck.');
		} finally {
			setBuying(null);
		}
	}

	if (error)
		return (
			<div className="ducksPanel">
				<p role="alert">{error}</p>
			</div>
		);
	if (!data)
		return (
			<div className="ducksPanel">
				<p>Gathering ducks...</p>
			</div>
		);

	return (
		<section className="ducksPanel" aria-labelledby="ducks-title">
			<header className="ducksHeader">
				<div>
					<p className="ducksEyebrow">The grand collection</p>
					<h3 id="ducks-title">Ducks</h3>
					<p className="tabSubtitle">
						Thirteen ducks. Thirteen stories. One very crowded pond.
					</p>
				</div>
				<div
					className="ducksProgress"
					aria-label={`${data.count} of ${data.total} ducks collected`}
				>
					<strong>
						<span>{data.count}</span>
						<small> / {data.total}</small>
					</strong>
					<span className="ducksRank">{data.rank}</span>
				</div>
			</header>
			<div
				className="ducksProgressTrack"
				role="progressbar"
				aria-valuenow={data.count}
				aria-valuemin={0}
				aria-valuemax={data.total}
				aria-label="Duck collection progress"
			>
				<span style={{ width: `${(data.count / data.total) * 100}%` }} />
			</div>
			{notice && (
				<p className="ducksNotice" role="status">
					{notice}
				</p>
			)}
			<div className="ducksGrid">
				{data.ducks.map((duck) => (
					<DuckCard
						key={duck.id}
						duck={duck}
						buying={buying === duck.id}
						onBuy={() => void buy(duck)}
					/>
				))}
			</div>
		</section>
	);
}

function DuckCard({ duck, buying, onBuy }: { duck: Duck; buying: boolean; onBuy: () => void }) {
	const [hovered, setHovered] = useState(false);
	const model = {
		id: duck.itemId,
		type: 'generic' as const,
		renderMode: 'model' as const,
		modelUrl: duck.modelUrl,
		textureUrl: duck.textureUrl,
		iconUrl: null,
		animation: null,
		animated: false,
		emissive: false,
		particleEmission: null,
		dyeable: false,
	};
	return (
		<article
			className={`duckCard ${duck.unlocked ? 'duckCard-unlocked' : 'duckCard-locked'} ${duck.id === 13 ? 'duckCard-final' : ''}`}
			tabIndex={0}
			aria-label={`${duck.unlocked ? duck.name : 'Unknown duck'}. ${duck.condition}`}
			onPointerEnter={() => {
				setHovered(true);
			}}
			onPointerLeave={() => {
				setHovered(false);
			}}
		>
			<span className="duckNumber">{String(duck.id).padStart(2, '0')} / 13</span>
			<div className="duckStage">
				<div className="duckModel">
					{duck.modelUrl && duck.textureUrl ? (
						<ShopPreview item={model} hovered={hovered} interactive />
					) : (
						<div className="duckMissingModel" aria-hidden="true" />
					)}
				</div>
			</div>
			<div className="duckCardFooter">
				<button
					type="button"
					className="duckBuy"
					disabled={!duck.unlocked || buying}
					onClick={onBuy}
					title={
						duck.unlocked
							? `Buy another ${duck.name} for 5 dabloons`
							: 'Earn this duck first'
					}
				>
					{buying ? 'Buying...' : 'Buy · 5 ◈'}
				</button>
				<span className="duckName">
					{duck.unlocked ? (
						duck.name
					) : (
						<ObfuscatedEventName
							wordLengths={duck.name.split(' ').map((word) => word.length)}
							label="Duck name hidden"
						/>
					)}
				</span>
			</div>
			<div className="duckCondition" role="tooltip">
				<small>How to earn it</small>
				<p>{duck.condition}</p>
			</div>
		</article>
	);
}
