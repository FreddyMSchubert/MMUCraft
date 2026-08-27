'use client';

import { useCallback, useEffect, useState } from 'react';
import {
	ClaimEditorCard,
	formatDimension,
	type EditableClaim,
} from '@/components/claim-editor-card';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import type { AdminTabController } from './use-admin-tab-controller';

interface CurrentChunk {
	dimension: string;
	chunkX: number;
	chunkZ: number;
}

export function ServerClaimAdministrationSection({
	controller,
}: {
	controller: AdminTabController;
}) {
	const { confirm, showAlert } = useSiteAlert();
	const [claims, setClaims] = useState<EditableClaim[]>([]);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const active = controller.activeSection === 'server-claims';
	const load = useCallback(async () => {
		const body = await request<{ claims: EditableClaim[] }>('/api/admin/server-claims');
		setClaims(body.claims);
	}, []);

	useEffect(() => {
		if (!active) return;
		let cancelled = false;
		void load().catch((caught: unknown) => {
			if (!cancelled) setError(readError(caught));
		});
		return () => {
			cancelled = true;
		};
	}, [active, load]);

	async function run(action: () => Promise<void>, failureTitle: string) {
		setBusy(true);
		setError('');
		try {
			await action();
			await load();
		} catch (caught) {
			await showAlert({ title: failureTitle, message: readError(caught), tone: 'danger' });
		} finally {
			setBusy(false);
		}
	}

	async function addClaim() {
		await run(async () => {
			const current = await request<CurrentChunk>('/api/admin/server-claims/current');
			if (
				!(await confirm({
					title: 'Add this server claim?',
					message: `Chunk ${current.chunkX}, ${current.chunkZ} in ${formatDimension(current.dimension)} will be protected for committee use only.`,
					confirmLabel: 'Add server claim',
				}))
			)
				return;
			await request('/api/admin/server-claims', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(current),
			});
		}, 'Could not add the server claim');
	}

	async function updateAppearance(claim: EditableClaim, name: string, color: string | null) {
		await run(async () => {
			await request(`/api/admin/server-claims/${claim.id}/appearance`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ name, color }),
			});
		}, 'Could not save the server claim');
	}

	async function removeClaim(claim: EditableClaim) {
		if (
			!(await confirm({
				title: 'Delete this server claim?',
				message: `“${claim.name}” at ${formatDimension(claim.dimension)} (${claim.chunkX}, ${claim.chunkZ}) will lose all protection.`,
				confirmLabel: 'Delete claim',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await run(async () => {
			await request(`/api/admin/server-claims/${claim.id}`, { method: 'DELETE' });
		}, 'Could not delete the server claim');
	}

	if (!active) return null;
	return (
		<section className="adminSection claimsPanel">
			<div className="claimsTop">
				<div className="adminSectionHeader">
					<h3>Server claims</h3>
					<p>
						Protect shared server chunks. Only committee members can create or interact
						with these claims.
					</p>
				</div>
				<button type="button" disabled={busy} onClick={() => void addClaim()}>
					Add server claim
				</button>
			</div>
			<p className="tabNote">
				Stand in the target chunk in Minecraft, then select Add server claim.
			</p>
			{error && <p className="authError">{error}</p>}
			{claims.length === 0 ? (
				<p className="claimsEmpty">There are no server claims.</p>
			) : (
				<div className="claimsList">
					{claims.map((claim) => (
						<ClaimEditorCard
							key={claim.id}
							claim={claim}
							busy={busy}
							onDelete={() => void removeClaim(claim)}
							onSave={(name, color) => updateAppearance(claim, name, color)}
						/>
					))}
				</div>
			)}
		</section>
	);
}

async function request<T = Record<string, unknown>>(url: string, init?: RequestInit): Promise<T> {
	const response = await fetch(url, init);
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, 'Request failed'));
	return body as T;
}

function readError(caught: unknown) {
	return caught instanceof Error ? caught.message : 'Request failed';
}
