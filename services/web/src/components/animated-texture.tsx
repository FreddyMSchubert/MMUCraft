'use client';

import { useEffect, useRef } from 'react';
import type { TextureAnimationOptions } from '@/lib/minecraft-model-renderer';

export function AnimatedTexture({
	url,
	animation,
	className,
	alt = '',
}: {
	url: string;
	animation: TextureAnimationOptions;
	className?: string;
	alt?: string;
}) {
	const canvasRef = useRef<HTMLCanvasElement | null>(null);

	useEffect(() => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		let timer: number | undefined;
		let cancelled = false;
		const image = new Image();
		image.onload = () => {
			if (cancelled) return;
			const frameSize = image.naturalWidth;
			const frameCount = Math.max(1, Math.floor(image.naturalHeight / frameSize));
			const frames = (
				animation.frames ?? Array.from({ length: frameCount }, (_, index) => index)
			).filter((frame) => frame < frameCount);
			const context = canvas.getContext('2d');
			if (!context || !frames.length) return;
			canvas.width = frameSize;
			canvas.height = frameSize;
			let frameIndex = 0;
			const draw = () => {
				context.clearRect(0, 0, frameSize, frameSize);
				context.drawImage(
					image,
					0,
					frames[frameIndex] * frameSize,
					frameSize,
					frameSize,
					0,
					0,
					frameSize,
					frameSize,
				);
			};
			draw();
			if (frames.length > 1)
				timer = window.setInterval(() => {
					frameIndex = (frameIndex + 1) % frames.length;
					draw();
				}, animation.frameDelayMs);
		};
		image.src = url;
		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, [animation.frameDelayMs, animation.frames, url]);

	return (
		<canvas
			ref={canvasRef}
			className={className}
			role={alt ? 'img' : undefined}
			aria-label={alt || undefined}
			aria-hidden={!alt}
		/>
	);
}
