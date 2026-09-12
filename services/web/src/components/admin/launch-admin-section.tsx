'use client';

import { type SyntheticEvent, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiBody, apiMessage, errorMessage, formatLondonInput } from './admin-api';
import type { AdminSection } from './admin-data.types';

export function LaunchAdminSection({ activeSection }: { activeSection: AdminSection }) {
	const { showAlert } = useSiteAlert();
	const [target, setTarget] = useState('');
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		if (activeSection !== 'launch') return;
		let cancelled = false;
		void fetch('/api/admin/launch', { cache: 'no-store' })
			.then(async (response) => {
				const body = await response.json().catch(() => null);
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to load launch time'));
				const launch = apiBody<{ launchAtUnixMs: number }>(body);
				if (!cancelled) setTarget(formatLondonInput(launch.launchAtUnixMs));
			})
			.catch(async (caught: unknown) => {
				if (!cancelled)
					await showAlert({
						title: 'Could not load launch time',
						message: errorMessage(caught, 'Please try again.'),
						tone: 'danger',
					});
			});
		return () => {
			cancelled = true;
		};
	}, [activeSection, showAlert]);

	function save(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setSaving(true);
		void fetch('/api/admin/launch', {
			method: 'PATCH',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ target }),
		})
			.then(async (response) => {
				const body = await response.json().catch(() => null);
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to update launch time'));
				const launch = apiBody<{ launchAtUnixMs: number }>(body);
				setTarget(formatLondonInput(launch.launchAtUnixMs));
				window.dispatchEvent(new Event('launch-settings-change'));
				await showAlert({
					title: 'Launch time updated',
					message: 'The public countdown and signup opening time now use this date.',
					tone: 'success',
				});
			})
			.catch(async (caught: unknown) => {
				await showAlert({
					title: 'Could not update launch time',
					message: errorMessage(caught, 'No changes were made.'),
					tone: 'danger',
				});
			})
			.finally(() => {
				setSaving(false);
			});
	}

	if (activeSection !== 'launch') return null;
	return (
		<section className="adminSection">
			<div className="adminSectionHeader">
				<h3>Server launch</h3>
				<p>
					The public countdown and signup gate share this date. Signups open automatically
					when it passes. Enter the date and time in British time.
				</p>
			</div>
			<form className="adminForm" onSubmit={save}>
				<label>
					Launch date and time (UK)
					<input
						type="datetime-local"
						value={target}
						onChange={(event) => {
							setTarget(event.target.value);
						}}
						disabled={saving}
						required
					/>
				</label>
				<button disabled={saving || !target}>
					{saving ? 'Saving...' : 'Save launch time'}
				</button>
			</form>
		</section>
	);
}
