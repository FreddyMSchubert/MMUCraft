'use client';

import { useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage, errorMessage } from './admin-api';

export function MinecraftCacheAdminSection() {
	const { showAlert } = useSiteAlert();
	const [busy, setBusy] = useState(false);

	async function refresh() {
		setBusy(true);
		try {
			const response = await fetch('/api/admin/minecraft/cache/refresh', { method: 'POST' });
			const body: unknown = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not refresh Minecraft data'));
			const onlinePlayers =
				body &&
				typeof body === 'object' &&
				'onlinePlayers' in body &&
				typeof body.onlinePlayers === 'number'
					? body.onlinePlayers
					: 0;
			await showAlert({
				title: 'Minecraft data refreshed',
				message: `Claims, gameplay settings, daily tasks, unlock availability, and ${onlinePlayers} online player profiles were refreshed.`,
				tone: 'success',
			});
		} catch (caught) {
			await showAlert({
				title: 'Refresh failed',
				message: errorMessage(caught, 'Could not refresh Minecraft data'),
				tone: 'danger',
			});
		} finally {
			setBusy(false);
		}
	}

	return (
		<section className="adminSection">
			<div className="adminSectionHeader">
				<h3>Refresh Minecraft data</h3>
				<p>
					Reload claims, gameplay settings, daily tasks, and online player data from the
					API.
				</p>
			</div>
			<button type="button" disabled={busy} onClick={() => void refresh()}>
				{busy ? 'Refreshing...' : 'Refresh cache'}
			</button>
		</section>
	);
}
