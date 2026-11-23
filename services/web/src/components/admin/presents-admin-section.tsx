'use client';

import { useEffect, useState } from 'react';
import type { PresentView } from '../presents-tab';

interface AdminPresent extends PresentView {
	adminCopyAt: number | null;
}
interface AdminState {
	clock: { now: number; simulatedAt: number | null };
	presents: AdminPresent[];
}

async function request(path: string, method = 'GET', body?: object) {
	const response = await fetch(path, {
		method,
		cache: 'no-store',
		headers: body ? { 'content-type': 'application/json' } : undefined,
		body: body ? JSON.stringify(body) : undefined,
	});
	const result = (await response.json().catch(() => ({}))) as AdminState & { message?: string };
	if (!response.ok) throw new Error(result.message ?? 'Could not update presents.');
	return result;
}

export function PresentsAdminSection() {
	const [data, setData] = useState<AdminState | null>(null);
	const [dateTime, setDateTime] = useState('');
	const [from, setFrom] = useState('');
	const [to, setTo] = useState('');
	const [query, setQuery] = useState('');
	const [returnId, setReturnId] = useState('');
	const [reason, setReason] = useState('');
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');

	useEffect(() => {
		let active = true;
		void request('/api/admin/presents')
			.then((result) => {
				if (active) {
					setData(result);
					setDateTime(
						new Date(result.clock.simulatedAt ?? result.clock.now)
							.toISOString()
							.slice(0, 16),
					);
				}
			})
			.catch((cause: unknown) => {
				if (active)
					setError(cause instanceof Error ? cause.message : 'Could not load presents.');
			});
		return () => {
			active = false;
		};
	}, []);

	async function change(path: string, method: string, body: object | undefined, success: string) {
		if (busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const result = await request(path, method, body);
			const next = path.endsWith('/clock') ? await request('/api/admin/presents') : result;
			setData(next);
			if (path.endsWith('/clock'))
				setDateTime(
					new Date(next.clock.simulatedAt ?? next.clock.now).toISOString().slice(0, 16),
				);
			setNotice(success);
			setReturnId('');
			setReason('');
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not update presents.');
		} finally {
			setBusy(false);
		}
	}

	const rows =
		data?.presents.filter(
			(present) =>
				present.from?.toLowerCase().includes(from.trim().toLowerCase()) &&
				present.to?.toLowerCase().includes(to.trim().toLowerCase()) &&
				`${present.title} ${present.itemName} ${present.status}`
					.toLowerCase()
					.includes(query.trim().toLowerCase()),
		) ?? [];
	return (
		<section className="adminSection presentsPage">
			<div className="adminSectionHeader">
				<h3>Christmas presents</h3>
				<p>Every sent letter remains in this log.</p>
			</div>
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
			<div className="presentAdminClock">
				<h4>Test the presents date</h4>
				<p>
					This date and time affects everyone on the site. Times are in London. Sending
					closes and receiving opens at 9am on 25 December.
				</p>
				<p>
					Present actions at a test time are real. Restoring the clock does not undo them.
				</p>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						void change(
							'/api/admin/presents/clock',
							'PUT',
							{ simulatedAt: dateTime },
							'The test date is active.',
						);
					}}
				>
					<label>
						Date and time{' '}
						<input
							type="datetime-local"
							value={dateTime}
							min="2026-11-01T00:00"
							max="2026-12-31T23:59"
							onChange={(event) => {
								setDateTime(event.target.value);
							}}
							required
						/>
					</label>
					<button disabled={busy} type="submit">
						Use this date
					</button>
					<button
						disabled={busy || data?.clock.simulatedAt === null}
						type="button"
						onClick={() =>
							void change(
								'/api/admin/presents/clock',
								'PUT',
								{ simulatedAt: null },
								'London time restored.',
							)
						}
					>
						Use real London time
					</button>
				</form>
				<p>
					{data?.clock.simulatedAt === null
						? 'Real London time is active.'
						: 'The simulated date is active.'}
				</p>
			</div>
			<div className="presentAdminSearch">
				<label>
					Search{' '}
					<input
						value={query}
						onChange={(event) => {
							setQuery(event.target.value);
						}}
						placeholder="Title, item or status"
					/>
				</label>
				<label>
					From{' '}
					<input
						value={from}
						onChange={(event) => {
							setFrom(event.target.value);
						}}
						placeholder="Sender"
					/>
				</label>
				<label>
					To{' '}
					<input
						value={to}
						onChange={(event) => {
							setTo(event.target.value);
						}}
						placeholder="Recipient"
					/>
				</label>
			</div>
			<p>{rows.length} letters</p>
			{rows.map((present) => (
				<article className="presentAdminRow" key={present.id}>
					<strong>{present.title}</strong>
					<p>
						From {present.from} to {present.to}
						{present.anonymous ? ' · anonymous to the recipient' : ''}
					</p>
					<p>
						{present.itemName} × {present.itemCount} · {present.status}
					</p>
					{present.adminReason && <p>Return reason: {present.adminReason}</p>}
					<div className="presentAdminActions">
						<button
							type="button"
							disabled={busy || !!present.adminCopyAt}
							onClick={() =>
								void change(
									`/api/admin/presents/${present.id}/copy`,
									'POST',
									undefined,
									'MMU admin received a copy.',
								)
							}
						>
							Gift to MMU admin
						</button>
						{present.status === 'sent' && (
							<button
								type="button"
								disabled={busy}
								onClick={() =>
									void change(
										`/api/admin/presents/${present.id}/delete`,
										'POST',
										undefined,
										'The letter is hidden from its recipient.',
									)
								}
							>
								Delete delivery
							</button>
						)}
						{present.status === 'deleted' && (
							<button
								type="button"
								disabled={busy}
								onClick={() =>
									void change(
										`/api/admin/presents/${present.id}/undo`,
										'POST',
										undefined,
										'The letter can be delivered again.',
									)
								}
							>
								Undo deletion
							</button>
						)}
						{['sent', 'deleted'].includes(present.status) && (
							<button
								type="button"
								disabled={busy}
								onClick={() => {
									setReturnId(present.id);
									setReason('');
								}}
							>
								Return to sender
							</button>
						)}
					</div>
					{returnId === present.id && (
						<form
							onSubmit={(event) => {
								event.preventDefault();
								void change(
									`/api/admin/presents/${present.id}/return`,
									'POST',
									{ reason },
									'The present is being returned.',
								);
							}}
						>
							<label>
								Reason for sender{' '}
								<input
									value={reason}
									onChange={(event) => {
										setReason(event.target.value);
									}}
									maxLength={300}
									required
								/>
							</label>
							<button type="submit" disabled={busy}>
								Confirm return
							</button>
							<button
								type="button"
								onClick={() => {
									setReturnId('');
								}}
							>
								Cancel
							</button>
						</form>
					)}
				</article>
			))}
		</section>
	);
}
