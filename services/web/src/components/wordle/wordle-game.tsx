'use client'

import type { CSSProperties } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Fireworks } from 'fireworks-js'
import { WORDLE_WORDS } from '@/data/wordle-words'
import { SitePage } from '@/components/site-page'
import {
	formatDisplayDate,
	getDailyAnswer,
	getMaxGuesses,
	getNextUKMidnight,
	getStorageKey,
	MIN_WORD_LENGTH,
	scoreGuess,
	type TileResult,
	type WordleGuess,
} from '@/lib/wordle'

const DICTIONARY_API = 'https://api.dictionaryapi.dev/api/v2/entries/en'
const DICTIONARY_CACHE_PREFIX = 'mmu-mcsoc-dictionary'
const DICTIONARY_CACHE_MAX_AGE = 30 * 86_400_000
const KEYBOARD = [
	['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
	['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
	['Enter', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'Backspace'],
]
const SHARE_TILES: Record<TileResult, string> = { correct: '🟩', present: '🟨', absent: '⬛', skipped: '⬜' }
const WORD_SETS = WORDLE_WORDS.reduce((sets, word) => {
	if (!sets.has(word.length)) sets.set(word.length, new Set())
	sets.get(word.length)?.add(word)
	return sets
}, new Map<number, Set<string>>())

interface Puzzle {
	answer: string
	dateKey: string
	maxGuesses: number
}

export function WordleGame({ dateKey, background, splash }: { dateKey: string; background: string; splash: string }) {
	const fireworksStage = useRef<HTMLDivElement>(null)
	const busy = useRef(false)
	const puzzle = useMemo(() => {
		const answer = getDailyAnswer(WORDLE_WORDS, dateKey)
		return { answer, dateKey, maxGuesses: getMaxGuesses(answer.length) }
	}, [dateKey])
	const savedRaw = useSyncExternalStore(subscribeToStorage, () => readStoredGame(puzzle), () => '')
	const saved = useMemo(() => parseStoredGame(savedRaw, puzzle), [puzzle, savedRaw])
	const [sessionGuesses, setGuesses] = useState<WordleGuess[] | null>(null)
	const [sessionGameOver, setGameOver] = useState<boolean | null>(null)
	const guesses = useMemo(() => sessionGuesses ?? saved?.guesses ?? [], [saved?.guesses, sessionGuesses])
	const savedGameOver = Boolean(saved?.gameOver && (guesses.at(-1)?.word === puzzle.answer || guesses.length >= puzzle.maxGuesses))
	const gameOver = sessionGameOver ?? savedGameOver
	const [currentGuess, setCurrentGuess] = useState('')
	const [statusOverride, setStatus] = useState<string | null>(null)
	const [revealingRow, setRevealingRow] = useState<number | null>(null)
	const [winningRow, setWinningRow] = useState<number | null>(null)
	const [shakeToken, setShakeToken] = useState(0)
	const [copyLabel, setCopyLabel] = useState('Copy playthrough')
	const [nextWordle, setNextWordle] = useState('00:00:00')

	useEffect(() => {
		const tick = () => {
			const seconds = Math.max(0, Math.floor((getNextUKMidnight(new Date()) - Date.now()) / 1000))
			setNextWordle([
				String(Math.floor(seconds / 3600)).padStart(2, '0'),
				String(Math.floor((seconds % 3600) / 60)).padStart(2, '0'),
				String(seconds % 60).padStart(2, '0'),
			].join(':'))
		}
		tick()
		const timer = window.setInterval(tick, 1000)
		return () => window.clearInterval(timer)
	}, [])

	const submitGuess = useCallback(async () => {
		if (gameOver || busy.current) return
		if (currentGuess.length < MIN_WORD_LENGTH) {
			setShakeToken((value) => value + 1)
			setStatus(`Guesses need at least ${MIN_WORD_LENGTH} letters.`)
			return
		}

		busy.current = true
		setStatus('Checking word...')
		if (!await validateGuess(currentGuess)) {
			busy.current = false
			setShakeToken((value) => value + 1)
			setStatus("Not in today's word list or dictionary.")
			return
		}

		const result = scoreGuess(currentGuess, puzzle.answer)
		const row = guesses.length
		const won = currentGuess === puzzle.answer
		const finished = won || row + 1 >= puzzle.maxGuesses
		const nextGuesses = [...guesses, { word: currentGuess, result }]
		setGuesses(nextGuesses)
		setGameOver(false)
		persistGame(puzzle, nextGuesses, false)
		setCurrentGuess('')
		setRevealingRow(row)

		window.setTimeout(() => {
			busy.current = false
			setRevealingRow(null)
			setGameOver(finished)
			persistGame(puzzle, nextGuesses, finished)
			if (won) {
				setStatus('You got it. Nicely done.')
				setWinningRow(row)
				if (fireworksStage.current) launchFireworks(fireworksStage.current, row + 1, puzzle.maxGuesses)
			} else setStatus(finished ? 'Out of guesses. Come back tomorrow.' : 'Keep going.')
		}, puzzle.answer.length * 120 + 260)
	}, [currentGuess, gameOver, guesses, puzzle])

	const handleKey = useCallback((key: string) => {
		if (gameOver || busy.current) return
		if (/^[a-z]$/i.test(key)) {
			setCurrentGuess((guess) => guess.length < puzzle.answer.length ? `${guess}${key.toLowerCase()}` : guess)
		} else if (key === 'Backspace') setCurrentGuess((guess) => guess.slice(0, -1))
		else if (key === 'Enter') void submitGuess()
	}, [gameOver, puzzle, submitGuess])

	useEffect(() => {
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.ctrlKey || event.metaKey || event.altKey) return
			if (/^[a-z]$/i.test(event.key) || event.key === 'Enter' || event.key === 'Backspace') {
				event.preventDefault()
				handleKey(event.key)
			}
		}
		document.addEventListener('keydown', onKeyDown)
		return () => document.removeEventListener('keydown', onKeyDown)
	}, [handleKey])

	const won = guesses.at(-1)?.word === puzzle.answer
	const lost = gameOver && !won
	const status = statusOverride ?? (gameOver ? won ? 'You got it. Nicely done.' : 'Out of guesses. Come back tomorrow.' : "Today's puzzle is ready.")
	const canCopy = gameOver && guesses.length > 0
	const layout = getWordleLayout(puzzle.answer.length)
	const styles = {
		'--word-length': puzzle.answer.length,
		'--wordle-board-width': layout.boardWidth,
		'--wordle-shell-width': layout.shellWidth,
	} as CSSProperties
	const keyClasses = getKeyboardClasses(guesses)

	async function copyPlaythrough() {
		if (!canCopy) return
		const score = won ? guesses.length : 'X'
		const rows = guesses.map((guess) => guess.result.map((result) => SHARE_TILES[result]).join(''))
		const text = [`MMU Minecraft Society Wordle ${formatDisplayDate(puzzle!.dateKey)} ${score}/${puzzle!.maxGuesses} (➡️ https://mmuminecraftsociety.co.uk/wordle/)`, ...rows].join('\n')
		try {
			await copyText(text)
			setStatus('Playthrough copied.')
			setCopyLabel('Copied')
		} catch {
			setStatus('Copy failed. Please try again.')
			setCopyLabel('Copy failed')
		}
		window.setTimeout(() => setCopyLabel('Copy playthrough'), 1600)
	}

	return <SitePage background={background} splash={splash} className="wordlePage" contentClassName="wordleContent" overlay={<div className="fireworksStage" ref={fireworksStage} aria-hidden="true" />}>
			<section className="dashboard wordleDashboard" style={styles}>
				<div className="wordleTopline">
					<h2>MMU Minecraft Society Wordle</h2>
					<span>{formatDisplayDate(puzzle.dateKey)}</span>
				</div>
				<div className="wordleRules">
					<strong>Rules</strong>
					<ul>
						<li>You get {puzzle.maxGuesses} guesses to find today&apos;s {puzzle.answer.length}-letter word.</li>
						<li>Green is correct, gold is in the wrong place, and grey is absent.</li>
						<li>The solution is Minecraft-related, but any English or listed Minecraft word can be guessed.</li>
						<li>Guesses may be shorter than the answer, but need at least three letters. Empty tiles are ignored.</li>
						<li>Answers are single words: &quot;soul&quot; and &quot;sand&quot; count; &quot;soulsand&quot; does not.</li>
					</ul>
				</div>

				<p className="wordleStatus" role="status" aria-live="polite">{status}</p>
				{lost && <p className="wordleSolution">Solution: {puzzle.answer.toUpperCase()}</p>}

				<div className="wordleBoard" aria-label="Daily word puzzle">
					{Array.from({ length: puzzle.maxGuesses }, (_, row) => Array.from({ length: puzzle.answer.length }, (_, column) => {
						const saved = guesses[row]
						const letter = saved?.word[column] ?? (row === guesses.length ? currentGuess[column] : '') ?? ''
						const result = saved?.result[column]
						const isCurrent = row === guesses.length
						const className = ['wordleTile', letter && 'filled', result, row === revealingRow && 'reveal', row === winningRow && 'win', isCurrent && shakeToken && 'shake'].filter(Boolean).join(' ')
						return <div
							className={className}
							key={`${row}-${column}-${isCurrent ? shakeToken : 0}`}
							style={{ animationDelay: row === revealingRow ? `${column * 120}ms` : row === winningRow ? `${column * 80}ms` : undefined }}
							aria-label={`Row ${row + 1}, letter ${column + 1}`}
						>{letter}</div>
					}))}
				</div>

				<div className="wordleKeyboard" aria-label="On-screen keyboard">
					{KEYBOARD.map((row, rowIndex) => <div className="wordleKeyboardRow" key={rowIndex}>
						{row.map((key) => <button
							className={`wordleKey${key.length > 1 ? ' wide' : ''}${keyClasses[key] ? ` ${keyClasses[key]}` : ''}`}
							type="button"
							key={key}
							onClick={() => handleKey(key)}
							aria-label={key === 'Backspace' ? 'Backspace' : key === 'Enter' ? 'Enter guess' : key}
						>{key === 'Backspace' ? '⌫' : key}</button>)}
					</div>)}
				</div>

				<button className="copyPlaythrough" type="button" disabled={!canCopy} onClick={() => void copyPlaythrough()}>{copyLabel}</button>
			</section>

			<footer className="wordleFooter">Next MMU Minecraft Society Wordle in <strong>{nextWordle}</strong></footer>
	</SitePage>
}

function getWordleLayout(wordLength: number) {
	const boardWidth = wordLength * 4.15 + Math.max(0, wordLength - 1) * 0.45
	return {
		boardWidth: `${boardWidth.toFixed(2)}rem`,
		shellWidth: `${Math.min(58, Math.max(33.75, boardWidth + 2.25)).toFixed(2)}rem`,
	}
}

function getKeyboardClasses(guesses: WordleGuess[]) {
	const rank: Record<string, number> = { absent: 1, present: 2, correct: 3 }
	const classes: Record<string, TileResult> = {}
	for (const guess of guesses) guess.word.split('').forEach((letter, index) => {
		const result = guess.result[index]
		if (!classes[letter] || rank[result] > rank[classes[letter]]) classes[letter] = result
	})
	return classes
}

async function validateGuess(guess: string) {
	if (WORD_SETS.get(guess.length)?.has(guess)) return true
	const cacheKey = `${DICTIONARY_CACHE_PREFIX}:${guess}`
	try {
		const cached = JSON.parse(localStorage.getItem(cacheKey) ?? 'null') as { isWord?: boolean; savedAt?: number } | null
		if (cached && typeof cached.isWord === 'boolean' && Date.now() - (cached.savedAt ?? 0) < DICTIONARY_CACHE_MAX_AGE) return cached.isWord
	} catch { /* Continue to the dictionary. */ }

	const controller = new AbortController()
	const timeout = window.setTimeout(() => controller.abort(), 3500)
	try {
		const response = await fetch(`${DICTIONARY_API}/${encodeURIComponent(guess)}`, { cache: 'force-cache', signal: controller.signal })
		if (!response.ok && response.status !== 404) return true
		const isWord = response.ok && Array.isArray(await response.json())
		try { localStorage.setItem(cacheKey, JSON.stringify({ isWord, savedAt: Date.now() })) } catch { /* Cache is optional. */ }
		return isWord
	} catch {
		return true
	} finally {
		window.clearTimeout(timeout)
	}
}

function subscribeToStorage(callback: () => void) {
	window.addEventListener('storage', callback)
	return () => window.removeEventListener('storage', callback)
}

function readStoredGame(puzzle: Puzzle) {
	try { return localStorage.getItem(getStorageKey(puzzle.dateKey, puzzle.answer)) ?? '' } catch { return '' }
}

function parseStoredGame(raw: string, puzzle: Puzzle): { guesses: WordleGuess[]; gameOver: boolean } | null {
	try {
		const saved = JSON.parse(raw || 'null')
		if (saved?.answer === puzzle.answer && Array.isArray(saved.guesses)) return saved
	} catch { /* Start fresh. */ }
	return null
}

function persistGame(puzzle: Puzzle, guesses: WordleGuess[], gameOver: boolean) {
	try {
		localStorage.setItem(getStorageKey(puzzle.dateKey, puzzle.answer), JSON.stringify({ answer: puzzle.answer, guesses, gameOver }))
	} catch { /* Local storage is optional. */ }
}

async function copyText(text: string) {
	if (navigator.clipboard?.writeText) {
		try { await navigator.clipboard.writeText(text); return } catch { /* Use the legacy path. */ }
	}
	const textArea = document.createElement('textarea')
	textArea.value = text
	textArea.readOnly = true
	textArea.style.position = 'fixed'
	textArea.style.top = '-999px'
	document.body.appendChild(textArea)
	textArea.select()
	const copied = document.execCommand('copy')
	textArea.remove()
	if (!copied) throw new Error('Copy command failed')
}

function launchFireworks(stage: HTMLElement, attempts: number, maxGuesses: number) {
	const durationSeconds = maxGuesses - Math.min(maxGuesses, Math.max(1, attempts)) + 1
	const scale = (durationSeconds - 1) / (maxGuesses - 1)
	const scaleValue = (minimum: number, maximum: number) => Math.round(minimum + (maximum - minimum) * scale)
	const fireworks = new Fireworks(stage, {
		autoresize: true,
		opacity: 0.72,
		acceleration: 1.02,
		friction: 0.97,
		gravity: 1.35,
		particles: scaleValue(62, 280),
		traceLength: scaleValue(2, 7),
		explosion: scaleValue(4, 12),
		intensity: scaleValue(17, 96),
		hue: { min: 38, max: 136 },
	})
	const duration = durationSeconds * 1000
	fireworks.start()
	window.setTimeout(() => fireworks.stop(), duration)
	window.setTimeout(() => fireworks.clear(), duration + 900)
}
