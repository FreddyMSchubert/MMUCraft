'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiBody, apiMessage, errorMessage, fetchAdmin } from './admin-api';

interface FeatureToggle {
	key: string;
	enabled: boolean;
}

const TOGGLE_ORDER = [
	'nether',
	'end',
	'welcoming',
	'soaring',
	'imaginative',
	'hellish',
	'circus',
	'efficient',
	'overpowered',
] as const;

function toggleTitle(key: string) {
	return key.replace(/[-_]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function togglePosition(key: string) {
	const position = TOGGLE_ORDER.indexOf(key as (typeof TOGGLE_ORDER)[number]);
	return position < 0 ? TOGGLE_ORDER.length : position;
}

export function FeatureToggleAdminSection() {
	const { showAlert } = useSiteAlert();
	const [toggles, setToggles] = useState<FeatureToggle[] | null>(null);
	const [busyKey, setBusyKey] = useState<string | null>(null);
	const [error, setError] = useState('');

	const load = useCallback(async () => {
		return fetchAdmin<{ toggles: FeatureToggle[] }>(
			'/api/admin/toggles',
			'Failed to load feature toggles',
		);
	}, []);

	useEffect(() => {
		let cancelled = false;
		async function refresh() {
			try {
				const result = await load();
				if (!cancelled) setToggles(result.toggles);
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught, 'Failed to load feature toggles'));
			}
		}
		void refresh();
		return () => {
			cancelled = true;
		};
	}, [load]);

	async function setToggle(key: string, enabled: boolean) {
		setBusyKey(key);
		setError('');
		try {
			const response = await fetch(`/api/admin/toggles/${encodeURIComponent(key)}`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ enabled }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to update the toggle'));
			const result = apiBody<{ toggles: FeatureToggle[]; minecraftSynced: boolean }>(body);
			setToggles(result.toggles);
			await showAlert({
				title: result.minecraftSynced ? 'Gameplay updated' : 'Toggle saved; sync pending',
				message: result.minecraftSynced
					? 'The running Minecraft server applied the change.'
					: 'The database saved the change. Minecraft will retry until it applies it.',
				tone: result.minecraftSynced ? 'success' : 'danger',
			});
		} catch (caught) {
			await showAlert({
				title: 'Could not update gameplay',
				message: errorMessage(caught, 'The feature toggle was not changed.'),
				tone: 'danger',
			});
		} finally {
			setBusyKey(null);
		}
	}

	return (
		<section className="adminSection settingsSection">
			<div className="adminSectionHeader">
				<h3>Gameplay toggles</h3>
				<p>Changes are stored in the API database and applied to the running server.</p>
			</div>
			{toggles ? (
				<div className="settingsList">
					{toggles
						.toSorted(
							(left, right) =>
								togglePosition(left.key) - togglePosition(right.key) ||
								left.key.localeCompare(right.key),
						)
						.map((toggle) => {
							return (
								<label className="settingToggle" key={toggle.key}>
									<span>
										<strong>{toggleTitle(toggle.key)}</strong>
										<small>
											Allow gameplay and unlocks linked to this toggle.
										</small>
									</span>
									<input
										type="checkbox"
										checked={toggle.enabled}
										disabled={busyKey !== null}
										onChange={(event) =>
											void setToggle(toggle.key, event.target.checked)
										}
									/>
									<i aria-hidden="true" />
								</label>
							);
						})}
				</div>
			) : (
				<p>{error || 'Loading gameplay toggles...'}</p>
			)}
			{toggles && error && <p className="authError">{error}</p>}
		</section>
	);
}
