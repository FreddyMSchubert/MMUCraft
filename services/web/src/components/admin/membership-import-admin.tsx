'use client';

import { useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
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
	const { confirm } = useSiteAlert();
	const [rows, setRows] = useState<Row[]>([]);
	const [results, setResults] = useState<Result[]>([]);
	const [excluded, setExcluded] = useState(0);
	const [busy, setBusy] = useState(false);
	const [applied, setApplied] = useState(false);
	const [error, setError] = useState('');
	const [discordIssue, setDiscordIssue] = useState<string | null>(null);

	async function request(path: string, input: Row[]) {
		const response = await fetch(path, {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ rows: input }),
		});
		const body = await response.json().catch(() => null);
		if (!response.ok) throw new Error(apiMessage(body, 'Membership import failed'));
		return body as { rows: Result[]; discordIssue: string | null };
	}

	async function chooseFile(file: File | undefined) {
		setResults([]);
		setRows([]);
		setApplied(false);
		setError('');
		setDiscordIssue(null);
		if (!file) return;
		if (!file.name.toLowerCase().endsWith('.csv') || file.size > 1_000_000) {
			setError('Choose a CSV report smaller than 1 MB.');
			return;
		}
		setBusy(true);
		try {
			const parsed = membershipRows(await file.text());
			if (!parsed.rows.length)
				throw new Error('No approved, paid, unexpired members were found.');
			setExcluded(parsed.excluded);
			setRows(parsed.rows);
			const preview = await request('/api/admin/membership-import/preview', parsed.rows);
			setResults(preview.rows);
			setDiscordIssue(preview.discordIssue);
		} catch (caught) {
			setError(errorMessage(caught, 'Could not read the report.'));
		} finally {
			setBusy(false);
		}
	}

	async function apply() {
		if (
			!(await confirm({
				title: 'Apply reviewed memberships?',
				message: `Grant API membership for matching emails and the 26/27 Member Discord role for uniquely matched names in these ${rows.length} report rows. Check the matches below first.`,
				confirmLabel: 'Apply memberships',
				confirmTone: 'primary',
				tone: 'info',
			}))
		)
			return;
		setBusy(true);
		setError('');
		try {
			const applied = await request('/api/admin/membership-import/apply', rows);
			setResults(applied.rows);
			setDiscordIssue(applied.discordIssue);
			setApplied(true);
			await onApplied();
		} catch (caught) {
			setError(errorMessage(caught, 'Could not apply memberships.'));
		} finally {
			setBusy(false);
		}
	}

	return (
		<div className="adminSection">
			<h3>Import society membership report</h3>
			<p>
				Upload the Union CSV. Approved, paid, unexpired entries are matched by email.
				Discord usernames are matched exactly to server accounts; the player profile name is
				used when the report has no Discord name. Review every match before applying.
			</p>
			<input
				type="file"
				accept=".csv,text/csv"
				disabled={busy}
				onChange={(event) => void chooseFile(event.target.files?.[0])}
				aria-label="Society membership CSV"
			/>
			{busy && <p>Checking membership report…</p>}
			{error && <p role="alert">{error}</p>}
			{discordIssue && (
				<p role="alert">
					Discord roles are unavailable: {discordIssue}. API memberships can still be
					applied.
				</p>
			)}
			{results.length > 0 && (
				<>
					<p>
						{results.length} eligible entries; {excluded} excluded.{' '}
						{applied
							? 'Import completed; inspect each result below.'
							: 'No changes made yet.'}
					</p>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Email</th>
									<th>API account</th>
									<th>API status</th>
									<th>Discord name</th>
									<th>Discord match</th>
									<th>Discord status</th>
								</tr>
							</thead>
							<tbody>
								{results.map((row, index) => (
									<tr key={`${row.email}-${index}`}>
										<td>{row.email}</td>
										<td>{row.minecraftUsername ?? 'None'}</td>
										<td>{row.apiStatus}</td>
										<td>
											{row.discordName || 'None'}
											{row.discordNameSource && ` (${row.discordNameSource})`}
										</td>
										<td>{row.discordLabel ?? 'None'}</td>
										<td>{row.discordStatus}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
					{!applied && (
						<button type="button" disabled={busy} onClick={() => void apply()}>
							Apply reviewed matches
						</button>
					)}
				</>
			)}
		</div>
	);
}
