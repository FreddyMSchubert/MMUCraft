'use client';

import { useCallback, useEffect, useState } from 'react';
import {
	ClaimEditorCard,
	formatClaimArea,
	formatDimension,
	type EditableClaim,
	type ClaimSettingsChange,
} from '@/components/claim-editor-card';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import type { AdminTabController } from './use-admin-tab-controller';

interface CurrentChunk {
	dimension: string;
	chunkX: number;
	chunkZ: number;
}

interface AreaInput extends CurrentChunk {
	oppositeChunkX: number;
	oppositeChunkZ: number;
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

		async function loadInitial() {
			try {
				await load();
			} catch (caught) {
				if (!cancelled) setError(readError(caught));
			}
		}

		void loadInitial();
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
			return true;
		} catch (caught) {
			await showAlert({ title: failureTitle, message: readError(caught), tone: 'danger' });
			return false;
		} finally {
			setBusy(false);
		}
	}

	async function addClaim(area: AreaInput) {
		if (
			!(await confirm({
				title: 'Add this server claim?',
				message: `${describeArea(area)} will be one server claim. MMU_Admin has full access.`,
				confirmLabel: 'Add server claim',
			}))
		)
			return false;
		return run(async () => {
			await request('/api/admin/server-claims', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(area),
			});
		}, 'Could not add the server claim');
	}

	async function updateArea(claim: EditableClaim, area: AreaInput) {
		if (
			!(await confirm({
				title: 'Change this server claim area?',
				message: `${describeArea(area)} will replace the current area. Chunks outside it will lose this claim's protection.`,
				confirmLabel: 'Save area',
			}))
		)
			return false;
		return run(async () => {
			await request(`/api/admin/server-claims/${claim.id}/area`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(area),
			});
		}, 'Could not change the server claim area');
	}

	async function updateAppearance(claim: EditableClaim, name: string, color: string | null) {
		return run(async () => {
			await request(`/api/admin/server-claims/${claim.id}/appearance`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ name, color }),
			});
		}, 'Could not save the server claim');
	}

	async function updateSettings(claim: EditableClaim, change: ClaimSettingsChange) {
		return run(async () => {
			await request(`/api/admin/server-claims/${claim.id}/settings`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(change),
			});
		}, 'Could not save the server claim settings');
	}

	async function removeClaim(claim: EditableClaim) {
		if (
			!(await confirm({
				title: 'Delete this server claim?',
				message: `“${claim.name}” at ${formatClaimArea(claim)} in ${formatDimension(claim.dimension)} will lose all protection.`,
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
						Protect shared server chunks. MMU_Admin has full access. Set which actions
						other players can take in each claim.
					</p>
				</div>
			</div>
			<p className="tabNote">
				Stand in one corner chunk and capture it. Then stand in the opposite corner chunk
				and capture it. Capture the same chunk twice for a one-chunk claim.
			</p>
			<ServerClaimAreaPicker busy={busy} onSave={addClaim} saveLabel="Add server claim" />
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
							onSettings={(change) => updateSettings(claim, change)}
							copySources={claims}
							areaEditor={
								<ServerClaimAreaPicker
									key={`${claim.id}:${claim.dimension}:${claim.chunkX}:${claim.chunkZ}:${claim.chunkXEnd}:${claim.chunkZEnd}`}
									initial={claim}
									busy={busy}
									onSave={(area) => updateArea(claim, area)}
									saveLabel="Save area"
								/>
							}
						/>
					))}
				</div>
			)}
		</section>
	);
}

function ServerClaimAreaPicker({
	initial,
	busy,
	onSave,
	saveLabel,
}: {
	initial?: EditableClaim;
	busy: boolean;
	onSave: (area: AreaInput) => Promise<boolean>;
	saveLabel: string;
}) {
	const { showAlert } = useSiteAlert();
	const [first, setFirst] = useState<CurrentChunk | null>(
		initial
			? {
					dimension: initial.dimension,
					chunkX: initial.chunkX,
					chunkZ: initial.chunkZ,
				}
			: null,
	);
	const [second, setSecond] = useState<CurrentChunk | null>(
		initial
			? {
					dimension: initial.dimension,
					chunkX: initial.chunkXEnd ?? initial.chunkX,
					chunkZ: initial.chunkZEnd ?? initial.chunkZ,
				}
			: null,
	);
	const [capturing, setCapturing] = useState(false);
	const selected = first !== null && second !== null && first.dimension === second.dimension;
	const chunkCount = selected
		? (Math.abs(first.chunkX - second.chunkX) + 1) *
			(Math.abs(first.chunkZ - second.chunkZ) + 1)
		: 0;

	async function capture(which: 'first' | 'second') {
		setCapturing(true);
		try {
			const current = await request<CurrentChunk>('/api/admin/server-claims/current');
			if (which === 'first') {
				setFirst(current);
				if (second?.dimension !== current.dimension) setSecond(null);
			} else {
				setSecond(current);
				if (first?.dimension !== current.dimension) setFirst(null);
			}
		} catch (caught) {
			await showAlert({
				title: 'Could not capture the current chunk',
				message: readError(caught),
				tone: 'danger',
			});
		} finally {
			setCapturing(false);
		}
	}

	return (
		<div className="serverClaimAreaPicker">
			<div className="serverClaimCorners">
				{(
					[
						['First corner', first, 'first'],
						['Opposite corner', second, 'second'],
					] as const
				).map(([label, corner, which]) => (
					<div className="serverClaimCorner" key={which}>
						<strong>{label}</strong>
						<span>
							{corner
								? `${corner.chunkX}, ${corner.chunkZ} in ${formatDimension(corner.dimension)}`
								: 'Not selected'}
						</span>
						<button
							type="button"
							disabled={busy || capturing}
							onClick={() => void capture(which)}
						>
							Use current chunk
						</button>
					</div>
				))}
			</div>
			{selected && (
				<p>
					{describeArea({
						...first,
						oppositeChunkX: second.chunkX,
						oppositeChunkZ: second.chunkZ,
					})}
				</p>
			)}
			{chunkCount > 4096 && (
				<p className="authError">A server claim can cover at most 4096 chunks.</p>
			)}
			<button
				type="button"
				disabled={busy || capturing || !selected || chunkCount > 4096}
				onClick={() => {
					if (!selected) return;
					void onSave({
						...first,
						oppositeChunkX: second.chunkX,
						oppositeChunkZ: second.chunkZ,
					}).then((saved) => {
						if (saved && !initial) {
							setFirst(null);
							setSecond(null);
						}
					});
				}}
			>
				{saveLabel}
			</button>
		</div>
	);
}

function describeArea(area: AreaInput) {
	const x1 = Math.min(area.chunkX, area.oppositeChunkX);
	const z1 = Math.min(area.chunkZ, area.oppositeChunkZ);
	const x2 = Math.max(area.chunkX, area.oppositeChunkX);
	const z2 = Math.max(area.chunkZ, area.oppositeChunkZ);
	const count = (x2 - x1 + 1) * (z2 - z1 + 1);
	return `${formatClaimArea({ chunkX: x1, chunkZ: z1, chunkXEnd: x2, chunkZEnd: z2 })} in ${formatDimension(area.dimension)} (${count} ${count === 1 ? 'chunk' : 'chunks'})`;
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
