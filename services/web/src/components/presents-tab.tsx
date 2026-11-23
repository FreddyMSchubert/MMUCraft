'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { PlayerSelector } from './player-selector';

export interface PresentView {
	id: string;
	from: string | null;
	to: string | null;
	title: string;
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
	remainingToday: number;
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

export function PresentsTab() {
	const [state, setState] = useState<PresentsState | null>(null);
	const [recipient, setRecipient] = useState('');
	const [title, setTitle] = useState('');
	const [anonymous, setAnonymous] = useState(false);
	const [preview, setPreview] = useState('');
	const [closed, setClosed] = useState(false);
	const [sending, setSending] = useState(false);
	const [opening, setOpening] = useState('');
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const draftId = useRef<string | null>(null);
	const applyState = useCallback((next: PresentsState) => {
		if ((next.draft?.id ?? null) !== draftId.current) {
			draftId.current = next.draft?.id ?? null;
			setRecipient(next.draft?.to ?? '');
			setTitle(next.draft?.title ?? '');
			setAnonymous(next.draft?.anonymous ?? false);
			setClosed(false);
		}
		setState(next);
	}, []);

	useEffect(() => {
		let active = true;
		const refresh = () =>
			void api<PresentsState>('/api/presents')
				.then((next) => {
					if (active) applyState(next);
				})
				.catch((cause: unknown) => {
					if (active)
						setError(
							cause instanceof Error ? cause.message : 'Could not load presents.',
						);
				});
		refresh();
		const timer = window.setInterval(refresh, 30_000);
		return () => {
			active = false;
			window.clearInterval(timer);
		};
	}, [applyState]);

	const validRecipient =
		!recipient ||
		!!state?.players.some(
			(player) => player.minecraftUsername.toLowerCase() === recipient.trim().toLowerCase(),
		);
	useEffect(() => {
		if (!state?.clock.sending || !validRecipient || busy || closed) return;
		const timer = window.setTimeout(() => {
			void api<PresentsState>('/api/presents/draft', 'PATCH', { recipient, title, anonymous })
				.then(applyState)
				.catch((cause: unknown) => {
					setError(cause instanceof Error ? cause.message : 'Could not save the letter.');
				});
		}, 600);
		return () => {
			window.clearTimeout(timer);
		};
	}, [
		recipient,
		title,
		anonymous,
		validRecipient,
		state?.clock.sending,
		state?.draft?.id,
		busy,
		closed,
		applyState,
	]);

	async function run(action: () => Promise<PresentsState>, success: string) {
		if (busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		try {
			applyState(await action());
			setNotice(success);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'The letter could not be updated.');
		} finally {
			setBusy(false);
		}
	}

	function save() {
		return api<PresentsState>('/api/presents/draft', 'PATCH', { recipient, title, anonymous });
	}

	async function capture() {
		await run(async () => {
			await save();
			return api<PresentsState>('/api/presents/draft/capture', 'POST');
		}, 'The full stack is safe inside your letter.');
		setPreview('');
	}

	async function closeLetter() {
		if (busy) return;
		setBusy(true);
		setError('');
		try {
			applyState(await save());
			setClosed(true);
			setNotice('Your letter is saved.');
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not save the letter.');
		} finally {
			setBusy(false);
		}
	}

	async function send() {
		if (busy) return;
		setBusy(true);
		setError('');
		try {
			const next = await api<PresentsState>('/api/presents/draft/send', 'POST');
			setSending(true);
			window.setTimeout(() => {
				applyState(next);
				setSending(false);
				setClosed(false);
				setNotice('Your letter is on its way.');
				setBusy(false);
			}, 750);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not send the letter.');
			setBusy(false);
		}
	}

	async function open(present: PresentView) {
		setError('');
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
			setError(cause instanceof Error ? cause.message : 'Could not open the letter.');
		}
	}

	if (!state)
		return (
			<section className="presentsPage">
				<p>Loading your letters...</p>
				{error && <p role="alert">{error}</p>}
			</section>
		);
	return (
		<section className="presentsPage">
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
						? 'Your letters have arrived. Open one, then join the Minecraft server to receive its stack.'
						: 'Pick a player, put one full stack in a letter, and send it for free. Letters arrive at 9am on Christmas Day.'}
				</p>
				{state.clock.simulatedAt !== null && (
					<p className="presentsTestClock">Admin test time is active for everyone.</p>
				)}
			</header>

			{error && (
				<p className="presentsError" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="presentsNotice" role="status">
					{notice}
				</p>
			)}
			{state.returned.map((item) => (
				<p className="presentsError" key={item.id}>
					An admin returned a present you sent: {item.reason}
				</p>
			))}
			{state.pendingReturns.map((item) => (
				<p className="presentsNotice" key={item.id}>
					{item.itemName ?? 'Your captured stack'} is waiting to be returned. Join the
					Minecraft server to receive it.
				</p>
			))}

			{state.clock.sending && (
				<div className="presentsCompose">
					<div
						className={`presentEnvelope ${closed ? 'presentEnvelopeClosed' : ''} ${sending ? 'presentEnvelopeSending' : ''}`}
					>
						<div className="presentEnvelopeFlap" aria-hidden="true" />
						<div className="presentPaper">
							<p className="presentsEyebrow">A letter from you</p>
							<label>
								To
								<PlayerSelector
									options={state.players}
									datalistId="present-recipients"
									value={recipient}
									onChange={setRecipient}
									placeholder="Choose a player"
									disabled={busy}
									required
								/>
							</label>
							<label>
								Title{' '}
								<span className="presentsOptional">optional, 80 characters</span>
								<input
									value={title}
									maxLength={80}
									onChange={(event) => {
										setTitle(event.target.value);
									}}
									placeholder={
										anonymous ? 'A Christmas letter' : 'Letter from you'
									}
									disabled={busy}
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
							<div className="presentItemSlot">
								<strong>One full stack per letter</strong>
								<p>
									{state.draft?.itemName
										? `${state.draft.itemName} × ${state.draft.itemCount} is inside the letter.`
										: preview ||
											'Hold the stack in your Minecraft main hand, then insert it here.'}
								</p>
								<div className="presentsActions">
									<button
										type="button"
										disabled={busy}
										onClick={() =>
											void api<{ itemName: string; itemCount: number }>(
												'/api/presents/preview',
											)
												.then((item) => {
													setPreview(
														`Currently holding ${item.itemName} × ${item.itemCount}.`,
													);
													setError('');
												})
												.catch((cause: unknown) => {
													setError(
														cause instanceof Error
															? cause.message
															: 'Could not check your hand.',
													);
												})
										}
									>
										Refresh main hand
									</button>
									{state.draft?.itemName ? (
										<button
											type="button"
											disabled={busy}
											onClick={() =>
												void run(
													() =>
														api<PresentsState>(
															'/api/presents/draft/take-out',
															'POST',
														),
													'The stack is back in your inventory.',
												)
											}
										>
											Take stack out
										</button>
									) : (
										<button
											type="button"
											disabled={busy}
											onClick={() => void capture()}
										>
											Insert full stack
										</button>
									)}
								</div>
							</div>
							<p className="presentsHint">
								Want to include a message? Put a signed book with your gift inside a
								shulker box or backpack, then send that container as your stack.
							</p>
							<button
								className="presentsSeal"
								type="button"
								disabled={busy || !state.draft?.itemName}
								onClick={() => void closeLetter()}
							>
								Close letter
							</button>
						</div>
					</div>
					{closed && (
						<div className="presentsSendActions">
							<button
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
						</div>
					)}
					<p className="presentsAllowance">
						{state.remainingToday} of 3 free letters left today. The limit resets at
						midnight London time.
					</p>
				</div>
			)}

			{state.incoming.length > 0 && (
				<section className="presentsList">
					<h2>Your Christmas post</h2>
					<div className="presentCards">
						{state.incoming.map((present) => (
							<article
								className={`presentCard ${opening === present.id ? 'presentCardOpening' : ''}`}
								key={present.id}
							>
								<span className="presentCardSeal" aria-hidden="true">
									✦
								</span>
								<h3>{present.title}</h3>
								<p>From {present.from ?? 'a secret sender'}</p>
								{!present.opened ? (
									<button type="button" onClick={() => void open(present)}>
										Open letter
									</button>
								) : (
									<>
										<p className="presentCardItem">
											{present.itemName} × {present.itemCount}
										</p>
										{present.status === 'claimed' ? (
											<p>Received in Minecraft</p>
										) : (
											<button
												type="button"
												disabled={busy}
												onClick={() =>
													void run(
														() =>
															api<PresentsState>(
																`/api/presents/${present.id}/claim`,
																'POST',
															),
														'Your stack has been delivered in Minecraft.',
													)
												}
											>
												Receive gift
											</button>
										)}
									</>
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
							<div key={present.id}>
								<strong>{present.title}</strong>
								<span>
									To {present.to} · {present.status}
								</span>
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
