'use client';

import Image from 'next/image';
import {
	useCallback,
	useEffect,
	useLayoutEffect,
	useRef,
	useState,
	type CSSProperties,
} from 'react';
import { PlayerSelector } from './player-selector';

const LETTER_COLORS = {
	red: { label: 'Red', value: '#a82f3d', sealFilter: 'hue-rotate(0deg)' },
	green: {
		label: 'Green',
		value: '#286b51',
		sealFilter: 'hue-rotate(145deg) saturate(.55) brightness(1.1)',
	},
	yellow: {
		label: 'Yellow',
		value: '#d9a83b',
		sealFilter: 'hue-rotate(45deg) saturate(.7) brightness(1.4)',
	},
	orange: {
		label: 'Orange',
		value: '#c56c32',
		sealFilter: 'hue-rotate(25deg) saturate(.75) brightness(1.1)',
	},
	blue: {
		label: 'Blue',
		value: '#345b95',
		sealFilter: 'hue-rotate(215deg) saturate(.65) brightness(1.05)',
	},
	'light-blue': {
		label: 'Light blue',
		value: '#5a9fba',
		sealFilter: 'hue-rotate(195deg) saturate(.55) brightness(1.1)',
	},
	purple: {
		label: 'Purple',
		value: '#704b8b',
		sealFilter: 'hue-rotate(270deg) saturate(.6) brightness(1.05)',
	},
	pink: {
		label: 'Pink',
		value: '#c16b8f',
		sealFilter: 'hue-rotate(330deg) saturate(.55) brightness(1.08)',
	},
} as const;
type LetterColor = keyof typeof LETTER_COLORS;
const letterStyle = (color: string): CSSProperties => {
	const option = color in LETTER_COLORS ? LETTER_COLORS[color as LetterColor] : LETTER_COLORS.red;
	return { '--letter-color': option.value, '--seal-filter': option.sealFilter } as CSSProperties;
};

const STAMPS = [
	'heart',
	'landscape',
	'unicorn',
	'bowarrow',
	'flower',
	'shrek',
	'sparkle',
	'shuriken',
	'house',
] as const;
function stampHash(id: string) {
	let hash = 0;
	for (const character of id) hash = (hash * 31 + character.charCodeAt(0)) | 0;
	return hash >>> 0;
}
function stampFor(id: string) {
	return `/assets/presents/stamp-${STAMPS[stampHash(id) % STAMPS.length]}.png`;
}
function stampAngle(id: string): CSSProperties {
	return { '--stamp-angle': `${(((stampHash(id) >>> 3) % 33) - 16) / 2}deg` } as CSSProperties;
}
function sealAngle(id: string): CSSProperties {
	return { '--seal-angle': `${((stampHash(id) >>> 8) % 25) - 12}deg` } as CSSProperties;
}

export interface PresentView {
	id: string;
	from: string | null;
	to: string | null;
	title: string;
	body: string | null;
	color: LetterColor;
	anonymous: boolean;
	itemName: string | null;
	itemCount: number | null;
	hasGift: boolean;
	status: string;
	opened: boolean;
	read: boolean;
	sentAt: number | null;
	claimedAt: number | null;
	adminReason: string | null;
}

interface PresentsState {
	clock: { now: number; simulatedAt: number | null; sending: boolean; receiving: boolean };
	draft: PresentView | null;
	incoming: PresentView[];
	sent: PresentView[];
	sentTodayRecipientIds: number[];
	returned: { id: string; reason: string }[];
	pendingReturns: { id: string; itemName: string | null }[];
	players: { id: number; minecraftUsername: string; nickname: string; color: string }[];
}

interface DraftInput {
	recipient: string;
	title: string;
	body: string;
	color: LetterColor;
	anonymous: boolean;
}

async function api<T>(path: string, method = 'GET', body?: object): Promise<T> {
	const response = await fetch(path, {
		method,
		cache: 'no-store',
		headers: body ? { 'content-type': 'application/json' } : undefined,
		body: body ? JSON.stringify(body) : undefined,
	});
	const value = (await response.json().catch(() => ({}))) as { message?: string };
	if (!response.ok) throw new Error(value.message ?? 'The letter could not be updated.');
	return value as T;
}

function alertError(cause: unknown, fallback: string) {
	window.alert(cause instanceof Error ? cause.message : fallback);
}

function paperTransform(from: DOMRect, to: DOMRect) {
	return `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${from.width / to.width}, ${from.height / to.height})`;
}

function restingPaperRect(paper: HTMLElement) {
	const envelope = paper.closest('.presentEnvelopeObject');
	if (!(envelope instanceof HTMLElement)) return paper.getBoundingClientRect();
	const rect = envelope.getBoundingClientRect();
	return new DOMRect(
		rect.left + paper.offsetLeft,
		rect.top + paper.offsetTop,
		paper.offsetWidth,
		paper.offsetHeight,
	);
}

function paperLift(paper: HTMLElement) {
	const envelope = paper.closest('.presentEnvelopeObject');
	if (!(envelope instanceof HTMLElement)) return paper.offsetHeight * 0.4;
	return Math.max(0, paper.offsetTop + paper.offsetHeight - envelope.offsetHeight * 0.32 + 12);
}

export function PresentsTab({ senderName }: { senderName: string }) {
	const [state, setState] = useState<PresentsState | null>(null);
	const [recipient, setRecipient] = useState('');
	const [title, setTitle] = useState('');
	const [body, setBody] = useState('');
	const [color, setColor] = useState<LetterColor>('red');
	const [anonymous, setAnonymous] = useState(false);
	const [page, setPage] = useState(0);
	const [mode, setMode] = useState<
		'list' | 'envelope' | 'writing' | 'received-envelope' | 'reading'
	>('list');
	const [writerReady, setWriterReady] = useState(false);
	const [sending, setSending] = useState(false);
	const [revealing, setRevealing] = useState(false);
	const [glowReturning, setGlowReturning] = useState<LetterColor | null>(null);
	const [opening, setOpening] = useState('');
	const [selectedId, setSelectedId] = useState('');
	const [flipped, setFlipped] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const dialog = useRef<HTMLDialogElement>(null);
	const previewPaper = useRef<HTMLSpanElement>(null);
	const writingPaper = useRef<HTMLDivElement>(null);
	const previewRect = useRef<DOMRect | null>(null);
	const writingAnimation = useRef<Animation | null>(null);
	const liftAnimation = useRef<Animation | null>(null);
	const insertionAnimation = useRef<Animation | null>(null);
	const liftDistance = useRef(0);
	const returningPaper = useRef(false);
	const sendingInProgress = useRef(false);
	const saveQueue = useRef<Promise<void>>(Promise.resolve());
	const draftId = useRef<string | null>(null);
	const initialized = useRef(false);
	const seenReturns = useRef(new Set<string>());
	const readRequests = useRef(new Set<string>());
	const loaded = state !== null;
	const applyState = useCallback((next: PresentsState) => {
		const nextId = next.draft?.id ?? null;
		if (!initialized.current || (draftId.current !== null && nextId === null)) {
			setRecipient(next.draft?.to ?? '');
			setTitle(next.draft?.title ?? '');
			setBody(next.draft?.body ?? '');
			setColor(next.draft?.color ?? 'red');
			setAnonymous(next.draft?.anonymous ?? false);
			setMode('list');
			setPage(0);
		}
		draftId.current = nextId;
		if (!initialized.current) {
			for (const item of next.returned) seenReturns.current.add(item.id);
			initialized.current = true;
		}
		for (const item of next.returned) {
			if (!seenReturns.current.has(item.id)) {
				seenReturns.current.add(item.id);
				window.alert(`An admin returned your letter: ${item.reason}`);
			}
		}
		setState(next);
	}, []);

	useEffect(() => {
		const element = dialog.current;
		if (!element) return;
		if (mode === 'list') {
			if (element.open) element.close();
			return;
		}
		if (!element.open) element.showModal();
	}, [mode]);

	useEffect(() => {
		if (mode === 'list') return;
		const previous = document.body.style.overflow;
		document.body.style.overflow = 'hidden';
		return () => {
			document.body.style.overflow = previous;
		};
	}, [mode]);

	useLayoutEffect(() => {
		if ((mode !== 'writing' && mode !== 'reading') || !writingPaper.current) return;
		if (!previewRect.current || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			setWriterReady(true);
			return;
		}
		const paper = writingPaper.current;
		const animation = paper.animate(
			[
				{ transform: paperTransform(previewRect.current, paper.getBoundingClientRect()) },
				{ transform: 'none' },
			],
			{ duration: 950, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'both' },
		);
		writingAnimation.current = animation;
		animation.onfinish = () => {
			setWriterReady(true);
		};
		liftAnimation.current?.cancel();
	}, [mode]);

	useLayoutEffect(() => {
		if (
			(mode !== 'envelope' && mode !== 'received-envelope') ||
			!returningPaper.current ||
			!previewPaper.current
		)
			return;
		returningPaper.current = false;
		const animation = previewPaper.current.animate(
			[{ transform: `translateY(-${liftDistance.current}px)` }, { transform: 'none' }],
			{ duration: 650, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' },
		);
		insertionAnimation.current = animation;
		animation.onfinish = () => {
			setRevealing(false);
			setBusy(false);
		};
		animation.oncancel = () => {
			setRevealing(false);
			setBusy(false);
		};
	}, [mode]);

	useEffect(() => {
		const paper = previewPaper.current;
		if (!paper) return;
		const updateLines = () => {
			const third = paper.querySelector<HTMLElement>('.presentFoldThird');
			if (third)
				paper.style.setProperty('--present-third-height', getComputedStyle(third).height);
		};
		const observer = new ResizeObserver(updateLines);
		observer.observe(paper);
		updateLines();
		return () => {
			observer.disconnect();
		};
	}, [loaded]);

	useEffect(() => {
		let active = true;
		let failed = false;
		const refresh = () =>
			void api<PresentsState>('/api/presents')
				.then((next) => {
					if (active && !sendingInProgress.current) {
						failed = false;
						applyState(next);
					}
				})
				.catch((cause: unknown) => {
					if (active && !failed) {
						failed = true;
						alertError(cause, 'Could not load letters.');
					}
				});
		refresh();
		const timer = window.setInterval(refresh, 30_000);
		return () => {
			active = false;
			window.clearInterval(timer);
		};
	}, [applyState]);

	const recipientPlayer = state?.players.find(
		(player) => player.minecraftUsername.toLowerCase() === recipient.trim().toLowerCase(),
	);
	const selectedPresent = state?.incoming.find((present) => present.id === selectedId);
	useEffect(() => {
		if (mode !== 'reading' || !writerReady || !selectedPresent || selectedPresent.read) return;
		if (readRequests.current.has(selectedPresent.id)) return;
		const id = selectedPresent.id;
		readRequests.current.add(id);
		void api<{ present: PresentView }>(`/api/presents/${id}/read`, 'POST')
			.then(({ present }) => {
				setState((current) =>
					current
						? {
								...current,
								incoming: current.incoming.map((item) =>
									item.id === id ? present : item,
								),
							}
						: current,
				);
			})
			.catch((cause: unknown) => {
				readRequests.current.delete(id);
				alertError(cause, 'Could not mark the letter as read.');
			});
	}, [mode, writerReady, selectedPresent]);
	const validRecipient = !!recipientPlayer;
	const alreadySentToday =
		!!recipientPlayer && !!state?.sentTodayRecipientIds.includes(recipientPlayer.id);
	const hasContent = !!body.trim() || !!state?.draft?.itemName;
	const hasDraft = !!state?.draft;
	const draftInput = { recipient, title, body, color, anonymous };
	const queueSave = useCallback((input: DraftInput) => {
		const task = saveQueue.current.then(() =>
			api<PresentsState>('/api/presents/draft', 'PATCH', input),
		);
		saveQueue.current = task.then(
			() => undefined,
			() => undefined,
		);
		return task;
	}, []);

	useEffect(() => {
		if (mode !== 'writing' || !state?.clock.sending || (recipient && !validRecipient) || busy)
			return;
		if (!hasDraft && !recipient && !title && !body && color === 'red' && !anonymous) return;
		const timer = window.setTimeout(() => {
			void queueSave({
				recipient,
				title,
				body,
				color,
				anonymous,
			})
				.then(applyState)
				.catch((cause: unknown) => {
					alertError(cause, 'Could not save the letter.');
				});
		}, 600);
		return () => {
			window.clearTimeout(timer);
		};
	}, [
		recipient,
		title,
		body,
		color,
		anonymous,
		validRecipient,
		state?.clock.sending,
		hasDraft,
		busy,
		mode,
		applyState,
		queueSave,
	]);

	async function save() {
		return queueSave(draftInput);
	}

	async function run(action: () => Promise<PresentsState>) {
		if (busy) return;
		setBusy(true);
		try {
			applyState(await action());
		} catch (cause) {
			alertError(cause, 'The letter could not be updated.');
		} finally {
			setBusy(false);
		}
	}

	function putDownLetter() {
		if (
			sending ||
			sendingInProgress.current ||
			(busy &&
				liftAnimation.current?.playState !== 'running' &&
				insertionAnimation.current?.playState !== 'running')
		)
			return;
		liftAnimation.current?.cancel();
		insertionAnimation.current?.cancel();
		setError('');
		setBusy(false);
		setMode('list');
		setSelectedId('');
		setFlipped(false);
	}

	async function liftPaper(nextMode: 'writing' | 'reading') {
		if (busy) return;
		setError('');
		if (nextMode === 'writing') setPage(0);
		setRevealing(false);
		setWriterReady(false);
		previewRect.current = null;
		const paper = previewPaper.current;
		if (!paper || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			setMode(nextMode);
			return;
		}
		const currentTransform = getComputedStyle(paper).transform;
		liftDistance.current = paperLift(paper);
		setBusy(true);
		const animation = paper.animate(
			[
				{ transform: currentTransform },
				{ transform: `translateY(-${liftDistance.current}px)` },
			],
			{ duration: 550, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'forwards' },
		);
		liftAnimation.current = animation;
		try {
			await animation.finished;
			previewRect.current = paper.getBoundingClientRect();
			setMode(nextMode);
		} catch {
			// The letter was put down before the lift finished.
		} finally {
			setBusy(false);
		}
	}

	async function finishPaper(saveDraft: boolean) {
		if (busy) return;
		if (saveDraft && recipient.trim() && !validRecipient) {
			setError('Choose a recipient from the list, or clear the field for now.');
			return;
		}
		setBusy(true);
		setError('');
		let inserting = false;
		try {
			if (saveDraft) applyState(await save());
			writingAnimation.current?.finish();
			setWriterReady(false);
			if (
				writingPaper.current &&
				previewPaper.current &&
				!window.matchMedia('(prefers-reduced-motion: reduce)').matches
			) {
				await new Promise<void>((resolve) => window.setTimeout(resolve, 280));
				const paper = writingPaper.current;
				const resting = restingPaperRect(previewPaper.current);
				liftDistance.current = paperLift(previewPaper.current);
				const raised = new DOMRect(
					resting.x,
					resting.y - liftDistance.current,
					resting.width,
					resting.height,
				);
				const transition = paper.animate(
					[
						{ transform: 'none' },
						{ transform: paperTransform(raised, paper.getBoundingClientRect()) },
					],
					{ duration: 950, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'forwards' },
				);
				await transition.finished;
				returningPaper.current = true;
				inserting = true;
				setRevealing(true);
			}
			setMode(saveDraft ? 'envelope' : 'received-envelope');
		} catch (cause) {
			setWriterReady(true);
			setError(cause instanceof Error ? cause.message : 'Could not save the letter.');
		} finally {
			if (!inserting) setBusy(false);
		}
	}

	function finishWriting() {
		return finishPaper(true);
	}

	function finishReading() {
		return finishPaper(false);
	}

	async function send() {
		if (busy) return;
		if (!hasContent) {
			window.alert('Write a message or add a gift before sending your letter.');
			return;
		}
		if (!validRecipient) {
			window.alert('Choose a recipient before sending your letter.');
			return;
		}
		if (alreadySentToday) {
			window.alert(
				`You already sent a letter to ${recipient} today. You can send one per player per day.`,
			);
			return;
		}
		setBusy(true);
		setError('');
		try {
			await save();
			sendingInProgress.current = true;
			const next = await api<PresentsState>('/api/presents/draft/send', 'POST');
			setSending(true);
			window.setTimeout(
				() => {
					sendingInProgress.current = false;
					setGlowReturning(color);
					applyState(next);
					setSending(false);
					setMode('list');
					setBusy(false);
					window.setTimeout(() => {
						setGlowReturning(null);
					}, 1400);
				},
				window.matchMedia('(prefers-reduced-motion: reduce)').matches
					? 250
					: (flightDelay + 1.5) * 1000,
			);
		} catch (cause) {
			sendingInProgress.current = false;
			alertError(cause, 'Could not send the letter.');
			setBusy(false);
		}
	}

	async function open(present: PresentView) {
		if (busy) return;
		setBusy(true);
		try {
			const result = await api<{ firstOpen: boolean; present: PresentView }>(
				`/api/presents/${present.id}/open`,
				'POST',
			);
			if (result.firstOpen) setOpening(present.id);
			setState((current) =>
				current
					? {
							...current,
							incoming: current.incoming.map((item) =>
								item.id === present.id ? result.present : item,
							),
						}
					: current,
			);
			if (result.firstOpen) {
				window.setTimeout(
					() => {
						setOpening('');
						setBusy(false);
					},
					window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 50 : 6_900,
				);
			} else setBusy(false);
		} catch (cause) {
			alertError(cause, 'Could not open the letter.');
			setBusy(false);
		}
	}

	if (!state)
		return (
			<section className="presentsPage" style={letterStyle(color)}>
				<button type="button" disabled>
					Write a letter
				</button>
			</section>
		);
	const displayName = (username: string | null) => {
		const player = state.players.find(
			(option) => option.minecraftUsername.toLowerCase() === username?.toLowerCase(),
		);
		const nickname = player?.nickname.trim();
		if (nickname) return nickname;
		return player?.minecraftUsername ?? username ?? 'a friend';
	};
	const receivingLetter =
		mode === 'received-envelope' || mode === 'reading' ? selectedPresent : undefined;
	const senderDisplay = displayName(senderName);
	const recipientDisplay = displayName(
		receivingLetter?.to ?? recipientPlayer?.minecraftUsername ?? null,
	);
	const fromDisplay = receivingLetter
		? receivingLetter.from
			? displayName(receivingLetter.from)
			: 'someone'
		: anonymous
			? 'someone'
			: senderDisplay;
	const addressLines = [`To ${recipientDisplay}`, `From ${fromDisplay}`];
	const addressCharacters = addressLines.reduce((count, line) => count + line.length, 0);
	const flightDelay = 8.3 + addressCharacters * 0.06 + 0.8;
	const previewTitle =
		receivingLetter?.title ??
		(title.trim() || (anonymous ? 'A Christmas letter' : 'Letter from ' + senderName));
	const previewBody = receivingLetter?.body ?? body;
	const hasPreview =
		!!receivingLetter || !!title.trim() || !!body.trim() || !!state.draft?.itemName;
	const previewByline = `By ${fromDisplay}`;
	const currentId = receivingLetter?.id ?? state.draft?.id ?? '';
	const isOpening = !!receivingLetter && opening === receivingLetter.id;
	const isSealed = !!receivingLetter && (!receivingLetter.opened || isOpening);
	const showingPaper = mode === 'writing' || mode === 'reading';
	return (
		<section
			className={'presentsPage' + (glowReturning ? ' isGlowReturning' : '')}
			style={
				{
					...letterStyle(color),
					'--glow-previous-color': glowReturning
						? LETTER_COLORS[glowReturning].value
						: undefined,
				} as CSSProperties
			}
		>
			<header className="presentsHero">
				<span className="presentsHeroIcon" aria-hidden="true">
					🎁
				</span>
				<p className="presentsEyebrow">Christmas post · 2026</p>
				<h1>
					{state.clock.receiving
						? 'Christmas presents are here!'
						: 'Send a little Christmas magic'}
				</h1>
				<p>
					{state.clock.receiving
						? 'Your letters have arrived. Open them to read your messages and find your gifts.'
						: 'Send letters to anyone on the server. They will be delivered on Christmas Day.'}
				</p>
			</header>
			{state.returned.length > 0 && (
				<div className="presentReturnedNotices" role="status">
					{state.returned.map((item) => (
						<p key={item.id}>An admin returned a letter to you: {item.reason}</p>
					))}
				</div>
			)}

			{state.clock.sending && (
				<div className="presentWriteEntry">
					<button
						type="button"
						onClick={() => {
							setError('');
							setMode('envelope');
						}}
					>
						Write a letter
					</button>
				</div>
			)}
			<dialog
				ref={dialog}
				className={'presentComposeDialog' + (sending ? ' isSending' : '')}
				style={
					{
						...letterStyle(receivingLetter?.color ?? color),
						'--present-flight-delay': `${flightDelay}s`,
					} as CSSProperties
				}
				aria-label={
					mode === 'writing'
						? 'Write your Christmas letter'
						: receivingLetter
							? 'Read your Christmas letter'
							: 'Your Christmas letter'
				}
				onCancel={(event) => {
					event.preventDefault();
					if (mode === 'writing') void finishWriting();
					else if (mode === 'reading') void finishReading();
					else putDownLetter();
				}}
				onKeyDownCapture={(event) => {
					if (event.key !== 'Escape') return;
					event.preventDefault();
					event.stopPropagation();
					if (mode === 'writing') void finishWriting();
					else if (mode === 'reading') void finishReading();
					else putDownLetter();
				}}
				onClick={(event) => {
					if (event.target !== event.currentTarget) return;
					if (mode === 'writing') void finishWriting();
					else if (mode === 'reading') void finishReading();
					else putDownLetter();
				}}
			>
				{mode === 'writing' && (
					<div
						className={'presentWritingView' + (writerReady ? ' isReady' : '')}
						onClick={(event) => {
							if (event.target === event.currentTarget) void finishWriting();
						}}
					>
						<div className="presentPaper presentWritingPaper" ref={writingPaper}>
							<div className="presentFormViewport" inert={!writerReady}>
								<div
									className="presentFormTrack"
									style={{ transform: `translateY(-${(page * 100) / 3}%)` }}
								>
									<section
										className="presentFormPage"
										aria-label="Recipient and colour"
										aria-hidden={page !== 0}
										inert={page !== 0}
									>
										<p className="presentsEyebrow">
											A letter from you · 1 of 3
										</p>
										<label>
											<span className="presentFieldHeading">Recipient</span>
											<PlayerSelector
												options={state.players}
												listboxId="present-recipients"
												value={recipient}
												onChange={setRecipient}
												placeholder="Choose a recipient"
												disabled={busy}
												required
											/>
										</label>
										<label className="presentsCheck">
											<input
												type="checkbox"
												checked={anonymous}
												onChange={(event) => {
													setAnonymous(event.target.checked);
												}}
												disabled={busy}
											/>{' '}
											Send anonymously
										</label>
										<fieldset className="presentColors">
											<legend>Letter colour</legend>
											<div className="presentColorOptions">
												{Object.entries(LETTER_COLORS).map(
													([key, option]) => (
														<button
															key={key}
															type="button"
															className="presentColorOption"
															style={
																{
																	'--swatch-color': option.value,
																} as CSSProperties
															}
															aria-label={option.label}
															aria-pressed={color === key}
															onClick={() => {
																setColor(key as LetterColor);
															}}
															disabled={busy}
														>
															<span aria-hidden="true" />
															{option.label}
														</button>
													),
												)}
											</div>
										</fieldset>
									</section>
									<section
										className="presentFormPage"
										aria-label="Letter message"
										aria-hidden={page !== 1}
										inert={page !== 1}
									>
										<p className="presentsEyebrow">Your words · 2 of 3</p>
										<label>
											<span className="presentFieldHeading">Title</span>
											<input
												value={title}
												maxLength={80}
												onChange={(event) => {
													setTitle(event.target.value);
												}}
												placeholder={
													anonymous
														? 'A Christmas letter'
														: 'Letter from you'
												}
												disabled={busy}
											/>
										</label>
										<label className="presentBodyLabel">
											<span className="presentFieldHeading">Message</span>
											<textarea
												value={body}
												maxLength={5000}
												onChange={(event) => {
													setBody(event.target.value);
												}}
												placeholder="Write your letter here..."
												disabled={busy}
											/>
										</label>
										{body.length === 5000 && (
											<p className="presentLimit" role="status">
												Your message has reached the 5000 character limit.
											</p>
										)}
									</section>
									<section
										className="presentFormPage"
										aria-label="Gift"
										aria-hidden={page !== 2}
										inert={page !== 2}
									>
										<p className="presentsEyebrow">Gift · 3 of 3</p>
										<h2>Add a gift</h2>
										<p>
											Hold it in your main hand in Minecraft, then click the
											“Add gift” button.
										</p>
										{state.draft?.itemName ? (
											<>
												<p>
													<strong>
														{state.draft.itemName} ×{' '}
														{state.draft.itemCount}
													</strong>{' '}
													is in your letter.
												</p>
												<button
													type="button"
													disabled={busy}
													onClick={() =>
														void run(() =>
															api<PresentsState>(
																'/api/presents/draft/take-out',
																'POST',
															),
														)
													}
												>
													Remove gift
												</button>
											</>
										) : (
											<button
												type="button"
												disabled={busy}
												onClick={() =>
													void run(async () => {
														await save();
														return api<PresentsState>(
															'/api/presents/draft/capture',
															'POST',
														);
													})
												}
											>
												Add gift
											</button>
										)}
									</section>
								</div>
							</div>
						</div>
						{error && (
							<p className="presentComposeError" role="alert">
								{error}
							</p>
						)}
						<div className="presentWritingControls" inert={!writerReady}>
							<div className="presentPageControls">
								<button
									type="button"
									disabled={busy || page === 0}
									onClick={() => {
										setPage((current) => Math.max(0, current - 1));
									}}
								>
									Previous
								</button>
								<span aria-live="polite">{page + 1} / 3</span>
								<button
									type="button"
									disabled={busy || page === 2}
									onClick={() => {
										setPage((current) => Math.min(2, current + 1));
									}}
								>
									Next
								</button>
							</div>
							<button
								className="presentFinishWriting"
								type="button"
								disabled={busy}
								onClick={() => void finishWriting()}
							>
								Finish writing
							</button>
						</div>
					</div>
				)}
				{mode === 'reading' && receivingLetter && (
					<div
						className={'presentWritingView' + (writerReady ? ' isReady' : '')}
						onClick={(event) => {
							if (event.target === event.currentTarget) void finishReading();
						}}
					>
						<div
							className="presentPaper presentWritingPaper presentReadingPaper"
							ref={writingPaper}
						>
							<div className="presentReadContent" inert={!writerReady} tabIndex={0}>
								<h2>{receivingLetter.title}</h2>
								<p className="presentReadByline">By {fromDisplay}</p>
								{receivingLetter.body && (
									<p className="presentReadBody">{receivingLetter.body}</p>
								)}
								{receivingLetter.hasGift && (
									<section className="presentReadGift">
										<h3>Receive your gift</h3>
										{receivingLetter.status === 'claimed' ? (
											<p>Gift received in Minecraft</p>
										) : (
											<>
												<p>
													Join the Minecraft server, then receive the
													stack here.
												</p>
												<button
													type="button"
													disabled={
														busy ||
														receivingLetter.status === 'claiming'
													}
													onClick={() =>
														void run(() =>
															api<PresentsState>(
																`/api/presents/${receivingLetter.id}/claim`,
																'POST',
															),
														)
													}
												>
													{receivingLetter.status === 'claiming'
														? 'Receiving gift...'
														: 'Receive gift'}
												</button>
											</>
										)}
									</section>
								)}
							</div>
						</div>
						<div className="presentWritingControls" inert={!writerReady}>
							<button
								className="presentFinishWriting"
								type="button"
								disabled={busy}
								onClick={() => void finishReading()}
							>
								Finish reading
							</button>
						</div>
					</div>
				)}
				<div
					className={
						'presentEnvelopeView' +
						(sending ? ' isSending' : '') +
						(showingPaper ? ' isWriting' : '') +
						(receivingLetter ? ' isReceived' : '') +
						(isOpening ? ' isOpening' : '') +
						(revealing ? ' isRevealing' : '')
					}
					inert={showingPaper}
					aria-hidden={showingPaper}
					onClick={(event) => {
						if (event.target === event.currentTarget) putDownLetter();
					}}
				>
					<button
						type="button"
						className="presentEnvelopeAction"
						disabled={busy || isSealed}
						onClick={() => void liftPaper(receivingLetter ? 'reading' : 'writing')}
						aria-label={
							receivingLetter ? 'Read the letter' : 'Open the letter to write'
						}
					>
						<span
							className={
								'presentEnvelopeObject' +
								(sending ? ' isSending' : '') +
								(receivingLetter ? ' isReceived' : '') +
								(isSealed ? ' isSealed' : ' isOpened') +
								(flipped ? ' isFlipped' : '') +
								(isOpening ? ' isOpening' : '')
							}
							aria-hidden="true"
						>
							<span className="presentEnvelopeRotor">
								<span className="presentEnvelopeReverse">
									<span className="presentEnvelopeFlap" />
									<span className="presentEnvelopeLining" />
									<span className="presentEnvelopeLetter" ref={previewPaper}>
										<span className="presentFoldThird presentFoldTop">
											<span className="presentFoldFace">
												<strong>{hasPreview ? previewTitle : ''}</strong>
												{hasPreview && <small>{previewByline}</small>}
											</span>
											<span className="presentFoldBack" />
										</span>
										<span className="presentFoldThird presentFoldMiddle">
											<span className="presentFoldFace">
												<span className="presentFoldText">
													{previewBody.trim()}
												</span>
											</span>
										</span>
										<span className="presentFoldThird presentFoldBottom">
											<span className="presentFoldFace" />
											<span className="presentFoldBack" />
										</span>
									</span>
									<span className="presentEnvelopePocket" />
									<span className="presentWaxSeal" style={sealAngle(currentId)} />
								</span>
								<span className="presentEnvelopeAddressFace">
									<span
										className="presentFrontWax"
										style={sealAngle(currentId)}
									/>
									<span className="presentPostage" style={stampAngle(currentId)}>
										<Image
											src={stampFor(currentId)}
											alt=""
											width={19}
											height={15}
											unoptimized
											loading="eager"
										/>
									</span>
									<span className="presentAddress">
										{addressLines.map((line, lineIndex) => (
											<span className="presentAddressLine" key={lineIndex}>
												{Array.from(line).map((char, charIndex) => (
													<span
														key={charIndex}
														style={{
															animationDelay: `${8.3 + (addressLines.slice(0, lineIndex).reduce((count, previous) => count + previous.length, 0) + charIndex) * 0.06}s`,
														}}
													>
														{char === ' ' ? '\u00a0' : char}
													</span>
												))}
											</span>
										))}
									</span>
								</span>
							</span>
						</span>
					</button>
					<div
						className="presentEnvelopeControls"
						inert={showingPaper}
						aria-hidden={showingPaper}
					>
						<button type="button" disabled={busy} onClick={putDownLetter}>
							Put down letter
						</button>
						{receivingLetter ? (
							isSealed && (
								<button
									type="button"
									disabled={busy}
									onClick={() => {
										setFlipped((value) => !value);
									}}
								>
									{flipped ? 'Flip back' : 'Flip around'}
								</button>
							)
						) : (
							<button
								className="presentSendButton"
								type="button"
								data-inactive={!hasContent || !validRecipient || alreadySentToday}
								title={
									alreadySentToday
										? 'You already sent this player a letter today.'
										: undefined
								}
								disabled={busy}
								onClick={() => void send()}
							>
								Send letter
							</button>
						)}
					</div>
					{receivingLetter && (
						<div
							className="presentOpenLetterRow"
							inert={showingPaper}
							aria-hidden={showingPaper}
						>
							{isSealed && flipped && (
								<button
									type="button"
									disabled={busy}
									onClick={() => void open(receivingLetter)}
								>
									Open letter
								</button>
							)}
						</div>
					)}
				</div>
			</dialog>
			{state.incoming.length > 0 && (
				<section className="presentsList presentIncomingList">
					<h2>Your Christmas post</h2>
					<ul className="presentSentGrid">
						{state.incoming.map((present) => (
							<li key={present.id}>
								<button
									type="button"
									className="presentSentEnvelope presentIncomingEnvelope"
									style={letterStyle(present.color)}
									aria-label={`Letter from ${present.from ? displayName(present.from) : 'someone'}${!present.read ? ', unread' : present.hasGift && present.status !== 'claimed' ? ', gift waiting' : ''}`}
									onClick={() => {
										setSelectedId(present.id);
										setFlipped(false);
										setRevealing(false);
										setMode('received-envelope');
									}}
								>
									<span
										className="presentSentStamp"
										style={stampAngle(present.id)}
										aria-hidden="true"
									>
										<Image
											src={stampFor(present.id)}
											alt=""
											width={19}
											height={15}
											unoptimized
										/>
									</span>
									<span
										className="presentSentWax"
										style={sealAngle(present.id)}
										aria-hidden="true"
									/>
									<span className="presentSentAddress">
										<span>To {displayName(present.to)}</span>
										<span>
											From{' '}
											{present.from ? displayName(present.from) : 'someone'}
										</span>
									</span>
									{(!present.read ||
										(present.hasGift && present.status !== 'claimed')) && (
										<span className="presentUnreadBadge" aria-hidden="true">
											!
										</span>
									)}
								</button>
							</li>
						))}
					</ul>
				</section>
			)}
			{state.sent.length > 0 && (
				<section className="presentsList presentSentList">
					<h2>Letters you sent</h2>
					<ul className="presentSentGrid">
						{state.sent.map((present) => (
							<li
								className="presentSentEnvelope"
								key={present.id}
								style={letterStyle(present.color)}
								aria-label={`${present.title}, to ${displayName(present.to)}, from ${present.anonymous ? 'someone' : displayName(present.from)}, ${present.status}`}
							>
								<span
									className="presentSentStamp"
									style={stampAngle(present.id)}
									aria-hidden="true"
								>
									<Image
										src={stampFor(present.id)}
										alt=""
										width={19}
										height={15}
										unoptimized
									/>
								</span>
								<span
									className="presentSentWax"
									style={sealAngle(present.id)}
									aria-hidden="true"
								/>
								<span className="presentSentAddress">
									<span>To {displayName(present.to)}</span>
									<span>
										From{' '}
										{present.anonymous ? 'someone' : displayName(present.from)}
									</span>
								</span>
								{['returned', 'deleted'].includes(present.status) && (
									<span className="presentSentStatus">
										{present.status === 'returned'
											? 'Returned'
											: 'Held by admins'}
									</span>
								)}
							</li>
						))}
					</ul>
				</section>
			)}
		</section>
	);
}
