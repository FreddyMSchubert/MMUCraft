'use client';

import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';
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
	players: { id: number; minecraftUsername: string; color: string }[];
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

export function PresentsTab() {
	const [state, setState] = useState<PresentsState | null>(null);
	const [recipient, setRecipient] = useState('');
	const [title, setTitle] = useState('');
	const [body, setBody] = useState('');
	const [color, setColor] = useState<LetterColor>('red');
	const [anonymous, setAnonymous] = useState(false);
	const [page, setPage] = useState(0);
	const [closed, setClosed] = useState(false);
	const [sending, setSending] = useState(false);
	const [opening, setOpening] = useState('');
	const [busy, setBusy] = useState(false);
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
			setClosed(false);
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
		let active = true;
		let failed = false;
		const refresh = () =>
			void api<PresentsState>('/api/presents')
				.then((next) => {
					if (active) {
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
		if (!state?.clock.sending || (recipient && !validRecipient) || busy || closed) return;
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
		closed,
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

	async function closeLetter() {
		if (busy) return;
		if (!validRecipient) {
			window.alert(
				recipient.trim()
					? 'Choose a recipient from the list.'
					: 'Choose a recipient first.',
			);
			return;
		}
		if (!hasContent) {
			window.alert('Write a message or add a gift before closing the letter.');
			return;
		}
		setBusy(true);
		try {
			applyState(await save());
			setClosed(true);
		} catch (cause) {
			alertError(cause, 'Could not save the letter.');
		} finally {
			setBusy(false);
		}
	}

	async function send() {
		if (busy) return;
		setBusy(true);
		try {
			await save();
			const next = await api<PresentsState>('/api/presents/draft/send', 'POST');
			setSending(true);
			window.setTimeout(() => {
				applyState(next);
				setSending(false);
				setClosed(false);
				setBusy(false);
			}, 750);
		} catch (cause) {
			alertError(cause, 'Could not send the letter.');
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
				<p>Loading your letters...</p>
			</section>
		);
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

			{state.clock.sending && (
				<div className="presentsCompose">
					<div
						className={`presentEnvelope ${closed ? 'presentEnvelopeClosed' : ''} ${sending ? 'presentEnvelopeSending' : ''}`}
					>
						<div className="presentEnvelopeFlap" aria-hidden="true" />
						<div className="presentPaper">
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
												datalistId="present-recipients"
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
					</div>
					<div className="presentsSendActions">
						{closed ? (
							<>
								<button
									className="presentPrevious"
									type="button"
									onClick={() => {
										setClosed(false);
									}}
									disabled={busy}
								>
									Reopen and edit
								</button>
								<button type="button" onClick={() => void send()} disabled={busy}>
									Stamp and send
								</button>
							</>
						) : (
							<>
								<button
									className="presentPrevious"
									type="button"
									onClick={() => {
										setPage((current) => Math.max(0, current - 1));
									}}
									disabled={busy || page === 0}
								>
									Previous
								</button>
								<span aria-live="polite">{page + 1} / 3</span>
								{page < 2 ? (
									<button
										type="button"
										onClick={() => {
											setPage((current) => Math.min(2, current + 1));
										}}
										disabled={busy || (page === 0 && !validRecipient)}
									>
										Next
									</button>
								) : (
									<button
										type="button"
										onClick={() => void closeLetter()}
										disabled={busy}
									>
										Close letter
									</button>
								)}
							</>
						)}
					</div>
				</div>
			)}

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

			{state.sent.length > 0 && (
				<section className="presentsList">
					<h2>Letters you sent</h2>
					<div className="presentSentList">
						{state.sent.map((present) => (
							<div key={present.id} style={letterStyle(present.color)}>
								<strong>{present.title}</strong>
								<span>To {present.to}</span>
								{present.adminReason && (
									<small>Returned by an admin: {present.adminReason}</small>
								)}
							</div>
						))}
					</div>
				</section>
			)}
		</section>
	);
}
