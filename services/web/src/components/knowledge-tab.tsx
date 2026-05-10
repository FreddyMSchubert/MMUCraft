'use client'

import { useEffect, useState } from 'react'

type KnowledgeSection =
	| {
		type: 'public'
		html: string
	}
	| {
		type: 'knowledge'
		id: string
		priority: number
		topic: string
		unlocked: boolean
		html: string
	}

interface KnowledgeResponse {
	sections: KnowledgeSection[]
}

export function KnowledgeTab() {
	const [data, setData] = useState<KnowledgeResponse | null>(null)
	const [error, setError] = useState('')

	useEffect(() => {
		let cancelled = false

		async function load() {
			setError('')

			try {
				const response = await fetch('/api/knowledge', {
					cache: 'no-store',
				})

				const body = await response.json().catch(() => null)

				if (!response.ok) {
					throw new Error(body?.message ?? 'Failed to load knowledge')
				}

				if (!cancelled) {
					setData(body as KnowledgeResponse)
				}
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load knowledge')
				}
			}
		}

		void load()

		return () => {
			cancelled = true
		}
	}, [])

	if (error) {
		return <p className="authError">{error}</p>
	}

	if (!data) {
		return <p>Loading knowledge...</p>
	}

	return (
		<article className="knowledgePage">
			{data.sections.map((section, index) => {
				const locked = section.type === 'knowledge' && !section.unlocked

				return (
					<section
						key={section.type === 'knowledge' ? section.id : `public-${index}`}
						className={`knowledgeSection${locked ? ' locked' : ''}`}
					>
						{section.type === 'knowledge' && (
							<div className="knowledgeMeta">
								<span>{section.unlocked ? 'Unlocked' : 'Locked'}</span>
								<span>Priority {section.priority}</span>
							</div>
						)}

						<div
							className="knowledgeHtml"
							dangerouslySetInnerHTML={{ __html: section.html }}
						/>
					</section>
				)
			})}
		</article>
	)
}
