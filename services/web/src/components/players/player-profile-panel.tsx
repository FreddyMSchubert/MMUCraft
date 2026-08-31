'use client';

import { useState, type ReactElement } from 'react';
import { MiniFishCompendium } from '@/components/fishing-tab';
import { PlayerName } from '@/components/player-name';
import { apiMessage } from '@/lib/api-response';
import { PlayerHead } from './player-table-cells';
import { PlayerStatsList } from './player-statistics-list';
import { formatBase, hasBase } from './player-display-format';
import { PROFILE_TEXT_LIMITS, type PlayerSummary, type StatOption } from './player-data.types';

export function PlayerProfilePanel({
	player,
	statOptions,
	onBack,
	onSaved,
	onError,
}: {
	player: PlayerSummary;
	statOptions: StatOption[];
	onBack: () => void;
	onSaved: () => void;
	onError: (message: string) => void;
}) {
	const [editing, setEditing] = useState(false);

	return (
		<section className="playerProfilePanel">
			<div className="playerProfileNav">
				<button type="button" onClick={onBack}>
					Back
				</button>
			</div>
			{editing && player.canEditProfile ? (
				<PlayerProfileForm
					player={player}
					onCancel={() => {
						setEditing(false);
					}}
					onSaved={() => {
						setEditing(false);
						onSaved();
					}}
					onError={onError}
				/>
			) : (
				<>
					<div className="playerProfileTop">
						<PlayerHead player={player} size="large" />
						<div className="playerProfileIdentity">
							<h4>
								<PlayerName
									name={player.minecraftUsername}
									color={player.profile.color}
								/>
							</h4>
							<ProfileFacts player={player} />
						</div>
						{player.canEditProfile && (
							<button
								type="button"
								onClick={() => {
									setEditing(true);
								}}
							>
								Edit profile
							</button>
						)}
					</div>
					<p className="playerBio">{player.profile.bio || 'No bio yet.'}</p>
					<PlayerStatsList player={player} statOptions={statOptions} />
					<MiniFishCompendium userId={player.id} />
				</>
			)}
		</section>
	);
}

export function ProfileFacts({ player }: { player: PlayerSummary }) {
	const profile = player.profile;
	const facts = [
		player.isExternal
			? { label: 'MMU affiliation', value: 'External player (not at MMU)' }
			: null,
		player.isExternal
			? {
					label: 'Responsible player',
					value:
						player.responsibleMinecraftUsername && player.responsiblePlayerColor ? (
							<PlayerName
								name={player.responsibleMinecraftUsername}
								color={player.responsiblePlayerColor}
							/>
						) : (
							'Unknown player'
						),
				}
			: null,
		{ label: 'Society member', value: player.isMember ? 'Yes' : 'No' },
		{ label: 'Committee', value: player.isCommittee ? 'Yes' : 'No' },
		profile.preferredName ? { label: 'Nickname', value: profile.preferredName } : null,
		profile.pronouns ? { label: 'Pronouns', value: profile.pronouns } : null,
		profile.courseYear ? { label: 'Course / Year', value: profile.courseYear } : null,
		profile.discordUsername
			? { label: 'Discord username', value: profile.discordUsername }
			: null,
		hasBase(profile) ? { label: 'Base location', value: formatBase(profile.base) } : null,
	].filter((fact): fact is { label: string; value: string | ReactElement } => Boolean(fact));

	if (facts.length === 0) {
		return <p className="playerProfileEmpty">No profile details yet.</p>;
	}

	return (
		<dl className="playerFacts">
			{facts.map((fact) => (
				<div key={fact.label}>
					<dt>{fact.label}</dt>
					<dd>{fact.value}</dd>
				</div>
			))}
		</dl>
	);
}

export function PlayerProfileForm({
	player,
	onCancel,
	onSaved,
	onError,
}: {
	player: PlayerSummary;
	onCancel: () => void;
	onSaved: () => void;
	onError: (message: string) => void;
}) {
	const [preferredName, setPreferredName] = useState(player.profile.preferredName);
	const [pronouns, setPronouns] = useState(player.profile.pronouns);
	const [courseYear, setCourseYear] = useState(player.profile.courseYear);
	const [discordUsername, setDiscordUsername] = useState(player.profile.discordUsername);
	const [baseX, setBaseX] = useState(player.profile.base.x?.toString() ?? '');
	const [baseY, setBaseY] = useState(player.profile.base.y?.toString() ?? '');
	const [baseZ, setBaseZ] = useState(player.profile.base.z?.toString() ?? '');
	const [bio, setBio] = useState(player.profile.bio);
	const [color, setColor] = useState<string | null>(player.profile.customColor);
	const [showDeathCounter, setShowDeathCounter] = useState(player.profile.showDeathCounter);
	const [saving, setSaving] = useState(false);
	const [loadingLocation, setLoadingLocation] = useState(false);
	const displayedColor = color ?? player.profile.defaultColor;

	async function loadCurrentLocation() {
		setLoadingLocation(true);
		onError('');
		try {
			const response = await fetch(`/api/players/${player.id}/current-location`, {
				cache: 'no-store',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) {
				throw new Error(apiMessage(body, 'Failed to load the current location'));
			}
			const location = body as { x: number; y: number; z: number };
			setBaseX(String(location.x));
			setBaseY(String(location.y));
			setBaseZ(String(location.z));
		} catch (caught) {
			onError(
				caught instanceof Error ? caught.message : 'Failed to load the current location',
			);
		} finally {
			setLoadingLocation(false);
		}
	}

	async function save() {
		setSaving(true);
		onError('');

		try {
			const response = await fetch(`/api/players/${player.id}/profile`, {
				method: 'PATCH',
				headers: {
					'content-type': 'application/json',
				},
				body: JSON.stringify({
					preferredName,
					pronouns,
					courseYear,
					discordUsername,
					baseX: baseX === '' ? null : Number(baseX),
					baseY: baseY === '' ? null : Number(baseY),
					baseZ: baseZ === '' ? null : Number(baseZ),
					bio,
					color,
					showDeathCounter,
				}),
			});
			const body = await response.json().catch(() => null);

			if (!response.ok) {
				throw new Error(apiMessage(body, 'Failed to save profile'));
			}

			onSaved();
		} catch (caught) {
			onError(caught instanceof Error ? caught.message : 'Failed to save profile');
		} finally {
			setSaving(false);
		}
	}

	return (
		<form
			className="playerProfileForm"
			onSubmit={(event) => {
				event.preventDefault();
				void save();
			}}
		>
			<div className="playerProfileFormHeader">
				<div>
					<h4>Edit profile</h4>
					<p>{player.minecraftUsername}</p>
				</div>
				<div className="playerProfileActions">
					<button type="button" onClick={onCancel} disabled={saving}>
						Cancel
					</button>
					<button type="submit" disabled={saving}>
						{saving ? 'Saving...' : 'Save'}
					</button>
				</div>
			</div>
			<p className="playerProfileHint">
				Everything you enter here is visible to every society member. Be open and share a
				little about yourself so people know who they&apos;re talking to. Your nickname and
				pronouns are also displayed in-game.
			</p>
			<label>
				<span>Nickname</span>
				<input
					value={preferredName}
					onChange={(event) => {
						setPreferredName(event.target.value);
					}}
					maxLength={PROFILE_TEXT_LIMITS.preferredName}
				/>
			</label>
			<label>
				<span>Pronouns</span>
				<input
					value={pronouns}
					onChange={(event) => {
						setPronouns(event.target.value);
					}}
					maxLength={PROFILE_TEXT_LIMITS.pronouns}
				/>
			</label>
			<label>
				<span>Course / Year</span>
				<input
					value={courseYear}
					onChange={(event) => {
						setCourseYear(event.target.value);
					}}
					maxLength={PROFILE_TEXT_LIMITS.courseYear}
				/>
			</label>
			<label>
				<span>Discord username</span>
				<input
					value={discordUsername}
					onChange={(event) => {
						setDiscordUsername(event.target.value);
					}}
					maxLength={PROFILE_TEXT_LIMITS.discordUsername}
				/>
			</label>
			<div className="playerBaseInputs">
				<div className="playerBaseHeader">
					<span>Base location (XYZ)</span>
					<button
						type="button"
						disabled={saving || loadingLocation}
						onClick={() => void loadCurrentLocation()}
					>
						{loadingLocation ? 'Loading...' : 'Use current location'}
					</button>
				</div>
				<label>
					<span>X</span>
					<input
						type="number"
						value={baseX}
						onChange={(event) => {
							setBaseX(event.target.value);
						}}
					/>
				</label>
				<label>
					<span>Y</span>
					<input
						type="number"
						value={baseY}
						onChange={(event) => {
							setBaseY(event.target.value);
						}}
					/>
				</label>
				<label>
					<span>Z</span>
					<input
						type="number"
						value={baseZ}
						onChange={(event) => {
							setBaseZ(event.target.value);
						}}
					/>
				</label>
			</div>
			<label className="playerBioInput">
				<span>Bio</span>
				<textarea
					value={bio}
					onChange={(event) => {
						setBio(event.target.value);
					}}
					maxLength={PROFILE_TEXT_LIMITS.bio}
					rows={4}
				/>
			</label>
			<div className="playerColorField">
				<label>
					<span>Player color</span>
					<input
						type="color"
						value={displayedColor}
						onChange={(event) => {
							setColor(event.target.value);
						}}
					/>
				</label>
				<button
					type="button"
					disabled={saving || color === null}
					onClick={() => {
						setColor(null);
					}}
				>
					Reset color
				</button>
			</div>
			<label className="settingToggle">
				<span>
					<strong>Show death counter in nametag</strong>
					<small>Shows your death and used-totem count below your in-game nametag.</small>
				</span>
				<input
					type="checkbox"
					checked={showDeathCounter}
					onChange={(event) => {
						setShowDeathCounter(event.target.checked);
					}}
				/>
				<i aria-hidden="true" />
			</label>
		</form>
	);
}
