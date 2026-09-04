'use client';

import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { apiBody, apiMessage } from './admin-api';
import type { SigninAttemptLogEntry } from './admin-data.types';
import type { AdminTabController } from './use-admin-tab-controller';

type AuthEvent = SigninAttemptLogEntry['event'];

interface Filters {
	journey: '' | SigninAttemptLogEntry['journey'];
	event: '' | AuthEvent;
	succeeded: '' | 'true' | 'false';
	from: string;
	to: string;
	search: string;
}

interface SigninAttemptLogResponse {
	attempts: SigninAttemptLogEntry[];
	hasMore: boolean;
	nextCursor: number | null;
}

const EMPTY_FILTERS: Filters = {
	journey: '',
	event: '',
	succeeded: '',
	from: '',
	to: '',
	search: '',
};

const EVENT_LABELS: Record<AuthEvent, string> = {
	email_send: 'Email send',
	email_resend: 'Email resend',
	email_code_input: 'Email code input',
	minecraft_username_input: 'Minecraft username input',
	minecraft_code_input: 'Minecraft code input',
	rules_accept: 'Rules accept',
};

export function SigninAttemptHistoryAdminSection({
	controller,
}: {
	controller: AdminTabController;
}) {
	const { activeSection } = controller;
	const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
	const [query, setQuery] = useState('');
	const [attempts, setAttempts] = useState<SigninAttemptLogEntry[]>([]);
	const [nextCursor, setNextCursor] = useState<number | null>(null);
	const [hasMore, setHasMore] = useState(false);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');

	const loadPage = useCallback(
		async (cursor: number | null, append: boolean) => {
			setLoading(true);
			setError('');
			try {
				const params = new URLSearchParams(query);
				if (cursor !== null) params.set('beforeId', String(cursor));
				const response = await fetch(`/api/admin/signin-attempt-logs?${params}`, {
					cache: 'no-store',
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to load sign-in attempts'));
				const result = apiBody<SigninAttemptLogResponse>(body);
				setAttempts((current) =>
					append ? [...current, ...result.attempts] : result.attempts,
				);
				setHasMore(result.hasMore);
				setNextCursor(result.nextCursor);
			} catch (caught) {
				setError(
					caught instanceof Error ? caught.message : 'Failed to load sign-in attempts',
				);
			} finally {
				setLoading(false);
			}
		},
		[query],
	);

	useEffect(() => {
		if (activeSection !== 'signin-attempts') return;
		const timeoutId = window.setTimeout(() => void loadPage(null, false), 0);
		return () => {
			window.clearTimeout(timeoutId);
		};
	}, [activeSection, loadPage]);

	if (activeSection !== 'signin-attempts') return null;

	function applyFilters(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const params = new URLSearchParams();
		if (draft.journey) params.set('journey', draft.journey);
		if (draft.event) params.set('event', draft.event);
		if (draft.succeeded) params.set('succeeded', draft.succeeded);
		if (draft.from) params.set('fromUnixMs', String(localDateTimeToUnixMs(draft.from, false)));
		if (draft.to) params.set('toUnixMs', String(localDateTimeToUnixMs(draft.to, true)));
		if (draft.search.trim()) params.set('search', draft.search.trim());
		setQuery(params.toString());
	}

	return (
		<section className="adminSection commandLogSection">
			<div className="adminSectionHeader">
				<h3>Sign-in attempt log</h3>
				<p>Every tracked sign-in and sign-up operation. Codes are never stored.</p>
			</div>
			<form className="commandLogFilters" onSubmit={applyFilters}>
				<label>
					<span>Journey</span>
					<select
						value={draft.journey}
						onChange={(event) => {
							setDraft({
								...draft,
								journey: event.target.value as Filters['journey'],
							});
						}}
					>
						<option value="">Sign-in + sign-up</option>
						<option value="signin">Sign-in</option>
						<option value="signup">Sign-up</option>
					</select>
				</label>
				<label>
					<span>Event</span>
					<select
						value={draft.event}
						onChange={(event) => {
							setDraft({ ...draft, event: event.target.value as Filters['event'] });
						}}
					>
						<option value="">All events</option>
						{Object.entries(EVENT_LABELS).map(([value, label]) => (
							<option key={value} value={value}>
								{label}
							</option>
						))}
					</select>
				</label>
				<label>
					<span>Outcome</span>
					<select
						value={draft.succeeded}
						onChange={(event) => {
							setDraft({
								...draft,
								succeeded: event.target.value as Filters['succeeded'],
							});
						}}
					>
						<option value="">All outcomes</option>
						<option value="true">Success</option>
						<option value="false">Failure</option>
					</select>
				</label>
				<label className="commandLogSearch">
					<span>Search email</span>
					<input
						type="search"
						value={draft.search}
						onChange={(event) => {
							setDraft({ ...draft, search: event.target.value });
						}}
						placeholder="Capitalization is ignored"
					/>
				</label>
				<DateFilter
					label="From (your local time)"
					value={draft.from}
					onChange={(from) => {
						setDraft({ ...draft, from });
					}}
				/>
				<DateFilter
					label="To (your local time)"
					value={draft.to}
					onChange={(to) => {
						setDraft({ ...draft, to });
					}}
				/>
				<div className="commandLogFilterActions">
					<button type="submit" disabled={loading}>
						Apply filters
					</button>
					<button
						type="button"
						className="secondary"
						disabled={loading}
						onClick={() => {
							setDraft(EMPTY_FILTERS);
							setQuery('');
						}}
					>
						Reset all
					</button>
					<button
						type="button"
						className="secondary"
						disabled={loading}
						onClick={() => void loadPage(null, false)}
					>
						Refresh
					</button>
				</div>
			</form>

			{error && <p className="authError">{error}</p>}
			<div className="adminTableWrap commandLogTableWrap">
				<table className="adminTable">
					<thead>
						<tr>
							<th>Email</th>
							<th>Journey</th>
							<th>Event</th>
							<th>Outcome</th>
							<th>Detail</th>
							<th>Created (with time zone)</th>
						</tr>
					</thead>
					<tbody>
						{attempts.map((entry) => (
							<tr key={entry.id}>
								<td>{entry.email ?? 'Unknown'}</td>
								<td>{entry.journey === 'signin' ? 'Sign-in' : 'Sign-up'}</td>
								<td>{EVENT_LABELS[entry.event]}</td>
								<td>
									<Outcome succeeded={entry.succeeded} />
								</td>
								<td>{entry.detail ?? '—'}</td>
								<td>{formatZonedDateTime(entry.createdAtUnixMs)}</td>
							</tr>
						))}
						{attempts.length === 0 && !loading && (
							<tr>
								<td colSpan={6}>No attempts match these filters.</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
			<div className="commandLogLoadMore">
				{loading && <span>Loading attempts…</span>}
				{hasMore && (
					<button
						type="button"
						disabled={loading || nextCursor === null}
						onClick={() => void loadPage(nextCursor, true)}
					>
						Load 50 older attempts
					</button>
				)}
				{!hasMore && attempts.length > 0 && (
					<span>Beginning of the filtered attempt history.</span>
				)}
			</div>
		</section>
	);
}

function DateFilter({
	label,
	value,
	onChange,
}: {
	label: string;
	value: string;
	onChange: (value: string) => void;
}) {
	return (
		<label className="commandLogDateFilter">
			<span>{label}</span>
			<div>
				<input
					type="datetime-local"
					value={value}
					onChange={(event) => {
						onChange(event.target.value);
					}}
				/>
				<button
					type="button"
					className="secondary"
					disabled={!value}
					onClick={() => {
						onChange('');
					}}
				>
					Reset
				</button>
			</div>
		</label>
	);
}

function Outcome({ succeeded }: { succeeded: boolean | null }) {
	if (succeeded === null) return <span title="Outcome does not apply">—</span>;
	return (
		<span
			className={`commandOutcome commandOutcome-${succeeded ? 'success' : 'failure'}`}
			title={succeeded ? 'Succeeded' : 'Failed'}
		>
			{succeeded ? '✅' : '❌'}
		</span>
	);
}

function formatZonedDateTime(timestamp: number) {
	return new Intl.DateTimeFormat(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
		timeZoneName: 'short',
	}).format(new Date(timestamp));
}

function localDateTimeToUnixMs(value: string, endOfMinute: boolean) {
	const timestamp = new Date(value).getTime();
	return endOfMinute && value.length === 16 ? timestamp + 59_999 : timestamp;
}
