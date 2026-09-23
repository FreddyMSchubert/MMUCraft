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

export function MembershipImportAdmin({ onApplied }: { onApplied: () => Promise<void> }) {
	const run = useRef(0);
	const controller = useRef<AbortController | null>(null);
	const [results, setResults] = useState<Result[]>([]);
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
			setRemaining(parsed.rows.length);
			setResults(
				parsed.rows.map((row) => ({
					...row,
					minecraftUsername: null,
					userId: null,
					discordNameSource: null,
					discordLabel: null,
					apiStatus: 'pending',
					discordStatus: 'pending',
				})),
			);
			let pending = parsed.rows.map((_, index) => index);
			const finished = new Set<number>();
			let retryDiscord = false;
			while (pending.length && !isCancelled()) {
				const failed: number[] = [];
				for (let start = 0; start < pending.length && !isCancelled(); start += 5) {
					const indices = pending.slice(start, start + 5);
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
						setResults((current) =>
							current.map((row, index) =>
								(current[index].apiStatus === 'pending' && !retryDiscord) ||
								indices.includes(index)
									? updated[index]
									: row,
							),
						);
						for (const index of indices) {
							if (
								updated[index].apiStatus === 'update failed' ||
								['role update failed', 'Discord unavailable'].includes(
									updated[index].discordStatus,
								) ||
								(result.discordIssue &&
									updated[index].userId &&
									updated[index].discordName &&
									updated[index].apiStatus !== 'already member')
							)
								failed.push(index);
							else finished.add(index);
						}
						setRemaining(parsed.rows.length - finished.size);
					} catch (caught) {
						if (isCancelled()) break;
						failed.push(...indices);
						setError(errorMessage(caught, 'Import request failed. Retrying.'));
					} finally {
						if (controller.current === requestController) controller.current = null;
					}
				}
				pending = failed;
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

	const visible = results.filter((row) => row.apiStatus !== 'already member');
	const complete = results.length - remaining;
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
			{results.length > 0 && (
				<>
					<progress
						className="membershipImportProgress"
						value={complete}
						max={results.length}
					>
						{complete} of {results.length}
					</progress>
					<p role="status">
						{complete} of {results.length} rows finished; {excluded} excluded.
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
										<td
											title={row.apiStatus}
											aria-label={`Minecraft: ${row.apiStatus}`}
										>
											{['updated', 'already member'].includes(row.apiStatus)
												? '✅'
												: '❌'}
										</td>
										<td
											title={row.discordStatus}
											aria-label={`Discord: ${row.discordStatus}`}
										>
											{[
												'role added',
												'already has role',
												'assumed member',
											].includes(row.discordStatus)
												? '✅'
												: '❌'}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}
			{error && <p role="alert">{error}</p>}
		</div>
	);
}
