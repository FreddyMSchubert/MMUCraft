'use client'

import { useEffect, useRef, useState } from 'react'
import { ASSETS } from '@/lib/assets'
import { MinecraftModelRenderer } from '@/lib/minecraft-model-renderer'

interface MinecraftItemSnapshotProps {
	className: string
	itemId: string
	modelUrl?: string | null
	textureUrl?: string | null
}

interface ItemRaster {
	frames: ImageBitmap[]
	frameDelayMs: number
}

const FRAME_DELAY_MS = 50
const MAX_ANIMATION_FRAMES = 100
const RENDER_SIZE = 64
const rasterCache = new Map<string, Promise<ItemRaster>>()
let renderQueue = Promise.resolve()

export function MinecraftItemSnapshot({ className, itemId, modelUrl, textureUrl }: MinecraftItemSnapshotProps) {
	const canvasRef = useRef<HTMLCanvasElement>(null)
	const [failed, setFailed] = useState(false)

	useEffect(() => {
		let cancelled = false
		let interval: number | undefined
		void getRaster({ itemId, modelUrl, textureUrl }).then((raster) => {
			if (cancelled || !canvasRef.current) return
			const canvas = canvasRef.current
			const context = canvas.getContext('2d')
			if (!context) throw new Error('Could not create item canvas.')
			canvas.width = raster.frames[0]!.width
			canvas.height = raster.frames[0]!.height
			context.imageSmoothingEnabled = false
			let frame = 0
			const draw = () => {
				context.clearRect(0, 0, canvas.width, canvas.height)
				context.drawImage(raster.frames[frame]!, 0, 0)
				frame = (frame + 1) % raster.frames.length
			}
			draw()
			if (raster.frames.length > 1) interval = window.setInterval(draw, raster.frameDelayMs)
		}).catch(() => { if (!cancelled) setFailed(true) })

		return () => {
			cancelled = true
			window.clearInterval(interval)
		}
	}, [itemId, modelUrl, textureUrl])

	if (failed) return <span aria-hidden="true" className={`${className} minecraftItemQuestionMark`}>?</span>
	return <canvas ref={canvasRef} aria-hidden="true" className={className} />
}

function getRaster(source: Omit<MinecraftItemSnapshotProps, 'className'>) {
	const key = JSON.stringify(source)
	const cached = rasterCache.get(key)
	if (cached) return cached

	const raster = renderQueue.then(() => renderRaster(source))
	renderQueue = raster.then(() => undefined, () => undefined)
	rasterCache.set(key, raster)
	void raster.catch(() => rasterCache.delete(key))
	return raster
}

async function renderRaster({ itemId, modelUrl, textureUrl }: Omit<MinecraftItemSnapshotProps, 'className'>): Promise<ItemRaster> {
	const host = document.createElement('div')
	host.style.cssText = `position:fixed;left:-10000px;top:0;width:${RENDER_SIZE}px;height:${RENDER_SIZE}px;pointer-events:none`
	document.body.appendChild(host)
	const isVanilla = itemId.startsWith('minecraft:')
	let renderer: MinecraftModelRenderer | undefined

	try {
		renderer = new MinecraftModelRenderer(host, {
			animateTextures: false,
			assetRoot: ASSETS.minecraft.root,
			canvasClassName: 'minecraftItemSnapshotSource',
			frameDelayMs: FRAME_DELAY_MS,
			preserveDrawingBuffer: true,
			view: 'icon',
		})
		await renderer.loadItem({
			assetRoot: ASSETS.minecraft.root,
			itemId,
			modelUrl: isVanilla ? null : modelUrl,
			textureUrl: isVanilla ? null : textureUrl,
		})
		if (renderer.hasFailedTextures()) throw new Error('Minecraft item texture failed to load.')

		const frames: ImageBitmap[] = []
		renderer.renderFrame()
		const firstKey = renderer.animationKey()
		frames.push(await renderer.captureFrame())
		if (renderer.isAnimated()) {
			for (let index = 1; index < MAX_ANIMATION_FRAMES; index += 1) {
				renderer.renderFrame(FRAME_DELAY_MS)
				if (renderer.animationKey() === firstKey) break
				frames.push(await renderer.captureFrame())
			}
		}
		return { frames, frameDelayMs: FRAME_DELAY_MS }
	} finally {
		renderer?.destroy()
		host.remove()
	}
}
