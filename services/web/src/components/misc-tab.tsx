'use client';

import Link from 'next/link';
import { type SyntheticEvent, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { DabloonText } from '@/components/dabloon-amount';
import { apiMessage } from '@/lib/api-response';
import { useSiteSettings } from '@/lib/site-settings';

type MiscSection = 'settings' | 'gift-codes' | 'referrals';

export function MiscTab({ section }: { section?: string }) {
	const activeSection: MiscSection =
		section === 'gift-codes' || section === 'referrals' ? section : 'settings';
	return (
		<div className="miscPanel">
			<nav className="miscSubTabs" aria-label="Miscellaneous sections">
				<Link
					className={activeSection === 'settings' ? 'active' : ''}
					href="/play/misc/settings"
				>
					Website settings
				</Link>
				<Link
					className={activeSection === 'gift-codes' ? 'active' : ''}
					href="/play/misc/gift-codes"
				>
					Redeem gift code
				</Link>
				<Link
					className={activeSection === 'referrals' ? 'active' : ''}
					href="/play/misc/referrals"
				>
					Referral links
				</Link>
			</nav>
			{activeSection === 'settings' ? (
				<SettingsSection />
			) : activeSection === 'gift-codes' ? (
				<GiftCodeSection />
			) : (
				<ReferralSection />
			)}
		</div>
	);
}

function ReferralSection() {
	const [links, setLinks] = useState<{ code: string }[]>([]);
	const origin = typeof window === 'undefined' ? '' : window.location.origin;
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [copied, setCopied] = useState('');

	useEffect(() => {
		void fetch('/api/referrals', { cache: 'no-store' })
			.then(async (response) => {
				const body = (await response.json()) as { links: { code: string }[] };
				if (!response.ok)
					throw new Error(apiMessage(body, 'Could not load referral links'));
				setLinks(body.links);
			})
			.catch((caught: unknown) => {
				setError(
					caught instanceof Error ? caught.message : 'Could not load referral links',
				);
			});
	}, []);

	async function create() {
		setBusy(true);
		setError('');
		try {
			const response = await fetch('/api/referrals', { method: 'POST' });
			const body = (await response.json()) as { code: string };
			if (!response.ok) throw new Error(apiMessage(body, 'Could not create a referral link'));
			setLinks((current) => [...current, { code: body.code }]);
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not create a referral link');
		} finally {
			setBusy(false);
		}
	}

	return (
		<section className="referralSection">
			<h3>Referral links</h3>
			<p>
				Know someone who would enjoy the society? Send them a link. When they join,
				we&apos;ll thank you with 25 <DabloonText>Dabloons</DabloonText>, then another 100
				if they become a member.
			</p>
			<button
				type="button"
				disabled={busy || links.length >= 3}
				onClick={() => {
					void create();
				}}
			>
				{busy ? 'Creating...' : 'Create one-use link'}
			</button>
			{error && (
				<p role="alert" className="authError">
					{error}
				</p>
			)}
			<h4>Unused links ({links.length}/3)</h4>
			{links.length === 0 ? (
				<p>No unused links yet.</p>
			) : (
				<ul className="referralLinks">
					{links.map(({ code }) => {
						const url = `${origin}/?referral=${code}`;
						return (
							<li key={code}>
								<input
									aria-label="Referral link"
									readOnly
									value={url}
									onFocus={(event) => {
										event.target.select();
									}}
								/>
								<button
									type="button"
									onClick={() => {
										void navigator.clipboard.writeText(url).then(() => {
											setCopied(code);
										});
									}}
								>
									{copied === code ? 'Copied' : 'Copy'}
								</button>
							</li>
						);
					})}
				</ul>
			)}
		</section>
	);
}

function SettingsSection() {
	const { settings, updateSetting } = useSiteSettings();

	return (
		<section className="settingsSection">
			<h3>Website settings</h3>
			<div className="settingsList">
				<SettingToggle
					checked={settings.arachnophobiaMode}
					onChange={(checked) => {
						updateSetting('arachnophobiaMode', checked);
					}}
					title="Arachnophobia mode"
					description="Fully hides all images with spider-related stuff from the website. You may still encounter spidery things in-game."
				/>
				<SettingToggle
					checked={settings.reduce3dRendering}
					onChange={(checked) => {
						updateSetting('reduce3dRendering', checked);
					}}
					title="Reduce 3D rendering"
					description="Only loads 3D item models inside the detail view."
				/>
				<SettingToggle
					checked={settings.mysteriousSetting}
					onChange={(checked) => {
						updateSetting('mysteriousSetting', checked);
					}}
					title="the very mysterious and highly preculiar, though irregularly occurring, setting"
				/>
			</div>
		</section>
	);
}

function SettingToggle({
	checked,
	onChange,
	title,
	description,
}: {
	checked: boolean;
	onChange: (checked: boolean) => void;
	title: string;
	description?: string;
}) {
	return (
		<label className="settingToggle">
			<span>
				<strong>{title}</strong>
				{description && <small>{description}</small>}
			</span>
			<input
				type="checkbox"
				checked={checked}
				onChange={(event) => {
					onChange(event.target.checked);
				}}
			/>
			<i aria-hidden="true" />
		</label>
	);
}

function GiftCodeSection() {
	const { showAlert } = useSiteAlert();
	const [code, setCode] = useState('');
	const [busy, setBusy] = useState(false);

	function redeem(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		void (async () => {
			try {
				const response = await fetch('/api/gift-codes/redeem', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ code }),
				});
				const body = await response.json().catch(() => null);
				if (!response.ok) throw new Error(apiMessage(body, 'Could not redeem gift code'));
				setCode('');
				await showAlert({
					title: 'Gift code redeemed',
					message: apiMessage(
						body,
						'The reward was added to your Minecraft account. Check your Dabloon balance in-game.',
					),
					tone: 'success',
				});
			} catch (caught) {
				await showAlert({
					title: 'Could not redeem this gift code',
					message:
						caught instanceof Error
							? caught.message
							: 'Check the code, make sure you are online in Minecraft, and try again.',
					tone: 'danger',
				});
			} finally {
				setBusy(false);
			}
		})();
	}

	return (
		<section className="giftCodeSection">
			<div>
				<h3>Gift codes</h3>
				<p>
					Enter a gift code while online to receive <DabloonText>Dabloons</DabloonText>{' '}
					in-game.
				</p>
			</div>
			<div className="giftInstructions">
				<p>
					Codes may expire or be first-come, first-served. Failed offline attempts do not
					use the code.
				</p>
			</div>
			<form className="redeemForm" onSubmit={redeem}>
				<label htmlFor="gift-code">Gift code</label>
				<div>
					<input
						id="gift-code"
						value={code}
						onChange={(event) => {
							setCode(event.target.value);
						}}
						placeholder="Enter your code"
						maxLength={64}
						required
					/>
					<button disabled={busy}>{busy ? 'Redeeming...' : 'Redeem'}</button>
				</div>
			</form>
		</section>
	);
}
