'use client';

import { useEffect, useRef, useState } from 'react';
import { ASSETS } from '@/lib/assets';
import { MinecraftModelRenderer } from '@/lib/minecraft-model-renderer';

interface MinecraftItemIconProps {
	className: string;
	itemId: string;
	modelUrl?: string | null;
	textureUrl?: string | null;
}

export function MinecraftItemIcon({
	className,
	itemId,
	modelUrl,
	textureUrl,
}: MinecraftItemIconProps) {
	const hostRef = useRef<HTMLDivElement>(null);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		const host = hostRef.current;
		if (!host) return;
		let cancelled = false;
		setFailed(false);
		const isVanilla = itemId.startsWith('minecraft:');
		const renderer = new MinecraftModelRenderer(host, {
			assetRoot: ASSETS.minecraft.root,
			canvasClassName: 'minecraftItemIconCanvas',
		});
		void renderer
			.loadItem({
				assetRoot: ASSETS.minecraft.root,
				itemId,
				modelUrl: isVanilla ? null : modelUrl,
				textureUrl: isVanilla ? null : textureUrl,
			})
			.catch(() => {
				if (!cancelled) setFailed(true);
			});
		return () => {
			cancelled = true;
			renderer.destroy();
		};
	}, [itemId, modelUrl, textureUrl]);

	return (
		<div
			ref={hostRef}
			aria-hidden="true"
			className={`${className}${failed ? ' minecraftItemIconFailed' : ''}`}
		/>
	);
}
