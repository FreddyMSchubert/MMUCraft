'use client';

import Image from 'next/image';
import { useState, type CSSProperties, type ReactNode } from 'react';
import { PlayerName, type PlayerEmoji } from '@/components/player-name';
import { PlayerSelector } from '@/components/player-selector';
import { useSiteAlert } from '@/components/site-alert';

export interface ClaimPerson {
	id: number;
	minecraftUsername: string;
	preferredName: string;
	pronouns: string;
	color: string;
	avatarUrl: string | null;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
	isOwner?: boolean;
}

export interface EditableClaim {
	id: string;
	dimension: string;
	chunkX: number;
	chunkZ: number;
	chunkXEnd?: number;
	chunkZEnd?: number;
	name: string;
	color: string;
	defaultColor: string;
	customColor: string | null;
	permissions: number;
}

export interface ClaimCopySource {
	id: string;
	name: string;
	dimension: string;
	chunkX: number;
	chunkZ: number;
	chunkXEnd?: number;
	chunkZEnd?: number;
}

export interface ClaimSettingsChange {
	permissions?: number;
	copyFromClaimId?: string;
}

const SETTINGS = [
	['Hit hostile mobs', 1 << 0],
	['Hit neutral mobs', 1 << 1],
	['Hit passive mobs', 1 << 2],
	['PvP', 1 << 3],
	['Press buttons and flip levers', 1 << 4],
	['Open doors, trapdoors, and fence gates', 1 << 5],
	['Break blocks', 1 << 6],
	['Place blocks', 1 << 7],
	['Open containers', 1 << 8],
	['Open other blocks', 1 << 9],
	['Sleep in beds and sit on cushions', 1 << 10],
	['Trade with villagers', 1 << 11],
	['Interact with mobs and entities', 1 << 12],
	['Damage blocks with explosions', 1 << 13],
	['Glide with a glider or elytra', 1 << 14],
	['Pick up items', 1 << 15],
	['Use projectiles', 1 << 16],
] as const;

export function ClaimEditorCard({
	claim,
	busy,
	onDelete,
	onSave,
	onSettings,
	copySources = [],
	summary,
	children,
	areaEditor,
	initiallyEditing = false,
}: {
	claim: EditableClaim;
	busy: boolean;
	onDelete?: () => void;
	onSave?: (name: string, color: string | null) => Promise<boolean>;
	onSettings?: (change: ClaimSettingsChange) => Promise<boolean>;
	copySources?: ClaimCopySource[];
	summary?: ReactNode;
	children?: ReactNode;
	areaEditor?: ReactNode;
	initiallyEditing?: boolean;
}) {
	const [editing, setEditing] = useState(initiallyEditing);

	return (
		<section className={`claimCard${editing ? '' : ' claimCardCollapsed'}`}>
			<div className="claimHeader">
				<div className="claimIdentity">
					<h4
						className="claimName"
						style={{ '--claim-color': claim.color } as CSSProperties}
					>
						{claim.name}
					</h4>
					<span>
						{formatClaimArea(claim)} - {formatDimension(claim.dimension)}
					</span>
				</div>
				{!editing && summary}
				{editing && onDelete && (
					<button type="button" disabled={busy} onClick={onDelete}>
						Delete claim
					</button>
				)}
				{!editing && onSave && (
					<button
						type="button"
						disabled={busy}
						aria-expanded={false}
						onClick={() => {
							setEditing(true);
						}}
					>
						Edit
					</button>
				)}
			</div>

			{editing && onSave && (
				<>
					<ClaimAppearanceForm
						claim={claim}
						busy={busy}
						onSave={async (name, color) => {
							if (await onSave(name, color)) setEditing(false);
						}}
					/>
					{areaEditor && (
						<details className="claimEditorSection">
							<summary>Area</summary>
							{areaEditor}
						</details>
					)}
					{onSettings && (
						<details className="claimEditorSection">
							<summary>Settings</summary>
							<ClaimSettingsEditor
								key={`${claim.id}:${claim.permissions}`}
								claim={claim}
								busy={busy}
								copySources={copySources}
								onSave={onSettings}
							/>
						</details>
					)}
					{children && (
						<details className="claimEditorSection">
							<summary>Members</summary>
							{children}
						</details>
					)}
				</>
			)}
		</section>
	);
}

function ClaimSettingsEditor({
	claim,
	busy,
	copySources,
	onSave,
}: {
	claim: EditableClaim;
	busy: boolean;
	copySources: ClaimCopySource[];
	onSave: (change: ClaimSettingsChange) => Promise<boolean>;
}) {
	const [permissions, setPermissions] = useState(claim.permissions);
	const [source, setSource] = useState('');
	return (
		<div className="claimSettings">
			<div className="claimSettingsCopy">
				<label htmlFor={`copy-${claim.id}`}>Copy settings from</label>
				<select
					id={`copy-${claim.id}`}
					value={source}
					disabled={busy}
					onChange={(event) => {
						setSource(event.target.value);
					}}
				>
					<option value="">Select another claim</option>
					{copySources
						.filter((candidate) => candidate.id !== claim.id)
						.map((candidate) => (
							<option key={candidate.id} value={candidate.id}>
								{candidate.name} ({formatClaimArea(candidate)} in{' '}
								{formatDimension(candidate.dimension)})
							</option>
						))}
				</select>
				<button
					type="button"
					disabled={busy || !source}
					onClick={() => {
						void onSave({ copyFromClaimId: source }).then((saved) => {
							if (saved) setPermissions(claim.permissions);
						});
					}}
				>
					Copy
				</button>
			</div>
			<p>
				Checked actions are allowed for other players. Claim members, or MMU_Admin on server
				claims, always have full access.
			</p>
			<div className="claimSettingsList">
				{SETTINGS.map(([label, bit]) => (
					<label key={bit}>
						<input
							type="checkbox"
							checked={(permissions & bit) !== 0}
							disabled={busy}
							onChange={(event) => {
								setPermissions((current) =>
									event.target.checked ? current | bit : current & ~bit,
								);
							}}
						/>
						{label}
					</label>
				))}
			</div>
			<button
				type="button"
				disabled={busy || permissions === claim.permissions}
				onClick={() => void onSave({ permissions })}
			>
				Save settings
			</button>
		</div>
	);
}

export function ClaimMembersSummary({ members }: { members: ClaimPerson[] }) {
	return (
		<div className="claimMembersSummary">
			<span>Members</span>
			<div className="claimMemberInlineList">
				{members.map((person) => (
					<span className="claimMemberInline" key={person.id}>
						<PlayerHead person={person} />
						<PlayerName
							name={person.minecraftUsername}
							color={person.color}
							isCommittee={person.isCommittee}
							customEmojis={person.customEmojis}
						/>
					</span>
				))}
			</div>
		</div>
	);
}

export function ClaimMembersEditor({
	claimId,
	members,
	candidates,
	busy,
	onAdd,
	onRemove,
}: {
	claimId: string;
	members: ClaimPerson[];
	candidates: ClaimPerson[];
	busy: boolean;
	onAdd: (person: ClaimPerson) => Promise<boolean>;
	onRemove: (person: ClaimPerson) => void;
}) {
	const { showAlert } = useSiteAlert();
	const [search, setSearch] = useState('');
	const memberIds = new Set(members.map((person) => person.id));
	const available = candidates.filter((person) => !memberIds.has(person.id));

	async function addMember() {
		const candidate = available.find(
			(person) =>
				person.minecraftUsername.localeCompare(search.trim(), 'en', {
					sensitivity: 'base',
				}) === 0,
		);
		if (!candidate) {
			await showAlert({
				title: 'Choose a valid player',
				message:
					'Select a player from the suggestions so the correct Minecraft account receives access.',
				tone: 'danger',
			});
			return;
		}
		if (await onAdd(candidate)) setSearch('');
	}

	return (
		<div className="claimMembers">
			{members.map((person) => (
				<div className="claimMember" key={person.id}>
					<PlayerHead person={person} />
					<div>
						<strong>
							<PlayerName
								name={person.minecraftUsername}
								color={person.color}
								isCommittee={person.isCommittee}
								customEmojis={person.customEmojis}
							/>
						</strong>
						{person.pronouns && <span>{person.pronouns}</span>}
					</div>
					{person.isOwner ? (
						<small>Owner</small>
					) : (
						<button
							type="button"
							disabled={busy}
							onClick={() => {
								onRemove(person);
							}}
						>
							Remove
						</button>
					)}
				</div>
			))}
			<form
				className="claimMemberSearch"
				onSubmit={(event) => {
					event.preventDefault();
					void addMember();
				}}
			>
				<PlayerSelector
					datalistId={`claim-candidates-${claimId}`}
					options={available}
					value={search}
					onChange={setSearch}
					placeholder="Search server players"
					disabled={busy || available.length === 0}
				/>
				<button type="submit" disabled={busy || available.length === 0}>
					Add
				</button>
			</form>
		</div>
	);
}

function PlayerHead({ person }: { person: ClaimPerson }) {
	const label = `${person.minecraftUsername} head`;
	if (!person.avatarUrl) {
		return (
			<span
				className="playerHead playerHead-small playerHeadFallback"
				role="img"
				aria-label={label}
			>
				{person.minecraftUsername[0]}
			</span>
		);
	}
	return (
		<Image
			unoptimized
			className="playerHead playerHead-small"
			src={person.avatarUrl}
			alt={label}
			width={42}
			height={42}
		/>
	);
}

function ClaimAppearanceForm({
	claim,
	busy,
	onSave,
}: {
	claim: EditableClaim;
	busy: boolean;
	onSave: (name: string, color: string | null) => Promise<void>;
}) {
	const [color, setColor] = useState<string | null>(claim.customColor);
	return (
		<form
			className="claimAppearanceForm"
			onSubmit={(event) => {
				event.preventDefault();
				const name = new FormData(event.currentTarget).get('name');
				void onSave(typeof name === 'string' ? name : '', color);
			}}
		>
			<label>
				<span>Claim name (50 characters maximum)</span>
				<input
					name="name"
					defaultValue={claim.name}
					maxLength={50}
					required
					disabled={busy}
				/>
			</label>
			<label className="claimColorInput">
				<span>Color</span>
				<input
					type="color"
					value={color ?? claim.defaultColor}
					onChange={(event) => {
						setColor(event.target.value);
					}}
					disabled={busy}
				/>
			</label>
			<button
				type="button"
				disabled={busy || color === null}
				onClick={() => {
					setColor(null);
				}}
			>
				Reset color
			</button>
			<button type="submit" disabled={busy}>
				Save
			</button>
		</form>
	);
}

export function formatDimension(dimension: string) {
	return dimension.replace('minecraft:', '').replaceAll('_', ' ');
}

export function formatClaimArea(
	claim: Pick<EditableClaim, 'chunkX' | 'chunkZ' | 'chunkXEnd' | 'chunkZEnd'>,
) {
	const endX = claim.chunkXEnd ?? claim.chunkX;
	const endZ = claim.chunkZEnd ?? claim.chunkZ;
	return endX === claim.chunkX && endZ === claim.chunkZ
		? `Chunk ${claim.chunkX}, ${claim.chunkZ}`
		: `Chunks ${claim.chunkX}, ${claim.chunkZ} to ${endX}, ${endZ}`;
}
