'use client';

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
	red: { label: 'Red', value: '#a82f3d' },
	green: { label: 'Green', value: '#286b51' },
	yellow: { label: 'Yellow', value: '#d9a83b' },
	orange: { label: 'Orange', value: '#c56c32' },
	blue: { label: 'Blue', value: '#345b95' },
	'light-blue': { label: 'Light blue', value: '#5a9fba' },
	purple: { label: 'Purple', value: '#704b8b' },
	pink: { label: 'Pink', value: '#c16b8f' },
} as const;
type LetterColor = keyof typeof LETTER_COLORS;
const letterStyle = (color: string): CSSProperties =>
	({
		'--letter-color':
			color in LETTER_COLORS
				? LETTER_COLORS[color as LetterColor].value
				: LETTER_COLORS.red.value,
	}) as CSSProperties;

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
	status: string;
	opened: boolean;
	sentAt: number | null;
	claimedAt: number | null;
	adminReason: string | null;
}

interface PresentsState {
	clock: { now: number; simulatedAt: number | null; sending: boolean; receiving: boolean };
	draft: PresentView | null;
	incoming: PresentView[];
	sent: PresentView[];
	returned: { id: string; reason: string }[];
	pendingReturns: { id: string; itemName: string | null }[];
	players: { id: number; minecraftUsername: string; nickname: string; color: string }[];
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

export function PresentsTab({ senderName }: { senderName: string }) {
	const [state, setState] = useState<PresentsState | null>(null);
	const [recipient, setRecipient] = useState('');
	const [title, setTitle] = useState('');
	const [body, setBody] = useState('');
	const [color, setColor] = useState<LetterColor>('red');
	const [anonymous, setAnonymous] = useState(false);
	const [page, setPage] = useState(0);
	const [mode, setMode] = useState<'list' | 'envelope' | 'writing'>('list');
	const [sending, setSending] = useState(false);
	const [opening, setOpening] = useState('');
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const dialog = useRef<HTMLDialogElement>(null);
	const previewPaper = useRef<HTMLSpanElement>(null);
	const writingPaper = useRef<HTMLDivElement>(null);
	const previewRect = useRef<DOMRect | null>(null);
	const writingAnimation = useRef<Animation | null>(null);
	const sendingInProgress = useRef(false);
	const draftId = useRef<string | null>(null);
	const initialized = useRef(false);
	const seenReturns = useRef(new Set<string>());
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
		if (mode !== 'writing' || !previewRect.current || !writingPaper.current) return;
		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
		const paper = writingPaper.current;
		writingAnimation.current = paper.animate(
			[
				{ transform: paperTransform(previewRect.current, paper.getBoundingClientRect()) },
				{ transform: 'none' },
			],
			{ duration: 1150, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' },
		);
	}, [mode]);

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

	const validRecipient = !!state?.players.some(
		(player) => player.minecraftUsername.toLowerCase() === recipient.trim().toLowerCase(),
	);
	const hasContent = !!body.trim() || !!state?.draft?.itemName;
	const hasDraft = !!state?.draft;
	const draftInput = { recipient, title, body, color, anonymous };

	useEffect(() => {
		if (mode !== 'writing' || !state?.clock.sending || (recipient && !validRecipient) || busy)
			return;
		if (!hasDraft && !recipient && !title && !body && color === 'red' && !anonymous) return;
		const timer = window.setTimeout(() => {
			void api<PresentsState>('/api/presents/draft', 'PATCH', {
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
	]);

	async function save() {
		return api<PresentsState>('/api/presents/draft', 'PATCH', draftInput);
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

	async function finishWriting() {
		if (busy) return;
		if (recipient.trim() && !validRecipient) {
			setError('Choose a recipient from the list, or clear the field for now.');
			return;
		}
		setBusy(true);
		setError('');
		try {
			applyState(await save());
			writingAnimation.current?.finish();
			if (
				writingPaper.current &&
				previewPaper.current &&
				!window.matchMedia('(prefers-reduced-motion: reduce)').matches
			) {
				const paper = writingPaper.current;
				paper.parentElement
					?.querySelector('.presentWritingControls')
					?.animate([{ opacity: 1 }, { opacity: 0 }], {
						duration: 300,
						fill: 'forwards',
					});
				const transition = paper.animate(
					[
						{ transform: 'none' },
						{
							transform: paperTransform(
								previewPaper.current.getBoundingClientRect(),
								paper.getBoundingClientRect(),
							),
						},
					],
					{ duration: 1000, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'forwards' },
				);
				await transition.finished;
			}
			setMode('envelope');
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not save the letter.');
		} finally {
			setBusy(false);
		}
	}

	async function send() {
		if (busy) return;
		if (!hasContent) {
			setError('Write a message or add a gift before sending your letter.');
			return;
		}
		if (!validRecipient) {
			setError('Choose a recipient before sending your letter.');
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
					applyState(next);
					setSending(false);
					setMode('list');
					setBusy(false);
				},
				window.matchMedia('(prefers-reduced-motion: reduce)').matches
					? 250
					: (flightDelay + 1.5) * 1000,
			);
		} catch (cause) {
			sendingInProgress.current = false;
			setError(cause instanceof Error ? cause.message : 'Could not send the letter.');
			setBusy(false);
		}
	}

	async function open(present: PresentView) {
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
			if (result.firstOpen)
				window.setTimeout(() => {
					setOpening('');
				}, 800);
		} catch (cause) {
			alertError(cause, 'Could not open the letter.');
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
	const recipientPlayer = state.players.find(
		(player) => player.minecraftUsername.toLowerCase() === recipient.trim().toLowerCase(),
	);
	const addressLines = [
		'For',
		...(recipientPlayer?.nickname ? [recipientPlayer.nickname] : []),
		recipientPlayer?.minecraftUsername ?? (recipient || 'A friend'),
	];
	const addressCharacters = addressLines.reduce((count, line) => count + line.length, 0);
	const flightDelay = 8.3 + addressCharacters * 0.06 + 0.8;
	const previewTitle =
		title.trim() || (anonymous ? 'A Christmas letter' : 'Letter from ' + senderName);
	const hasPreview = Boolean(title.trim() || body.trim() || state.draft?.itemName);
	const previewByline = anonymous ? 'By someone' : 'By ' + senderName;
	return (
		<section className="presentsPage" style={letterStyle(color)}>
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
						...letterStyle(color),
						'--present-flight-delay': `${flightDelay}s`,
					} as CSSProperties
				}
				aria-label={
					mode === 'writing' ? 'Write your Christmas letter' : 'Your Christmas letter'
				}
				onCancel={(event) => {
					event.preventDefault();
					if (busy) return;
					if (mode === 'writing') void finishWriting();
					else setMode('list');
				}}
			>
				{mode === 'writing' && (
					<div className="presentWritingView">
						<div className="presentPaper presentWritingPaper" ref={writingPaper}>
							<div className="presentFormViewport">
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
						<div className="presentWritingControls">
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
				<div
					className={
						'presentEnvelopeView' +
						(sending ? ' isSending' : '') +
						(mode === 'writing' ? ' isWriting' : '')
					}
					inert={mode === 'writing'}
					aria-hidden={mode === 'writing'}
				>
					<button
						type="button"
						className="presentEnvelopeAction"
						disabled={busy}
						onClick={() => {
							setError('');
							previewRect.current =
								previewPaper.current?.getBoundingClientRect() ?? null;
							setPage(0);
							setMode('writing');
						}}
						aria-label="Open the letter to write"
					>
						<span
							className={'presentEnvelopeObject' + (sending ? ' isSending' : '')}
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
											<span className="presentFoldFace">{body.trim()}</span>
										</span>
										<span className="presentFoldThird presentFoldBottom">
											<span className="presentFoldFace" />
											<span className="presentFoldBack" />
										</span>
									</span>
									<span className="presentEnvelopePocket" />
									<span className="presentWaxSeal">✦</span>
								</span>
								<span className="presentEnvelopeAddressFace">
									<span className="presentPostage">
										<span>✦</span>
										<small>MMU POST</small>
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
					{error && mode !== 'writing' && (
						<p className="presentComposeError" role="alert">
							{error}
						</p>
					)}
					{mode !== 'writing' && (
						<div className="presentEnvelopeControls">
							<button
								type="button"
								disabled={busy}
								onClick={() => {
									setError('');
									setMode('list');
								}}
							>
								Put down letter
							</button>
							<button
								className="presentSendButton"
								type="button"
								disabled={busy}
								onClick={() => void send()}
							>
								Send letter
							</button>
						</div>
					)}
				</div>
			</dialog>

			{state.incoming.length > 0 && (
				<section className="presentsList">
					<h2>Your Christmas post</h2>
					<div className="presentCards">
						{state.incoming.map((present) => (
							<article
								className={`presentCard ${opening === present.id ? 'presentCardOpening' : ''}`}
								style={letterStyle(present.color)}
								key={present.id}
							>
								<div className="presentCardPaper">
									<span className="presentCardSeal" aria-hidden="true">
										✦
									</span>
									<h3>{present.title}</h3>
									<p>From {present.from ?? 'a secret sender'}</p>
									{present.opened && (
										<>
											{present.body && (
												<p className="presentCardBody">{present.body}</p>
											)}
											{present.itemName && (
												<p className="presentCardItem">
													{present.itemName} × {present.itemCount}
												</p>
											)}
										</>
									)}
								</div>
								{(!present.opened || present.itemName) && (
									<div className="presentCardActions">
										{!present.opened ? (
											<button
												type="button"
												onClick={() => void open(present)}
											>
												Open letter
											</button>
										) : (
											present.itemName &&
											(present.status === 'claimed' ? (
												<p>Gift received in Minecraft</p>
											) : (
												<button
													type="button"
													disabled={busy}
													onClick={() =>
														void run(() =>
															api<PresentsState>(
																`/api/presents/${present.id}/claim`,
																'POST',
															),
														)
													}
												>
													Receive gift
												</button>
											))
										)}
									</div>
								)}
							</article>
						))}
					</div>
				</section>
			)}
		</section>
	);
}
