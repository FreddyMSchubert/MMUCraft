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
				Enter chunk coordinates directly, or fill either corner from your position or
				MMU_Admin&apos;s position.
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
							areaEditor={(onDirtyChange) => (
								<ServerClaimAreaPicker
									key={`${claim.id}:${claim.dimension}:${claim.chunkX}:${claim.chunkZ}:${claim.chunkXEnd}:${claim.chunkZEnd}`}
									initial={claim}
									busy={busy}
									onSave={(area) => updateArea(claim, area)}
									onDirtyChange={onDirtyChange}
									saveLabel="Save area"
								/>
							)}
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
	onDirtyChange,
	saveLabel,
}: {
	initial?: EditableClaim;
	busy: boolean;
	onSave: (area: AreaInput) => Promise<boolean>;
	onDirtyChange?: (dirty: boolean) => void;
	saveLabel: string;
}) {
	const { showAlert } = useSiteAlert();
	const [dimension, setDimension] = useState(initial?.dimension ?? 'minecraft:overworld');
	const [firstX, setFirstX] = useState(initial ? String(initial.chunkX) : '');
	const [firstZ, setFirstZ] = useState(initial ? String(initial.chunkZ) : '');
	const [secondX, setSecondX] = useState(
		initial ? String(initial.chunkXEnd ?? initial.chunkX) : '',
	);
	const [secondZ, setSecondZ] = useState(
		initial ? String(initial.chunkZEnd ?? initial.chunkZ) : '',
	);
	const [capturing, setCapturing] = useState(false);
	const first = parseCorner(dimension, firstX, firstZ);
	const second =
		secondX === '' && secondZ === '' ? first : parseCorner(dimension, secondX, secondZ);
	const selected = first !== null && second !== null;
	const chunkCount = selected
		? (Math.abs(first.chunkX - second.chunkX) + 1) *
			(Math.abs(first.chunkZ - second.chunkZ) + 1)
		: 0;

	useEffect(() => {
		if (!initial) return;
		onDirtyChange?.(
			dimension !== initial.dimension ||
				firstX !== String(initial.chunkX) ||
				firstZ !== String(initial.chunkZ) ||
				secondX !== String(initial.chunkXEnd ?? initial.chunkX) ||
				secondZ !== String(initial.chunkZEnd ?? initial.chunkZ),
		);
	}, [dimension, firstX, firstZ, secondX, secondZ, initial, onDirtyChange]);

	async function capture(which: 'first' | 'second', source: 'mine' | 'admin') {
		setCapturing(true);
		try {
			const current = await request<CurrentChunk>(
				source === 'mine'
					? '/api/admin/server-claims/current'
					: '/api/admin/build-events/admin-position',
			);
			if (dimension !== current.dimension) {
				if (which === 'first') {
					setSecondX('');
					setSecondZ('');
				} else {
					setFirstX('');
					setFirstZ('');
				}
			}
			setDimension(current.dimension);
			if (which === 'first') {
				setFirstX(String(current.chunkX));
				setFirstZ(String(current.chunkZ));
			} else {
				setSecondX(String(current.chunkX));
				setSecondZ(String(current.chunkZ));
			}
		} catch (caught) {
			await showAlert({
				title: `Could not get ${source === 'mine' ? 'your' : "MMU_Admin's"} current chunk`,
				message: readError(caught),
				tone: 'danger',
			});
		} finally {
			setCapturing(false);
		}
	}

	return (
		<div className="serverClaimAreaPicker">
			<label className="serverClaimDimension">
				<span>Dimension</span>
				<input
					type="text"
					list={`server-claim-dimensions-${initial?.id ?? 'new'}`}
					value={dimension}
					onChange={(event) => {
						setDimension(event.target.value);
					}}
					disabled={busy || capturing}
				/>
			</label>
			<datalist id={`server-claim-dimensions-${initial?.id ?? 'new'}`}>
				<option value="minecraft:overworld" />
				<option value="minecraft:the_nether" />
				<option value="minecraft:the_end" />
			</datalist>
			<div className="serverClaimCorners">
				{(
					[
						['First corner', firstX, firstZ, setFirstX, setFirstZ, 'first'],
						[
							'Opposite corner (optional)',
							secondX,
							secondZ,
							setSecondX,
							setSecondZ,
							'second',
						],
					] as const
				).map(([label, x, z, setX, setZ, which]) => (
					<div className="serverClaimCorner" key={which}>
						<strong>{label}</strong>
						<div className="serverClaimCoordinates">
							<label>
								<span>Chunk X</span>
								<input
									type="number"
									min={-1875000}
									max={1875000}
									step={1}
									value={x}
									aria-label={`${label} chunk X`}
									onChange={(event) => {
										setX(event.target.value);
									}}
									disabled={busy || capturing}
								/>
							</label>
							<label>
								<span>Chunk Z</span>
								<input
									type="number"
									min={-1875000}
									max={1875000}
									step={1}
									value={z}
									aria-label={`${label} chunk Z`}
									onChange={(event) => {
										setZ(event.target.value);
									}}
									disabled={busy || capturing}
								/>
							</label>
						</div>
						<div className="serverClaimCaptureActions">
							<button
								type="button"
								disabled={busy || capturing}
								onClick={() => void capture(which, 'mine')}
							>
								Use my current chunk
							</button>
							<button
								type="button"
								disabled={busy || capturing}
								onClick={() => void capture(which, 'admin')}
							>
								Use MMU_Admin&apos;s current chunk
							</button>
						</div>
					</div>
				))}
			</div>
			<p>Leave the opposite corner empty for a one-chunk claim.</p>
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
							setFirstX('');
							setFirstZ('');
							setSecondX('');
							setSecondZ('');
						}
						if (saved) onDirtyChange?.(false);
					});
				}}
			>
				{saveLabel}
			</button>
		</div>
	);
}

function parseCorner(dimension: string, x: string, z: string): CurrentChunk | null {
	if (!x.trim() || !z.trim() || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(dimension)) return null;
	const chunkX = Number(x);
	const chunkZ = Number(z);
	if (
		!Number.isInteger(chunkX) ||
		!Number.isInteger(chunkZ) ||
		Math.abs(chunkX) > 1875000 ||
		Math.abs(chunkZ) > 1875000
	)
		return null;
	return { dimension, chunkX, chunkZ };
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
