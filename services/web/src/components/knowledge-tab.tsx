'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

interface KnowledgeResponse {
	html: string
	lastUnlockedElementId: string | null
}

const POLL_INTERVAL_MS = 8000
const OBFUSCATION_INTERVAL_MS = 75
const OBFUSCATION_GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵž `{|}~ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤÷≈°∙·√²■'

export function KnowledgeTab() {
	const [data, setData] = useState<KnowledgeResponse | null>(null)
	const [error, setError] = useState('')
	const articleRef = useRef<HTMLElement | null>(null)

	const load = useCallback(async (options: { quiet?: boolean } = {}) => {
		if (!options.quiet) {
			setError('')
		}

		const response = await fetch('/api/knowledge', {
			cache: 'no-store',
		})

		const body = await response.json().catch(() => null)

		if (!response.ok) {
			throw new Error(body?.message ?? 'Failed to load knowledge')
		}

		setData(body as KnowledgeResponse)
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitial() {
			try {
				if (!cancelled) {
					await load()
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load knowledge')
				}
			}
		}

		void loadInitial()

		return () => {
			cancelled = true
		}
	}, [load])

	useEffect(() => {
		const interval = window.setInterval(() => {
			if (document.visibilityState !== 'visible') return

			void load({ quiet: true }).catch(() => undefined)
		}, POLL_INTERVAL_MS)

		function refreshWhenVisible() {
			if (document.visibilityState === 'visible') {
				void load({ quiet: true }).catch(() => undefined)
			}
		}

		document.addEventListener('visibilitychange', refreshWhenVisible)

		return () => {
			window.clearInterval(interval)
			document.removeEventListener('visibilitychange', refreshWhenVisible)
		}
	}, [load])

	useEffect(() => {
		const article = articleRef.current
		if (!article) return
		const root = article

		function updateObfuscatedText() {
			const elements = root.querySelectorAll<HTMLElement>('.minecraftObfuscated')

			for (const element of elements) {
				const length = Number(element.dataset.obfuscatedLength ?? element.textContent?.length ?? 0)
				element.textContent = randomGlyphs(length)
			}
		}

		updateObfuscatedText()
		const interval = window.setInterval(updateObfuscatedText, OBFUSCATION_INTERVAL_MS)

		return () => window.clearInterval(interval)
	}, [data?.html])

	function jumpToLastUnlock() {
		if (!data?.lastUnlockedElementId) return

		document
			.getElementById(data.lastUnlockedElementId)
			?.scrollIntoView({ behavior: 'smooth', block: 'start' })
	}

	if (error) {
		return <p className="authError">{error}</p>
	}

	if (!data) {
		return <p>Loading knowledge...</p>
	}

	return (
		<>
			<div className="knowledgeActions">
				<button
					type="button"
					disabled={!data.lastUnlockedElementId}
					onClick={jumpToLastUnlock}
				>
					Jump to last unlock
				</button>
			</div>

			<article
				ref={articleRef}
				className="knowledgePage"
				dangerouslySetInnerHTML={{ __html: data.html }}
			/>
		</>
	)
}

function randomGlyphs(length: number) {
	let result = ''

	for (let index = 0; index < length; index++) {
		result += OBFUSCATION_GLYPHS[Math.floor(Math.random() * OBFUSCATION_GLYPHS.length)]
	}

	return result
}
