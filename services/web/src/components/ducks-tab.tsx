'use client';

import { useEffect, useRef, useState } from 'react';
import { DabloonAmount } from '@/components/dabloon-amount';
import { ShopPreview } from './shop/shop-item-preview';

interface Duck {
	id: number;
	itemId: string;
	name: string;
	condition: string;
	priceDabloons: number;
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
			setNotice(
				`Another copy of ${duck.name} is yours. Find it in your Minecraft inventory.`,
			);
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
					<h3 id="ducks-title">Ducks</h3>
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
			<p className="ducksFootnote">
				Ducks are a small gesture for very dedicated players and people who were helpful.
				You are not entitled to any ducks.
			</p>
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
			aria-label={`${duck.unlocked ? duck.name : 'Unknown duck'}. ${duck.condition}`}
			onPointerEnter={() => {
				setHovered(true);
			}}
			onPointerLeave={() => {
				setHovered(false);
			}}
		>
			<div
				className="duckStage"
				title={duck.modelUrl ? 'Drag to rotate this duck' : undefined}
			>
				<div className="duckModel">
					{duck.modelUrl && duck.textureUrl ? (
						<ShopPreview item={model} hovered={hovered} interactive view="duck" />
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
							? `Buy another copy of ${duck.name} for ${duck.priceDabloons} Dabloons`
							: 'Earn this duck first'
					}
				>
					{buying ? (
						'Buying...'
					) : (
						<>
							Buy · <DabloonAmount amount={duck.priceDabloons} tone="inherit" />
						</>
					)}
				</button>
				<span className="duckCardTitle">
					<span className="duckNumber">
						{duck.id === 13 ? 'Final Duck' : `#${duck.id}`} -{' '}
					</span>
					<span className="duckName">
						{duck.unlocked ? (
							duck.name
						) : (
							<DuckObfuscatedName
								wordLengths={duck.name.split(' ').map((word) => word.length)}
								label="Duck name hidden"
							/>
						)}
					</span>
				</span>
			</div>
			<div className="duckCondition" role="tooltip">
				<small>How to earn it</small>
				<p>{duck.condition}</p>
			</div>
		</article>
	);
}

function DuckObfuscatedName({ wordLengths, label }: { wordLengths: number[]; label: string }) {
	const root = useRef<HTMLSpanElement>(null);
	useEffect(() => {
		const glyphs = root.current?.querySelectorAll<HTMLElement>('.duckScrambleGlyph');
		if (!glyphs) return;
		const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
		const shuffle = () => {
			glyphs.forEach((glyph) => {
				glyph.textContent = alphabet[Math.floor(Math.random() * alphabet.length)] ?? '?';
			});
		};
		shuffle();
		const timer = window.setInterval(shuffle, 50);
		return () => {
			window.clearInterval(timer);
		};
	}, [wordLengths]);
	return (
		<span ref={root} role="img" aria-label={label} className="duckScramble">
			{wordLengths.map((length, word) => (
				<span className="duckScrambleWord" key={word}>
					{Array.from({ length }, (_, index) => (
						<span className="duckScrambleGlyph" key={index} aria-hidden="true">
							?
						</span>
					))}
				</span>
			))}
		</span>
	);
}
