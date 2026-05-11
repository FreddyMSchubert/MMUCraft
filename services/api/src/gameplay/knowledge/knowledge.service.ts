import { Injectable } from '@nestjs/common'
import { randomInt } from 'node:crypto'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { DatabaseService, UserRow } from '../../database/database.service'

const DEFAULT_KNOWLEDGE_PATH = join(process.cwd(), 'content', 'knowledge.html')
const OBSCURED_IMAGE_SRC = '/assets/knowledge/obscured.png'

type PublicKnowledgeSection = {
	type: 'public'
	html: string
}

type UnlockableKnowledgeSection = {
	type: 'knowledge'
	id: string
	priority: number
	topic: string
	html: string
}

type ParsedKnowledgeSection = PublicKnowledgeSection | UnlockableKnowledgeSection

interface KnowledgeDocument {
	sections: ParsedKnowledgeSection[]
	unlockable: UnlockableKnowledgeSection[]
}

interface CachedKnowledgeDocument {
	path: string
	mtimeMs: number
	document: KnowledgeDocument
}

interface KnowledgeUnlockResponse {
	unlocked: boolean
	all_unlocked: boolean
	knowledge_id: string
	priority: number
	topic: string
	message: string
}

type KnowledgeResponseSection =
	| PublicKnowledgeSection
	| {
		type: 'knowledge'
		id: string
		priority: number
		topic: string
		unlocked: boolean
		html: string
	}

@Injectable()
export class KnowledgeService {
	private cached: CachedKnowledgeDocument | null = null

	constructor(private readonly database: DatabaseService) { }

	getKnowledgeForUser(userId: number) {
		const document = this.loadDocument()
		const unlockedIds = this.getUnlockedIds(userId)
		const lastUnlockedKnowledgeId = this.getLastUnlockedKnowledgeId(userId, document.unlockable)

		return {
			lastUnlockedKnowledgeId,
			sections: document.sections.map((section): KnowledgeResponseSection => {
				if (section.type === 'public') {
					return section
				}

				const unlocked = unlockedIds.has(section.id)

				return {
					type: 'knowledge' as const,
					id: section.id,
					priority: section.priority,
					topic: section.topic,
					unlocked,
					html: unlocked ? section.html : this.obscureLockedHtml(section.html),
				}
			}),
		}
	}

	unlockNextForMinecraftUsername(
		minecraftUsernameInput: string,
		sourceInput: string,
	): KnowledgeUnlockResponse {
		const minecraftUsername = minecraftUsernameInput.trim()
		const source = sourceInput.trim() || 'knowledge_book'

		if (!minecraftUsername) {
			return this.noUnlock('No Minecraft username was provided.')
		}

		const user = this.database.connection.prepare(`
						SELECT *
						FROM users
						WHERE lower(minecraft_username) = lower(?)
				`).get(minecraftUsername) as UserRow | undefined

		if (!user) {
			return this.noUnlock('No website account is linked to this Minecraft username yet.')
		}

		const document = this.loadDocument()

		if (document.unlockable.length === 0) {
			return {
				unlocked: false,
				all_unlocked: true,
				knowledge_id: '',
				priority: 0,
				topic: '',
				message: 'There is no unlockable knowledge configured yet.',
			}
		}

		for (let attempt = 0; attempt < 5; attempt++) {
			const picked = this.database.connection.transaction(() => {
				const unlockedIds = this.getUnlockedIds(user.id)

				const remaining = document.unlockable.filter((section) => !unlockedIds.has(section.id))

				if (remaining.length === 0) {
					return 'all-unlocked' as const
				}

				const chosen = this.pickRandomLowestPrioritySection(remaining)

				const result = this.database.connection.prepare(`
					INSERT OR IGNORE INTO knowledge_unlocks (
							user_id,
							knowledge_id,
							unlocked_at_unix_ms,
							source
					)
					VALUES (?, ?, ?, ?)
				`).run(
					user.id,
					chosen.id,
					Date.now(),
					source,
				)

				return result.changes === 1 ? chosen : null
			})()

			if (picked === 'all-unlocked') {
				return {
					unlocked: false,
					all_unlocked: true,
					knowledge_id: '',
					priority: 0,
					topic: '',
					message: 'You have already unlocked all available knowledge.',
				}
			}

			if (picked) {
				return {
					unlocked: true,
					all_unlocked: false,
					knowledge_id: picked.id,
					priority: picked.priority,
					topic: picked.topic,
					message: `You've unlocked new knowledge about ${picked.topic}. Visit the website to learn more.`,
				}
			}
		}

		return this.noUnlock('Knowledge unlock was busy. Try again.')
	}

	private getUnlockedIds(userId: number): Set<string> {
		const rows = this.database.connection.prepare(`
						SELECT knowledge_id
						FROM knowledge_unlocks
						WHERE user_id = ?
				`).all(userId) as { knowledge_id: string }[]

		return new Set(rows.map((row) => row.knowledge_id))
	}

	private pickRandomLowestPrioritySection(
		sections: UnlockableKnowledgeSection[],
	): UnlockableKnowledgeSection {
		const lowestPriority = Math.min(...sections.map((section) => section.priority))
		const candidates = sections.filter((section) => section.priority === lowestPriority)

		return candidates[randomInt(candidates.length)]!
	}

	private getLastUnlockedKnowledgeId(
		userId: number,
		unlockable: UnlockableKnowledgeSection[],
	): string | null {
		const configuredIds = new Set(unlockable.map((section) => section.id))

		const rows = this.database.connection.prepare(`
			SELECT knowledge_id
			FROM knowledge_unlocks
			WHERE user_id = ?
			ORDER BY unlocked_at_unix_ms DESC
		`).all(userId) as { knowledge_id: string }[]

		return rows.find((row) => configuredIds.has(row.knowledge_id))?.knowledge_id ?? null
	}

	private loadDocument(): KnowledgeDocument {
		const path = process.env.KNOWLEDGE_HTML_PATH ?? DEFAULT_KNOWLEDGE_PATH

		if (!existsSync(path)) {
			return {
				sections: [
					{
						type: 'public',
						html: '<p>No knowledge file exists yet.</p>',
					},
				],
				unlockable: [],
			}
		}

		const mtimeMs = statSync(path).mtimeMs

		if (this.cached && this.cached.path === path && this.cached.mtimeMs === mtimeMs) {
			return this.cached.document
		}

		const source = readFileSync(path, 'utf8')
		const document = this.parseKnowledgeHtml(source)

		this.cached = {
			path,
			mtimeMs,
			document,
		}

		return document
	}

	private parseKnowledgeHtml(source: string): KnowledgeDocument {
		const sections: ParsedKnowledgeSection[] = []
		const publicBuffer: string[] = []

		let current:
			| {
				id: string
				priority: number
				topic: string
				lines: string[]
			}
			| null = null

		const flushPublic = () => {
			const html = this.stripDangerousHtml(publicBuffer.join('\n')).trim()

			if (html) {
				sections.push({
					type: 'public',
					html,
				})
			}

			publicBuffer.length = 0
		}

		const lines = source.replace(/\r\n/g, '\n').split('\n')

		for (const line of lines) {
			const start = line.match(/^###\s+([A-Za-z0-9_-]+)\s+(-?\d+)\s+"([^"]+)"\s*$/)
			const end = line.match(/^###\s*$/)

			if (!current && start) {
				flushPublic()

				current = {
					id: start[1]!,
					priority: Number(start[2]),
					topic: start[3]!,
					lines: [],
				}

				continue
			}

			if (current && end) {
				const html = this.stripDangerousHtml(current.lines.join('\n')).trim()

				sections.push({
					type: 'knowledge',
					id: current.id,
					priority: current.priority,
					topic: current.topic,
					html,
				})

				current = null
				continue
			}

			if (current) {
				current.lines.push(line)
			} else {
				publicBuffer.push(line)
			}
		}

		if (current) {
			throw new Error(`Unclosed knowledge section: ${current.id}`)
		}

		flushPublic()

		const unlockable = sections.filter(
			(section): section is UnlockableKnowledgeSection => section.type === 'knowledge',
		)

		const seen = new Set<string>()
		for (const section of unlockable) {
			if (seen.has(section.id)) {
				throw new Error(`Duplicate knowledge id: ${section.id}`)
			}

			seen.add(section.id)
		}

		return {
			sections,
			unlockable,
		}
	}

	private stripDangerousHtml(html: string): string {
		return html.replace(/<script\b[\s\S]*?<\/script>/gi, '')
	}

	private obscureLockedHtml(html: string): string {
		return html
			.split(/(<[^>]*>)/g)
			.map((part) => {
				if (part.startsWith('<')) {
					return this.obscureTag(part)
				}

				return this.obscureTextNodes(part)
			})
			.join('')
	}

	private obscureTag(tag: string): string {
		if (!/^<img\b/i.test(tag)) {
			return tag
		}

		if (/\ssrc\s*=/i.test(tag)) {
			return tag.replace(
				/\ssrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/i,
				` src="${OBSCURED_IMAGE_SRC}"`,
			)
		}

		return tag.replace(/^<img\b/i, `<img src="${OBSCURED_IMAGE_SRC}"`)
	}

	private obscureTextNodes(text: string): string {
		return text.replace(/[^\s]+/g, (segment) => {
			const value = this.escapeHtml(this.randomGlyphs(segment.length))
			return `<span class="minecraftObfuscated" data-obfuscated-length="${segment.length}" style="--obfuscated-width: ${segment.length}ch">${value}</span>`
		})
	}

	private randomGlyphs(length: number): string {
		const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*'
		let result = ''

		for (let index = 0; index < length; index++) {
			result += letters.charAt(randomInt(letters.length))
		}

		return result
	}

	private escapeHtml(value: string): string {
		return value
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#39;')
	}

	private noUnlock(message: string): KnowledgeUnlockResponse {
		return {
			unlocked: false,
			all_unlocked: false,
			knowledge_id: '',
			priority: 0,
			topic: '',
			message,
		}
	}
}
