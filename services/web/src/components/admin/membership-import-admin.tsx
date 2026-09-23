'use client';

import { useRef, useState } from 'react';
import { apiMessage, errorMessage } from './admin-api';

interface Row {
	email: string;
	discordName: string;
}
interface Result extends Row {
	minecraftUsername: string | null;
	userId: number | null;
	discordNameSource: string | null;
	discordLabel: string | null;
	apiStatus: string;
	discordStatus: string;
	apiError: string | null;
	discordError: string | null;
}

function parseCsv(text: string): string[][] {
	const rows: string[][] = [];
	let row: string[] = [];
	let field = '';
	let quoted = false;
	for (let i = 0; i < text.length; i++) {
		const char = text[i];
		if (char === '"') {
			if (quoted && text[i + 1] === '"') {
				field += '"';
				i++;
			} else quoted = !quoted;
		} else if (char === ',' && !quoted) {
			row.push(field);
			field = '';
		} else if ((char === '\n' || char === '\r') && !quoted) {
			if (char === '\r' && text[i + 1] === '\n') i++;
			row.push(field);
			field = '';
			if (row.some((value) => value.trim())) rows.push(row);
			row = [];
		} else field += char;
	}
	if (quoted) throw new Error('The CSV has an unclosed quoted field.');
	row.push(field);
	if (row.some((value) => value.trim())) rows.push(row);
	return rows;
}

function membershipRows(text: string) {
	const [headers, ...data] = parseCsv(text.replace(/^\uFEFF/, ''));
	if (headers.length === 0) throw new Error('The CSV is empty.');
	const column = (match: (name: string) => boolean) => headers.findIndex(match);
	const email = column((name) => name.trim().toLowerCase() === 'email');
	const discord = column((name) =>
		name.toLowerCase().replace(/\s+/g, '').includes('discordusername'),
	);
	const membership = column((name) => name.trim().toLowerCase() === 'membership status');
	const payment = column((name) => name.trim().toLowerCase() === 'payment status');
	const expiry = column((name) => name.trim().toLowerCase() === 'expiry date');
	if ([email, discord, membership, payment, expiry].some((index) => index < 0))
		throw new Error('This is not the expected society membership report CSV.');
	const today = new Date().toISOString().slice(0, 10);
	const selected = data.filter((fields) => {
		const date = /^(\d{2})-(\d{2})-(\d{4})$/.exec(fields[expiry]?.trim() ?? '');
		const iso = date ? `${date[3]}-${date[2]}-${date[1]}` : '';
		return (
			fields[membership]?.trim().toLowerCase() === 'approved' &&
			fields[payment]?.trim().toLowerCase() === 'payment processed' &&
			iso >= today
		);
	});
	return {
		rows: selected.map((fields) => ({
			email: fields[email]?.trim() ?? '',
			discordName: fields[discord]?.trim() ?? '',
		})),
		excluded: data.length - selected.length,
	};
}

function statusReason(status: string, error: string | null) {
	if (status === 'ready' || status === 'pending') return 'Pending';
	if (status === 'updated' || status === 'role added') return 'Applied';
	if (status === 'already has role') return 'Already has role';
	if (status === 'no account for email') return 'No API account for this email';
	if (status === 'no server member found')
		return 'No member with this username is in the Discord server';
	return error ? `${status}: ${error}` : status;
}

export function MembershipImportAdmin({ onApplied }: { onApplied: () => Promise<void> }) {
	const run = useRef(0);
	const controller = useRef<AbortController | null>(null);
	const [results, setResults] = useState<Result[]>([]);
	const [existingIndices, setExistingIndices] = useState<Set<number>>(new Set());
	const [total, setTotal] = useState(0);
	const [excluded, setExcluded] = useState(0);
	const [busy, setBusy] = useState(false);
	const [remaining, setRemaining] = useState(0);
	const [retryIn, setRetryIn] = useState(0);
	const [error, setError] = useState('');
	function cancel(message: string) {
		run.current++;
		controller.current?.abort();
		setBusy(false);
		setRetryIn(0);
		setError(message);
		void onApplied().catch(() => undefined);
	}

	async function chooseFile(file: File | undefined) {
		setResults([]);
		setExistingIndices(new Set());
		setTotal(0);
		setRemaining(0);
		setError('');
		if (!file) return;
		if (!file.name.toLowerCase().endsWith('.csv') || file.size > 1_000_000) {
			setError('Choose a CSV report smaller than 1 MB.');
			return;
		}
		const currentRun = ++run.current;
		const isCancelled = () => run.current !== currentRun;
		setBusy(true);
		try {
			const parsed = membershipRows(await file.text());
			if (!parsed.rows.length)
				throw new Error('No approved, paid, unexpired members were found.');
			if (parsed.rows.length > 500)
				throw new Error('The report has more than 500 eligible rows.');
			setExcluded(parsed.excluded);
			setTotal(parsed.rows.length);
			setRemaining(parsed.rows.length);
			let pending = parsed.rows.map((_, index) => index);
			const finished = new Set<number>();
			let existing: Set<number> | null = null;
			let retryDiscord = false;
			while (pending.length && !isCancelled()) {
				const failed: number[] = [];
				while (pending.length && !isCancelled()) {
					const indices = pending.splice(0, 5);
					const requestController = new AbortController();
					controller.current = requestController;
					try {
						const response = await fetch('/api/admin/membership-import/apply', {
							method: 'POST',
							headers: { 'content-type': 'application/json' },
							body: JSON.stringify({ rows: parsed.rows, indices, retryDiscord }),
							signal: requestController.signal,
						});
						const body = await response.json().catch(() => null);
						if (
							!response.ok &&
							response.status >= 400 &&
							response.status < 500 &&
							response.status !== 429
						) {
							cancel(apiMessage(body, 'Membership import was rejected.'));
							break;
						}
						if (!response.ok)
							throw new Error(apiMessage(body, 'Membership import failed'));
						if (isCancelled()) break;
						const result = body as { rows: Result[]; discordIssue: string | null };
						const updated = result.rows;
						if (!Array.isArray(updated) || updated.length !== parsed.rows.length)
							throw new Error('The API returned an incomplete import result.');
						if (!existing) {
							existing = new Set(
								updated.flatMap((row, index) =>
									row.apiStatus === 'already member' ? [index] : [],
								),
							);
							setExistingIndices(existing);
							setTotal(parsed.rows.length - existing.size);
							pending = pending.filter((index) => !existing?.has(index));
						}
						setResults((current) =>
							current.length
								? current.map((row, index) =>
										indices.includes(index)
											? {
													...updated[index],
													apiStatus:
														row.apiStatus === 'updated' &&
														updated[index].apiStatus ===
															'already member'
															? 'updated'
															: updated[index].apiStatus,
												}
											: row,
									)
								: updated,
						);
						for (const index of indices) {
							if (existing.has(index)) continue;
							if (
								updated[index].apiStatus === 'update failed' ||
								['role update failed', 'Discord unavailable'].includes(
									updated[index].discordStatus,
								) ||
								(result.discordIssue && updated[index].discordName)
							)
								failed.push(index);
							else finished.add(index);
						}
						setRemaining(parsed.rows.length - existing.size - finished.size);
					} catch (caught) {
						if (isCancelled()) break;
						failed.push(...indices);
						setError(errorMessage(caught, 'Import request failed. Retrying.'));
					} finally {
						if (controller.current === requestController) controller.current = null;
					}
				}
				pending = failed.filter((index) => !existing?.has(index));
				if (!pending.length || isCancelled()) break;
				retryDiscord = true;
				for (let seconds = 30; seconds > 0 && !isCancelled(); seconds--) {
					setRetryIn(seconds);
					await new Promise((resolve) => setTimeout(resolve, 1000));
				}
				setRetryIn(0);
				setError('');
			}
			if (!isCancelled()) await onApplied();
		} catch (caught) {
			if (!isCancelled()) setError(errorMessage(caught, 'Could not read the report.'));
		} finally {
			if (!isCancelled()) {
				setBusy(false);
				setRetryIn(0);
			}
		}
	}

	const visible = results.filter((_, index) => !existingIndices.has(index));
	const complete = total - remaining;
	return (
		<div className="adminSection">
			<h3>Import society membership report</h3>
			<p>
				Upload the Union CSV to apply approved, paid, unexpired memberships. Discord names
				match server usernames exactly.
			</p>
			<input
				type="file"
				accept=".csv,text/csv"
				disabled={busy}
				onChange={(event) => {
					const file = event.target.files?.[0];
					event.target.value = '';
					void chooseFile(file);
				}}
				aria-label="Society membership CSV"
			/>
			{total > 0 && (
				<>
					<progress className="membershipImportProgress" value={complete} max={total}>
						{complete} of {total}
					</progress>
					<p role="status">
						{complete} of {total} new rows finished; {existingIndices.size} already
						members; {excluded} excluded.
					</p>
					{retryIn > 0 && (
						<p role="status">
							<span className="membershipImportSpinner" aria-hidden="true" />
							Retrying remaining rows in {retryIn}s…
						</p>
					)}
					{busy && (
						<button
							type="button"
							onClick={() => {
								cancel('Import stopped. The current batch may have finished.');
							}}
						>
							Cancel import
						</button>
					)}
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Email</th>
									<th>Minecraft name</th>
									<th>Discord name</th>
									<th>Minecraft applied</th>
									<th>Discord applied</th>
								</tr>
							</thead>
							<tbody>
								{visible.map((row, index) => (
									<tr key={`${row.email}-${index}`}>
										<td>{row.email}</td>
										<td>{row.minecraftUsername ?? '—'}</td>
										<td>{row.discordName || '—'}</td>
										<td>
											{row.apiStatus === 'updated' ? '✅' : '❌'}{' '}
											{statusReason(row.apiStatus, row.apiError)}
										</td>
										<td>
											{[
												'role added',
												'already has role',
												'assumed member',
											].includes(row.discordStatus)
												? '✅'
												: '❌'}{' '}
											{statusReason(row.discordStatus, row.discordError)}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}
			{results.length > 0 && total === 0 && (
				<p role="status">
					No new members to apply; {existingIndices.size} already have API membership.
				</p>
			)}
			{error && <p role="alert">{error}</p>}
		</div>
	);
}
