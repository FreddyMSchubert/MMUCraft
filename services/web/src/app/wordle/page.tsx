import type { Metadata } from 'next'
import { WordleGame } from '@/components/wordle/wordle-game'
import { getSiteVisuals } from '@/lib/site-assets'
import { getUKDateKey } from '@/lib/wordle'

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
	title: 'Wordle | MMU Minecraft Society',
	description: 'The daily MMU Minecraft Society Wordle.',
}

export default function WordlePage() {
	// The UK calendar date is intentionally evaluated per request so the daily puzzle changes at UK midnight.
	return <WordleGame {...getSiteVisuals()} dateKey={getUKDateKey(new Date())} />
}
