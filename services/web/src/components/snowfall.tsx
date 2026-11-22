'use client';

import { useState } from 'react';
import { ASSETS } from '@/lib/assets';

const WEATHER_FLAKES = [
	[36, 14],
	[55, 15],
	[29, 44],
	[23, 19],
] as const;

function Snowflake({ index }: { index: number }) {
	const [texture, setTexture] = useState(index % WEATHER_FLAKES.length);
	const [x, y] = WEATHER_FLAKES[texture] ?? WEATHER_FLAKES[0];
	const size = 12 + (index % 4) * 3;
	const scale = size / 3;
	const randomize = () => {
		setTexture(Math.floor(Math.random() * WEATHER_FLAKES.length));
	};
	return (
		<span
			className="snowflake"
			style={{
				left: `${(index * 37) % 100}%`,
				backgroundImage: `url("${ASSETS.minecraft.vanilla}/textures/environment/snow.png")`,
				backgroundSize: `${64 * scale}px ${256 * scale}px`,
				backgroundPosition: `${-x * scale}px ${-y * scale}px`,
				animationDelay: `${-(index * 0.73)}s`,
				animationDuration: `${6 + (index % 5)}s`,
				width: `${size}px`,
				height: `${size}px`,
			}}
			onAnimationStart={randomize}
			onAnimationIteration={randomize}
		/>
	);
}

export function Snowfall({ className = '', count = 160 }: { className?: string; count?: number }) {
	return (
		<div className={`snowfall ${className}`.trim()} aria-hidden="true">
			{Array.from({ length: count }, (_, index) => (
				<Snowflake key={index} index={index} />
			))}
		</div>
	);
}
