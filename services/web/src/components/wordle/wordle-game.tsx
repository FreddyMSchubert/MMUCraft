'use client';

import type { CSSProperties } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { Fireworks } from 'fireworks-js';
import { WORDLE_WORDS } from '@/data/wordle-words';
import { SitePage } from '@/components/site-page';
import { useSiteAlert } from '@/components/site-alert';
import {
	formatDisplayDate,
	getDailyAnswer,
	getDifficulty,
	getMaxGuesses,
	getNextUKMidnight,
	getStorageKey,
	MIN_WORD_LENGTH,
	scoreGuess,
	type TileResult,
	type WordleGuess,
	type WordleHintCell,
	type WordleHintRow,
} from '@/lib/wordle';

const KEYBOARD = [
	['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
	['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
	['Enter', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'Backspace'],
];
const SHARE_TILES: Record<TileResult, string> = {
	correct: '🟩',
	present: '🟨',
	absent: '⬜',
	skipped: '⬛',
};
const WORD_SETS = WORDLE_WORDS.reduce((sets, word) => {
	if (!sets.has(word.length)) sets.set(word.length, new Set());
	sets.get(word.length)?.add(word);
	return sets;
}, new Map<number, Set<string>>());

interface Puzzle {
	answer: string;
	dateKey: string;
	maxGuesses: number;
}

export function WordleGame({
	dateKey,
	background,
	splash,
}: {
	dateKey: string;
	background: string;
	splash: string;
}) {
	const fireworksStage = useRef<HTMLDivElement>(null);
	const { showAlert } = useSiteAlert();
	const busy = useRef(false);
	const puzzle = useMemo(() => {
		const answer = getDailyAnswer(WORDLE_WORDS, dateKey);
		return { answer, dateKey, maxGuesses: getMaxGuesses(answer.length) };
	}, [dateKey]);
	const savedRaw = useSyncExternalStore(
		subscribeToStorage,
		() => readStoredGame(puzzle),
		() => '',
	);
	const saved = useMemo(() => parseStoredGame(savedRaw, puzzle), [puzzle, savedRaw]);
	const [sessionGuesses, setGuesses] = useState<WordleGuess[] | null>(null);
	const [sessionHints, setHints] = useState<WordleHintRow[] | null>(null);
	const [sessionGameOver, setGameOver] = useState<boolean | null>(null);
	const guesses = useMemo(
		() => sessionGuesses ?? saved?.guesses ?? [],
		[saved?.guesses, sessionGuesses],
	);
	const hints = useMemo(() => sessionHints ?? saved?.hints ?? [], [saved?.hints, sessionHints]);
	const savedGameOver = Boolean(
		saved?.gameOver &&
		(guesses.at(-1)?.word === puzzle.answer || guesses.length >= puzzle.maxGuesses),
	);
	const gameOver = sessionGameOver ?? savedGameOver;
	const [currentGuess, setCurrentGuess] = useState('');
	const [checkingWord, setCheckingWord] = useState(false);
	const [revealingRow, setRevealingRow] = useState<number | null>(null);
	const [winningRow, setWinningRow] = useState<number | null>(null);
	const [shakeToken, setShakeToken] = useState(0);
	const [copyLabel, setCopyLabel] = useState('Copy playthrough');
	const [hintMessage, setHintMessage] = useState('');
	const [nextWordle, setNextWordle] = useState('00:00:00');

	useEffect(() => {
		const tick = () => {
			const seconds = Math.max(
				0,
				Math.floor((getNextUKMidnight(new Date()) - Date.now()) / 1000),
			);
			setNextWordle(
				[
					String(Math.floor(seconds / 3600)).padStart(2, '0'),
					String(Math.floor((seconds % 3600) / 60)).padStart(2, '0'),
					String(seconds % 60).padStart(2, '0'),
				].join(':'),
			);
		};
		tick();
		const timer = window.setInterval(tick, 1000);
		return () => {
			window.clearInterval(timer);
		};
	}, []);

	const submitGuess = useCallback(async () => {
		if (gameOver || busy.current) return;
		if (currentGuess.length < MIN_WORD_LENGTH) {
			setShakeToken((value) => value + 1);
			void showAlert({
				title: 'That guess is too short',
				message: `Enter at least ${MIN_WORD_LENGTH} letters before submitting your guess.`,
			});
			return;
		}

		busy.current = true;
		setCheckingWord(true);
		const valid = await validateGuess(currentGuess);
		setCheckingWord(false);
		if (!valid) {
			busy.current = false;
			setShakeToken((value) => value + 1);
			void showAlert({
				title: 'Word not found',
				message:
					"That guess is not in today's Minecraft word list or the English dictionary. Check the spelling and try another word.",
			});
			return;
		}

		const result = scoreGuess(currentGuess, puzzle.answer);
		const row = guesses.length;
		const won = currentGuess === puzzle.answer;
		const finished = won || row + 1 >= puzzle.maxGuesses;
		const nextGuesses = [...guesses, { word: currentGuess, result }];
		setGuesses(nextGuesses);
		setGameOver(false);
		persistGame(puzzle, nextGuesses, hints, false);
		setCurrentGuess('');
		setHintMessage('');
		setRevealingRow(row + hints.length);

		window.setTimeout(
			() => {
				busy.current = false;
				setRevealingRow(null);
				setGameOver(finished);
				persistGame(puzzle, nextGuesses, hints, finished);
				if (won) {
					setWinningRow(row + hints.length);
					if (fireworksStage.current)
						launchFireworks(fireworksStage.current, row + 1, puzzle.maxGuesses);
				}
			},
			puzzle.answer.length * 120 + 260,
		);
	}, [currentGuess, gameOver, guesses, hints, puzzle, showAlert]);

	const handleKey = useCallback(
		(key: string) => {
			if (gameOver || busy.current) return;
			if (/^[a-z]$/i.test(key)) {
				setCurrentGuess((guess) =>
					guess.length < puzzle.answer.length ? `${guess}${key.toLowerCase()}` : guess,
				);
			} else if (key === 'Backspace') setCurrentGuess((guess) => guess.slice(0, -1));
			else if (key === 'Enter') void submitGuess();
		},
		[gameOver, puzzle, submitGuess],
	);

	useEffect(() => {
		const onKeyDown = (event: KeyboardEvent) => {
			if (event.ctrlKey || event.metaKey || event.altKey) return;
			if (/^[a-z]$/i.test(event.key) || event.key === 'Enter' || event.key === 'Backspace') {
				event.preventDefault();
				handleKey(event.key);
			}
		};
		document.addEventListener('keydown', onKeyDown);
		return () => {
			document.removeEventListener('keydown', onKeyDown);
		};
	}, [handleKey]);

	const won = guesses.at(-1)?.word === puzzle.answer;
	const lost = gameOver && !won;
	const canCopy = gameOver && guesses.length > 0;
	const difficulty = getDifficulty(puzzle.answer.length);
	const layout = getWordleLayout(puzzle.answer.length);
	const styles = {
		'--word-length': puzzle.answer.length,
		'--wordle-board-width': layout.boardWidth,
		'--wordle-shell-width': layout.shellWidth,
	} as CSSProperties;
	const keyClasses = getKeyboardClasses(guesses, hints);
	const boardRows = getBoardRows(guesses, hints, puzzle.maxGuesses);

	function useHint() {
		if (gameOver || busy.current) return;
		const next = createHint(puzzle.answer, guesses, hints);
		if (!next) {
			void showAlert({
				title: 'No more hints available',
				message: 'Every letter position has already been revealed.',
			});
			return;
		}
		setHints(next.hints);
		setHintMessage(next.message);
		persistGame(puzzle, guesses, next.hints, false);
	}

	async function copyPlaythrough() {
		if (!canCopy) return;
		const score = won ? guesses.length : 'X';
		const sharedRows = getSharedRows(guesses, hints, puzzle.answer.length);
		const hintCount = hints.reduce((total, hint) => total + hint.cells.length, 0);
		const hintSummary = hintCount
			? ` + ${hintCount} ${hintCount === 1 ? 'hint' : 'hints'} 💡`
			: '';
		const text = [
			`MMU Minecraft Society Wordle ${formatDisplayDate(puzzle.dateKey)} — ${difficulty.emoji} ${difficulty.label} (${puzzle.answer.length} letters) ${score}/${puzzle.maxGuesses}${hintSummary} (➡️ https://mmuminecraftsociety.co.uk/wordle/)`,
			...sharedRows,
		].join('\n');
		try {
			await copyText(text);
			setCopyLabel('Copied');
		} catch {
			setCopyLabel('Copy failed');
			void showAlert({
				title: 'Could not copy the playthrough',
				message:
					'Your browser did not allow the result to be copied. Check clipboard permission and try again.',
				tone: 'danger',
			});
		}
		window.setTimeout(() => {
			setCopyLabel('Copy playthrough');
		}, 1600);
	}

	return (
		<SitePage
			background={background}
			splash={splash}
			className="wordlePage"
			contentClassName="wordleContent"
			overlay={<div className="fireworksStage" ref={fireworksStage} aria-hidden="true" />}
		>
			<section className="dashboard wordleDashboard" style={styles}>
				<div className="wordleTopline">
					<h2>MMU Minecraft Society Wordle</h2>
					<div className="wordleMeta">
						<span>{formatDisplayDate(puzzle.dateKey)}</span>
						<span className={`wordleDifficulty ${difficulty.tone}`}>
							{difficulty.emoji} {difficulty.label}
						</span>
						<span>{puzzle.answer.length} letters</span>
					</div>
				</div>
				<div className="wordleRules">
					<strong>Rules</strong>
					<ul>
						<li>
							You get {puzzle.maxGuesses} guesses to find today&apos;s{' '}
							{puzzle.answer.length}-letter word.
						</li>
						<li>Green is correct, gold is in the wrong place, and grey is absent.</li>
						<li>Hints reveal a useful gold or green letter without using a guess.</li>
						<li>
							The solution is Minecraft-related, but any English or listed Minecraft
							word can be guessed.
						</li>
						<li>
							Guesses may be shorter than the answer, but need at least three letters.
							Empty tiles are ignored.
						</li>
						<li>
							Answers are single words: &quot;soul&quot; and &quot;sand&quot; count;
							&quot;soulsand&quot; does not.
						</li>
					</ul>
				</div>

				{lost && <p className="wordleSolution">Solution: {puzzle.answer.toUpperCase()}</p>}

				<div className="wordleBoard" aria-label="Daily word puzzle">
					{boardRows.map((boardRow, row) =>
						Array.from({ length: puzzle.answer.length }, (_, column) => {
							const hintCell =
								boardRow.type === 'hint'
									? boardRow.hint.cells.find((cell) => cell.column === column)
									: undefined;
							const letter =
								boardRow.type === 'guess'
									? boardRow.guess.word.at(column)
									: boardRow.type === 'hint'
										? hintCell?.letter
										: boardRow.type === 'current'
											? currentGuess.at(column)
											: '';
							const result =
								boardRow.type === 'guess'
									? boardRow.guess.result.at(column)
									: boardRow.type === 'hint'
										? (hintCell?.result ?? 'skipped')
										: undefined;
							const isCurrent = boardRow.type === 'current';
							const className = [
								'wordleTile',
								letter && 'filled',
								result,
								row === revealingRow && 'reveal',
								row === winningRow && 'win',
								isCurrent && shakeToken && 'shake',
							]
								.filter(Boolean)
								.join(' ');
							return (
								<div
									className={className}
									key={`${row}-${column}-${isCurrent ? shakeToken : 0}`}
									style={{
										animationDelay:
											row === revealingRow
												? `${column * 120}ms`
												: row === winningRow
													? `${column * 80}ms`
													: undefined,
									}}
									aria-label={`${boardRow.type === 'hint' ? 'Hint row' : `Row ${row + 1}`}, letter ${column + 1}`}
								>
									{letter}
								</div>
							);
						}),
					)}
				</div>

				<p className="wordleChecking" role="status" aria-live="polite">
					{checkingWord ? 'Checking word...' : hintMessage || '\u00a0'}
				</p>

				<div className="wordleKeyboard" aria-label="On-screen keyboard">
					{KEYBOARD.map((row, rowIndex) => (
						<div className="wordleKeyboardRow" key={rowIndex}>
							{row.map((key) => (
								<button
									className={`wordleKey${key.length > 1 ? ' wide' : ''}${keyClasses[key] ? ` ${keyClasses[key]}` : ''}`}
									type="button"
									key={key}
									onClick={() => {
										handleKey(key);
									}}
									aria-label={
										key === 'Backspace'
											? 'Backspace'
											: key === 'Enter'
												? 'Enter guess'
												: key
									}
								>
									{key === 'Backspace' ? '⌫' : key}
								</button>
							))}
						</div>
					))}
				</div>

				{canCopy ? (
					<button
						className="copyPlaythrough"
						type="button"
						onClick={() => void copyPlaythrough()}
					>
						{copyLabel}
					</button>
				) : (
					<button className="wordleHintButton" type="button" onClick={useHint}>
						💡 Hint
					</button>
				)}
			</section>

			<footer className="wordleFooter">
				Next MMU Minecraft Society Wordle in <strong>{nextWordle}</strong>
			</footer>
		</SitePage>
	);
}

function getWordleLayout(wordLength: number) {
	const boardWidth = wordLength * 4.15 + Math.max(0, wordLength - 1) * 0.45;
	return {
		boardWidth: `${boardWidth.toFixed(2)}rem`,
		shellWidth: `${Math.min(58, Math.max(33.75, boardWidth + 2.25)).toFixed(2)}rem`,
	};
}

function getKeyboardClasses(guesses: WordleGuess[], hints: WordleHintRow[]) {
	const rank: Record<string, number> = { absent: 1, present: 2, correct: 3 };
	const classes: Partial<Record<string, TileResult>> = {};
	for (const guess of guesses)
		guess.word.split('').forEach((letter, index) => {
			const result = guess.result.at(index);
			if (!result) return;
			const current = classes[letter];
			if (!current || rank[result] > rank[current]) classes[letter] = result;
		});
	for (const hint of hints)
		for (const cell of hint.cells) {
			const current = classes[cell.letter];
			if (!current || rank[cell.result] > rank[current]) classes[cell.letter] = cell.result;
		}
	return classes;
}

type BoardRow =
	| { type: 'guess'; guess: WordleGuess }
	| { type: 'hint'; hint: WordleHintRow }
	| { type: 'current' }
	| { type: 'empty' };

function getBoardRows(
	guesses: WordleGuess[],
	hints: WordleHintRow[],
	maxGuesses: number,
): BoardRow[] {
	const rows: BoardRow[] = [];
	const appendHints = (afterGuess: number) => {
		for (const hint of hints)
			if (hint.afterGuess === afterGuess) rows.push({ type: 'hint', hint });
	};
	appendHints(0);
	guesses.forEach((guess, index) => {
		rows.push({ type: 'guess', guess });
		appendHints(index + 1);
	});
	for (let index = guesses.length; index < maxGuesses; index += 1)
		rows.push(index === guesses.length ? { type: 'current' } : { type: 'empty' });
	return rows;
}

function createHint(
	answer: string,
	guesses: WordleGuess[],
	hints: WordleHintRow[],
): { hints: WordleHintRow[]; message: string } | null {
	const knownPositions = new Set<number>();
	const yellowLetters = new Set<string>();
	for (const guess of guesses)
		guess.result.forEach((result, column) => {
			if (result === 'correct') knownPositions.add(column);
			if (result === 'present') yellowLetters.add(guess.word[column]);
		});
	for (const row of hints)
		for (const cell of row.cells) {
			if (cell.result === 'correct') knownPositions.add(cell.column);
			else yellowLetters.add(cell.letter);
		}

	const unresolvedYellow = answer
		.split('')
		.map((letter, column) => ({ letter, column }))
		.filter(({ letter, column }) => yellowLetters.has(letter) && !knownPositions.has(column));
	if (unresolvedYellow.length > 0) {
		const reveal = pickRandom(unresolvedYellow);
		const cell: WordleHintCell = { ...reveal, result: 'correct' };
		return {
			hints: appendHintCell(hints, guesses.length, cell),
			message: `Hint: ${cell.letter.toUpperCase()} is correct in position ${cell.column + 1}.`,
		};
	}

	const latest = hints.at(-1);
	const occupiedColumns = new Set(
		latest?.afterGuess === guesses.length ? latest.cells.map((cell) => cell.column) : [],
	);
	const yellowOptions = answer.split('').flatMap((letter, answerColumn) => {
		if (knownPositions.has(answerColumn)) return [];
		return answer
			.split('')
			.map((_, column) => ({ letter, answerColumn, column }))
			.filter(({ column }) => column !== answerColumn && answer[column] !== letter);
	});
	if (yellowOptions.length === 0) return null;
	const mergeableOptions = yellowOptions.filter(({ column }) => !occupiedColumns.has(column));
	const reveal = pickRandom(mergeableOptions.length > 0 ? mergeableOptions : yellowOptions);
	const cell: WordleHintCell = {
		column: reveal.column,
		letter: reveal.letter,
		result: 'present',
	};
	return {
		hints: appendHintCell(hints, guesses.length, cell),
		message: `Hint: ${cell.letter.toUpperCase()} is in the word, but not in position ${cell.column + 1}.`,
	};
}

function appendHintCell(hints: WordleHintRow[], afterGuess: number, cell: WordleHintCell) {
	const latest = hints.at(-1);
	if (
		latest?.afterGuess === afterGuess &&
		!latest.cells.some(({ column }) => column === cell.column)
	) {
		return [
			...hints.slice(0, -1),
			{ ...latest, cells: [...latest.cells, cell].sort((a, b) => a.column - b.column) },
		];
	}
	return [...hints, { afterGuess, cells: [cell] }];
}

function getSharedRows(guesses: WordleGuess[], hints: WordleHintRow[], wordLength: number) {
	return getBoardRows(guesses, hints, guesses.length)
		.filter((row) => row.type === 'guess' || row.type === 'hint')
		.map((row) => {
			if (row.type === 'guess')
				return row.guess.result.map((result) => SHARE_TILES[result]).join('');
			const tiles = Array<TileResult>(wordLength).fill('skipped');
			for (const cell of row.hint.cells) tiles[cell.column] = cell.result;
			return `${tiles.map((result) => SHARE_TILES[result]).join('')} - ${'💡'.repeat(row.hint.cells.length)}`;
		});
}

function pickRandom<T>(items: T[]) {
	return items[Math.floor(Math.random() * items.length)];
}

async function validateGuess(guess: string) {
	if (WORD_SETS.get(guess.length)?.has(guess)) return true;
	const controller = new AbortController();
	const timeout = window.setTimeout(() => {
		controller.abort();
	}, 3500);
	try {
		const response = await fetch(`/wordle/validate?guess=${encodeURIComponent(guess)}`, {
			cache: 'no-store',
			signal: controller.signal,
		});
		if (!response.ok) return false;
		const { isWord } = (await response.json()) as { isWord: boolean };
		return isWord;
	} catch {
		return false;
	} finally {
		window.clearTimeout(timeout);
	}
}

function subscribeToStorage(callback: () => void) {
	window.addEventListener('storage', callback);
	return () => {
		window.removeEventListener('storage', callback);
	};
}

function readStoredGame(puzzle: Puzzle) {
	try {
		return localStorage.getItem(getStorageKey(puzzle.dateKey, puzzle.answer)) ?? '';
	} catch {
		return '';
	}
}

function parseStoredGame(
	raw: string,
	puzzle: Puzzle,
): { guesses: WordleGuess[]; hints: WordleHintRow[]; gameOver: boolean } | null {
	try {
		const saved = JSON.parse(raw || 'null') as unknown;
		if (!saved || typeof saved !== 'object') return null;
		if ('answer' in saved && saved.answer === puzzle.answer && 'guesses' in saved) {
			if (Array.isArray(saved.guesses)) {
				return {
					guesses: saved.guesses as WordleGuess[],
					hints:
						'hints' in saved && Array.isArray(saved.hints)
							? (saved.hints as WordleHintRow[])
							: [],
					gameOver: Boolean('gameOver' in saved && saved.gameOver),
				};
			}
		}
	} catch {
		/* Start fresh. */
	}
	return null;
}

function persistGame(
	puzzle: Puzzle,
	guesses: WordleGuess[],
	hints: WordleHintRow[],
	gameOver: boolean,
) {
	try {
		localStorage.setItem(
			getStorageKey(puzzle.dateKey, puzzle.answer),
			JSON.stringify({ answer: puzzle.answer, guesses, hints, gameOver }),
		);
	} catch {
		/* Local storage is optional. */
	}
}

async function copyText(text: string) {
	await navigator.clipboard.writeText(text);
}

function launchFireworks(stage: HTMLElement, attempts: number, maxGuesses: number) {
	const durationSeconds = maxGuesses - Math.min(maxGuesses, Math.max(1, attempts)) + 1;
	const scale = (durationSeconds - 1) / (maxGuesses - 1);
	const scaleValue = (minimum: number, maximum: number) =>
		Math.round(minimum + (maximum - minimum) * scale);
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
	});
	const duration = durationSeconds * 1000;
	fireworks.start();
	window.setTimeout(() => {
		fireworks.stop();
	}, duration);
	window.setTimeout(() => {
		fireworks.clear();
	}, duration + 900);
}
