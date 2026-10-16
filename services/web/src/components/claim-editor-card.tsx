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
	name: string;
	color: string;
	defaultColor: string;
	customColor: string | null;
}

export function ClaimEditorCard({
	claim,
	busy,
	onDelete,
	onSave,
	summary,
	children,
	initiallyEditing = false,
}: {
	claim: EditableClaim;
	busy: boolean;
	onDelete?: () => void;
	onSave?: (name: string, color: string | null) => Promise<boolean>;
	summary?: ReactNode;
	children?: ReactNode;
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
						Chunk {claim.chunkX}, {claim.chunkZ} - {formatDimension(claim.dimension)}
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
					{children}
				</>
			)}
		</section>
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
