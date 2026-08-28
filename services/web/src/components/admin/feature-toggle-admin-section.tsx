'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiBody, apiMessage, errorMessage, fetchAdmin } from './admin-api';

interface FeatureToggle {
	key: string;
	enabled: boolean;
}

const TOGGLES = [
	{
		key: 'nether',
		title: 'Nether access',
		description: 'Allow Nether portal creation and travel, and Nether-dependent dailies.',
	},
	{
		key: 'end',
		title: 'End access',
		description:
			'Allow Eyes of Ender in portal frames, travel to the End, and End-dependent dailies.',
	},
] as const;

export function FeatureToggleAdminSection() {
	const { showAlert } = useSiteAlert();
	const [toggles, setToggles] = useState<FeatureToggle[] | null>(null);
	const [busyKey, setBusyKey] = useState<string | null>(null);
	const [error, setError] = useState('');

	const load = useCallback(async () => {
		const result = await fetchAdmin<{ toggles: FeatureToggle[] }>(
			'/api/admin/toggles',
			'Failed to load feature toggles',
		);
		setToggles(result.toggles);
	}, []);

	useEffect(() => {
		void load().catch((caught: unknown) => {
			setError(errorMessage(caught, 'Failed to load feature toggles'));
		});
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
					{TOGGLES.map((definition) => {
						const toggle = toggles.find(
							(candidate) => candidate.key === definition.key,
						);
						return (
							<label className="settingToggle" key={definition.key}>
								<span>
									<strong>{definition.title}</strong>
									<small>{definition.description}</small>
								</span>
								<input
									type="checkbox"
									checked={toggle?.enabled ?? false}
									disabled={!toggle || busyKey !== null}
									onChange={(event) =>
										void setToggle(definition.key, event.target.checked)
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
