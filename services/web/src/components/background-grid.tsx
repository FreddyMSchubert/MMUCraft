'use client'

import { useEffect, useState } from 'react'
import { useSiteSettings } from '@/lib/site-settings'

const TARGET_TILE_SIZE = 96
const REDUCED_BACKGROUND_IMAGE = 'https://images3.alphacoders.com/118/1184187.jpg'

function pickRandom(items: string[]): string {
    return items[Math.floor(Math.random() * items.length)] ?? ''
}

export function BackgroundGrid({ images }: { images: string[] }) {
	const { settings } = useSiteSettings()
    const [cols, setCols] = useState(1)
    const [tiles, setTiles] = useState<string[]>([])

	useEffect(() => {
		if (settings.reduceBackgroundImageLoading) {
			return
		}
        const updateGrid = () => {
            const width = window.innerWidth
            const height = window.innerHeight

            const nextCols = Math.max(1, Math.ceil(width / TARGET_TILE_SIZE))
            const tileSize = width / nextCols
            const nextRows = Math.max(1, Math.ceil(height / tileSize))
            const nextCount = nextCols * nextRows

            setCols(nextCols)

            setTiles((current) => {
                if (images.length === 0) return []
                if (current.length === nextCount) return current
                return Array.from({ length: nextCount }, () => pickRandom(images))
            })
        }

        updateGrid()
        window.addEventListener('resize', updateGrid)
        return () => window.removeEventListener('resize', updateGrid)
    }, [images, settings.reduceBackgroundImageLoading])

    useEffect(() => {
		if (images.length === 0 || settings.reduceBackgroundImageLoading) return

        const id = window.setInterval(() => {
            setTiles((current) => {
                if (current.length === 0) return current

                const next = current.slice()
                const index = Math.floor(Math.random() * next.length)
                next[index] = pickRandom(images)
                return next
            })
        }, 100)

        return () => window.clearInterval(id)
    }, [images, settings.reduceBackgroundImageLoading])

	if (settings.reduceBackgroundImageLoading) {
		return <div className="bg bgSingle" style={{ backgroundImage: `url(${REDUCED_BACKGROUND_IMAGE})` }} aria-hidden="true" />
	}

    return (
        <div
            className="bg"
            style={{ gridTemplateColumns: `repeat(${cols}, 1fr)` }}
            aria-hidden="true"
        >
            {tiles.map((src, index) => (
                <img key={index} className="bgTile" src={src} alt="" />
            ))}
        </div>
    )
}
