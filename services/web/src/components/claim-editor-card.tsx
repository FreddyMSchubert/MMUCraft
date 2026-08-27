'use client';

import { useState, type CSSProperties, type ReactNode } from 'react';

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
	children,
}: {
	claim: EditableClaim;
	busy: boolean;
	onDelete: () => void;
	onSave: (name: string, color: string | null) => Promise<void>;
	children?: ReactNode;
}) {
	return (
		<section className="claimCard">
			<div className="claimHeader">
				<div>
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
				<button type="button" disabled={busy} onClick={onDelete}>
					Delete claim
				</button>
			</div>

			<ClaimAppearanceForm claim={claim} busy={busy} onSave={onSave} />
			{children}
		</section>
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
				<span>Claim name (20 characters maximum)</span>
				<input
					name="name"
					defaultValue={claim.name}
					maxLength={20}
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
