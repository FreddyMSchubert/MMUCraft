'use client';

import NextImage from 'next/image';
import { useEffect, useRef, useState } from 'react';
import { AnimatedTexture } from '@/components/animated-texture';
import { ASSETS } from '@/lib/assets';
import { loadAssetJson } from '@/lib/asset-fetch-cache';
import {
	MinecraftModelRenderer,
	type MinecraftModel,
	type MinecraftModelPreviewState,
	type PreviewView,
} from '@/lib/minecraft-model-renderer';
import type { ShopItem } from './shop-catalog.types';

type PreviewItem = Pick<
	ShopItem,
	| 'id'
	| 'type'
	| 'renderMode'
	| 'modelUrl'
	| 'textureUrl'
	| 'iconUrl'
	| 'animation'
	| 'animated'
	| 'emissive'
	| 'particleEmission'
	| 'dyeable'
>;

const modelCache = new Map<string, Promise<MinecraftModel>>();
const snapshotCache = new Map<string, HTMLCanvasElement>();
const MAX_SNAPSHOT_CACHE_SIZE = 48;
let staticRenderQueue = Promise.resolve();

function loadModel(url: string) {
	const cached = modelCache.get(url);
	if (cached) return cached;
	const loading = loadAssetJson<MinecraftModel>(url).catch((error: unknown) => {
		modelCache.delete(url);
		throw error;
	});
	modelCache.set(url, loading);
	return loading;
}

function snapshotKey(
	modelUrl: string,
	textureUrl: string,
	view: PreviewView,
	skinUrl?: string | null,
) {
	return `${modelUrl}:${textureUrl}:${view}:${skinUrl ?? ''}`;
}

function restoreSnapshot(target: HTMLCanvasElement, key: string) {
	const cached = snapshotCache.get(key);
	if (!cached) return false;
	target.width = cached.width;
	target.height = cached.height;
	target.getContext('2d')?.drawImage(cached, 0, 0);
	snapshotCache.delete(key);
	snapshotCache.set(key, cached);
	return true;
}

function cacheSnapshot(source: HTMLCanvasElement, key: string) {
	const snapshot = document.createElement('canvas');
	snapshot.width = source.width;
	snapshot.height = source.height;
	snapshot.getContext('2d')?.drawImage(source, 0, 0);
	snapshotCache.set(key, snapshot);
	while (snapshotCache.size > MAX_SNAPSHOT_CACHE_SIZE) {
		const oldest = snapshotCache.keys().next().value;
		if (oldest === undefined) break;
		snapshotCache.delete(oldest);
	}
}

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
	nightMode = false,
	onEmissionSupport,
}: {
	item: PreviewItem;
	hovered: boolean;
	interactive?: boolean;
	hidden?: boolean;
	allow3d?: boolean;
	view?: PreviewView;
	skinUrl?: string | null;
	nightMode?: boolean;
	onEmissionSupport?: (modelUrl: string, supported: boolean) => void;
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
					interactive
						? (view ?? (item.type === 'cosmetic' ? 'cosmetic' : 'basic3d'))
						: item.type === 'cosmetic'
							? 'cosmetic'
							: 'basic3d'
				}
				skinUrl={skinUrl}
				nightMode={nightMode}
				onEmissionSupport={onEmissionSupport}
			/>
		);
	if (item.renderMode === 'model') return <div className="shopReduced3dPlaceholder" />;
	if (item.iconUrl && item.animation)
		return (
			<AnimatedTexture
				url={item.iconUrl}
				animation={item.animation}
				className="shopItemIcon shopAnimatedTexture"
			/>
		);
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

function ShopModelPreview({
	item,
	hovered,
	interactive,
	view,
	skinUrl,
	nightMode = false,
	onEmissionSupport,
}: {
	item: PreviewItem;
	hovered: boolean;
	interactive: boolean;
	view: PreviewView;
	skinUrl?: string | null;
	nightMode?: boolean;
	onEmissionSupport?: (modelUrl: string, supported: boolean) => void;
}) {
	const hostRef = useRef<HTMLDivElement | null>(null);
	const canvasRef = useRef<HTMLCanvasElement | null>(null);
	const rendererRef = useRef<MinecraftModelRenderer | null>(null);
	const previewStateRef = useRef<MinecraftModelPreviewState | null>(null);
	const [interactiveHover, setInteractiveHover] = useState(false);
	const [ready, setReady] = useState(false);
	const [liveReady, setLiveReady] = useState(false);
	const [failed, setFailed] = useState(false);
	const [retryCount, setRetryCount] = useState(0);
	const nightModeRef = useRef(nightMode);
	useEffect(() => {
		if (!failed) return;
		const timer = window.setTimeout(
			() => {
				setFailed(false);
				setRetryCount((count) => count + 1);
			},
			2_000 * 2 ** Math.min(retryCount, 4),
		);
		return () => {
			window.clearTimeout(timer);
		};
	}, [failed, retryCount]);
	useEffect(() => {
		nightModeRef.current = nightMode;
		rendererRef.current?.setNightMode(nightMode);
	}, [nightMode]);

	useEffect(() => {
		const host = hostRef.current;
		const canvas = canvasRef.current;
		if (
			failed ||
			interactive ||
			item.animated ||
			item.emissive ||
			!host ||
			!canvas ||
			!item.modelUrl ||
			!item.textureUrl
		)
			return;
		const textureUrl = item.textureUrl;
		const cacheKey = snapshotKey(item.modelUrl, item.textureUrl, view, skinUrl);
		if (!item.dyeable && restoreSnapshot(canvas, cacheKey)) {
			setReady(true);
			return;
		}
		let renderer: MinecraftModelRenderer | null = null;
		const abortController = new AbortController();
		const modelPromise = loadModel(item.modelUrl);
		void queueStaticRender(async () => {
			const model = await modelPromise;
			if (isAborted(abortController.signal) || !host.isConnected) return;
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
			if (renderer.hasFailedTextures()) throw new Error('Model texture failed to load');
			if (isAborted(abortController.signal)) return;
			const savedState = previewStateRef.current;
			if (savedState) renderer.setPreviewState(savedState);
			if (isConnected(host) && renderer.copyFrameTo(canvas)) {
				previewStateRef.current = renderer.getPreviewState();
				if (!item.dyeable) cacheSnapshot(canvas, cacheKey);
				setReady(true);
			} else if (isConnected(host)) throw new Error('Model frame failed to render');
			renderer.destroy();
			renderer = null;
		}).catch(() => {
			if (!isAborted(abortController.signal) && host.isConnected) {
				setReady(false);
				setFailed(true);
			}
			renderer?.destroy();
			renderer = null;
		});
		return () => {
			abortController.abort();
			renderer?.destroy();
			renderer = null;
		};
	}, [
		interactive,
		item.animated,
		item.emissive,
		item.animation?.frameDelayMs,
		item.animation?.frames,
		item.dyeable,
		item.id,
		item.modelUrl,
		item.textureUrl,
		failed,
		retryCount,
		skinUrl,
		view,
	]);

	const shouldAutoRotate = interactive ? !interactiveHover : hovered;
	const shouldAnimateDye = interactive ? liveReady && !interactiveHover : hovered;
	const rendererActive = interactive || hovered || item.animated || item.emissive;
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
		if (failed || !rendererActive || !host || !canvas || !item.modelUrl || !item.textureUrl) {
			setLiveReady(false);
			return;
		}
		const textureUrl = item.textureUrl;
		const modelUrl = item.modelUrl;
		let renderer: MinecraftModelRenderer | null = null;
		const abortController = new AbortController();
		setLiveReady(false);
		const modelPromise = loadModel(modelUrl);
		void modelPromise
			.then(async (model) => {
				if (isAborted(abortController.signal) || !host.isConnected) return;
				renderer = new MinecraftModelRenderer(host, {
					assetRoot: ASSETS.minecraft.root,
					animateDye: animateDyeRef.current,
					autoRotate: autoRotateRef.current,
					dyeable: item.dyeable,
					particleEmission: item.particleEmission,
					enableDrag: interactive,
					frameDelayMs: item.animation?.frameDelayMs,
					frameSequence: item.animation?.frames ?? null,
					textureSource: textureUrl,
					view,
					skinSource: view === 'player' ? (skinUrl ?? undefined) : undefined,
				});
				rendererRef.current = renderer;
				await renderer.loadModel(model);
				if (renderer.hasFailedTextures()) throw new Error('Model texture failed to load');
				if (!renderer.copyFrameTo(canvas)) throw new Error('Model frame failed to render');
				renderer.setNightMode(nightModeRef.current);
				if (isAborted(abortController.signal)) return;
				onEmissionSupport?.(modelUrl, renderer.hasEmissiveElements());
				const savedState = previewStateRef.current;
				if (savedState) renderer.setPreviewState(savedState);
				if (isConnected(host)) {
					renderer.setAutoRotate(autoRotateRef.current);
					renderer.setDyeAnimation(animateDyeRef.current);
					setReady(true);
					setLiveReady(true);
				}
			})
			.catch(() => {
				if (!isAborted(abortController.signal) && host.isConnected) {
					setReady(false);
					setLiveReady(false);
					setFailed(true);
				}
				renderer?.destroy();
				if (rendererRef.current === renderer) rendererRef.current = null;
				renderer = null;
			});
		return () => {
			abortController.abort();
			if (renderer) previewStateRef.current = renderer.getPreviewState();
			if (!interactive && !item.animated && !item.emissive) renderer?.copyFrameTo(canvas);
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
		item.emissive,
		item.particleEmission,
		item.id,
		item.modelUrl,
		item.textureUrl,
		failed,
		onEmissionSupport,
		rendererActive,
		retryCount,
		skinUrl,
		view,
	]);

	return (
		<div
			ref={hostRef}
			className={`shopModelHost view-${view} ${ready && !failed ? 'ready' : 'shopReduced3dPlaceholder'} ${liveReady && !failed ? 'live' : ''} ${interactive ? 'interactive' : ''}`}
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

function isAborted(signal: AbortSignal) {
	return signal.aborted;
}
