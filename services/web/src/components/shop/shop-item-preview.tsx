'use client';

import NextImage from 'next/image';
import { useEffect, useRef, useState } from 'react';
import { ASSETS } from '@/lib/assets';
import {
	MinecraftModelRenderer,
	type MinecraftModel,
	type MinecraftModelPreviewState,
	type PreviewView,
} from '@/lib/minecraft-model-renderer';
import type { ShopItem } from './shop-catalog.types';

const modelCache = new Map<string, Promise<MinecraftModel>>();
const modelPreviewStateCache = new Map<string, MinecraftModelPreviewState>();
let staticRenderQueue = Promise.resolve();

function queueStaticRender(render: () => Promise<void>) {
	const queued = staticRenderQueue.then(render, render);
	staticRenderQueue = queued.catch(() => undefined);
	return queued;
}

export function ShopPreview({
	item,
	hovered,
	interactive = false,
	hidden = false,
	allow3d = true,
	view,
	skinUrl,
}: {
	item: ShopItem;
	hovered: boolean;
	interactive?: boolean;
	hidden?: boolean;
	allow3d?: boolean;
	view?: PreviewView;
	skinUrl?: string | null;
}) {
	if (hidden) return <div className="shopHiddenPreview" />;
	if (!allow3d && item.renderMode === 'model')
		return <div className="shopReduced3dPlaceholder" />;
	if (item.renderMode === 'model' && item.modelUrl && item.textureUrl)
		return (
			<ShopModelPreview
				item={item}
				hovered={hovered}
				interactive={interactive}
				view={
					interactive && item.type === 'cosmetic'
						? (view ?? 'cosmetic')
						: item.type === 'cosmetic'
							? 'cosmetic'
							: 'basic3d'
				}
				skinUrl={skinUrl}
			/>
		);
	if (item.iconUrl && item.animation)
		return <AnimatedTexturePreview url={item.iconUrl} animation={item.animation} />;
	if (item.iconUrl)
		return (
			<NextImage
				unoptimized
				src={item.iconUrl}
				alt=""
				className="shopItemIcon"
				width={76}
				height={76}
			/>
		);
	return <div className="shopItemEmpty" />;
}

function AnimatedTexturePreview({
	url,
	animation,
}: {
	url: string;
	animation: NonNullable<ShopItem['animation']>;
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

	return <canvas ref={canvasRef} className="shopItemIcon shopAnimatedTexture" />;
}

function ShopModelPreview({
	item,
	hovered,
	interactive,
	view,
	skinUrl,
}: {
	item: ShopItem;
	hovered: boolean;
	interactive: boolean;
	view: PreviewView;
	skinUrl?: string | null;
}) {
	const hostRef = useRef<HTMLDivElement | null>(null);
	const canvasRef = useRef<HTMLCanvasElement | null>(null);
	const rendererRef = useRef<MinecraftModelRenderer | null>(null);
	const [interactiveHover, setInteractiveHover] = useState(false);
	const [ready, setReady] = useState(false);
	const [liveReady, setLiveReady] = useState(false);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		const host = hostRef.current;
		const canvas = canvasRef.current;
		if (interactive || item.animated || !host || !canvas || !item.modelUrl || !item.textureUrl)
			return;
		const textureUrl = item.textureUrl;
		let renderer: MinecraftModelRenderer | null = null;
		setFailed(false);
		const modelPromise =
			modelCache.get(item.modelUrl) ??
			fetch(item.modelUrl).then((response) => {
				if (!response.ok) throw new Error('Model failed to load');
				return response.json() as Promise<MinecraftModel>;
			});
		modelCache.set(item.modelUrl, modelPromise);
		void queueStaticRender(async () => {
			const model = await modelPromise;
			if (!host.isConnected) return;
			renderer = new MinecraftModelRenderer(host, {
				assetRoot: ASSETS.minecraft.root,
				animateDye: false,
				autoRotate: false,
				animateTextures: false,
				dyeable: item.dyeable,
				frameDelayMs: item.animation?.frameDelayMs,
				frameSequence: item.animation?.frames ?? null,
				textureSource: textureUrl,
				view,
				skinSource: view === 'player' ? (skinUrl ?? undefined) : undefined,
			});
			await renderer.loadModel(model);
			const stateKey = `${item.id}:${view}`;
			const savedState = modelPreviewStateCache.get(stateKey);
			if (savedState) renderer.setPreviewState(savedState);
			if (isConnected(host) && renderer.copyFrameTo(canvas)) {
				modelPreviewStateCache.set(stateKey, renderer.getPreviewState());
				setReady(true);
			}
			renderer.destroy();
			renderer = null;
		}).catch(() => {
			if (host.isConnected) setFailed(true);
		});
		return () => {
			renderer?.destroy();
			renderer = null;
		};
	}, [
		interactive,
		item.animated,
		item.animation?.frameDelayMs,
		item.animation?.frames,
		item.dyeable,
		item.id,
		item.modelUrl,
		item.textureUrl,
		skinUrl,
		view,
	]);

	const shouldAutoRotate = interactive ? !interactiveHover : hovered;
	const shouldAnimateDye = interactive ? liveReady && !interactiveHover : hovered;
	const rendererActive = interactive || hovered || item.animated;
	const autoRotateRef = useRef(shouldAutoRotate);
	const animateDyeRef = useRef(shouldAnimateDye);
	useEffect(() => {
		autoRotateRef.current = shouldAutoRotate;
		rendererRef.current?.setAutoRotate(shouldAutoRotate);
	}, [shouldAutoRotate]);
	useEffect(() => {
		animateDyeRef.current = shouldAnimateDye;
		rendererRef.current?.setDyeAnimation(shouldAnimateDye);
	}, [shouldAnimateDye]);

	useEffect(() => {
		const host = hostRef.current;
		const canvas = canvasRef.current;
		if (!rendererActive || !host || !item.modelUrl || !item.textureUrl) {
			setLiveReady(false);
			return;
		}
		const textureUrl = item.textureUrl;
		let renderer: MinecraftModelRenderer | null = null;
		setLiveReady(false);
		setFailed(false);
		const modelPromise =
			modelCache.get(item.modelUrl) ??
			fetch(item.modelUrl).then((response) => {
				if (!response.ok) throw new Error('Model failed to load');
				return response.json() as Promise<MinecraftModel>;
			});
		modelCache.set(item.modelUrl, modelPromise);
		void modelPromise
			.then(async (model) => {
				if (!host.isConnected) return;
				renderer = new MinecraftModelRenderer(host, {
					assetRoot: ASSETS.minecraft.root,
					animateDye: animateDyeRef.current,
					autoRotate: autoRotateRef.current,
					dyeable: item.dyeable,
					enableDrag: interactive,
					frameDelayMs: item.animation?.frameDelayMs,
					frameSequence: item.animation?.frames ?? null,
					textureSource: textureUrl,
					view,
					skinSource: view === 'player' ? (skinUrl ?? undefined) : undefined,
				});
				rendererRef.current = renderer;
				await renderer.loadModel(model);
				const stateKey = `${item.id}:${view}`;
				const savedState = modelPreviewStateCache.get(stateKey);
				if (savedState) renderer.setPreviewState(savedState);
				if (isConnected(host)) {
					renderer.setAutoRotate(autoRotateRef.current);
					renderer.setDyeAnimation(animateDyeRef.current);
					setReady(true);
					setLiveReady(true);
				}
			})
			.catch(() => {
				if (host.isConnected) setFailed(true);
			});
		return () => {
			if (renderer)
				modelPreviewStateCache.set(`${item.id}:${view}`, renderer.getPreviewState());
			if (!interactive && !item.animated && canvas) renderer?.copyFrameTo(canvas);
			renderer?.destroy();
			if (rendererRef.current === renderer) rendererRef.current = null;
			renderer = null;
		};
	}, [
		interactive,
		item.animated,
		item.animation?.frameDelayMs,
		item.animation?.frames,
		item.dyeable,
		item.id,
		item.modelUrl,
		item.textureUrl,
		rendererActive,
		skinUrl,
		view,
	]);

	if (failed && item.iconUrl)
		return (
			<NextImage
				unoptimized
				src={item.iconUrl}
				alt=""
				className="shopItemIcon"
				width={76}
				height={76}
			/>
		);
	return (
		<div
			ref={hostRef}
			className={`shopModelHost view-${view} ${ready ? 'ready' : 'loading'} ${liveReady ? 'live' : ''} ${interactive ? 'interactive' : ''}`}
			onPointerEnter={() => {
				if (interactive) setInteractiveHover(true);
			}}
			onPointerLeave={() => {
				if (interactive) setInteractiveHover(false);
			}}
		>
			<canvas ref={canvasRef} className="shopModelSnapshot" />
		</div>
	);
}

function isConnected(element: Element) {
	return element.isConnected;
}

export function ShopMetaIcons({ item }: { item: ShopItem }) {
	if (!item.animated && !item.dyeable) return null;
	return (
		<div className="shopMetaIcons">
			{item.animated && (
				<span className="shopMetaIcon shopMetaIcon-animated" title="Animated texture" />
			)}
			{item.dyeable && <span className="shopMetaIcon shopMetaIcon-dyeable" title="Dyeable" />}
		</div>
	);
}
