'use client';

import { useState } from 'react';
import { DabloonAmount } from '@/components/dabloon-amount';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';

export function BecomeMemberTab({ onPurchased }: { onPurchased: () => Promise<void> }) {
	const { confirm, showAlert } = useSiteAlert();
	const [busy, setBusy] = useState(false);

	async function purchase() {
		if (
			busy ||
			!(await confirm({
				title: 'Become a member?',
				message:
					'This will take 300 Dabloons from your Minecraft balance. You must be online on the server.',
				confirmLabel: 'Pay 300 Dabloons',
			}))
		)
			return;
		setBusy(true);
		try {
			const response = await fetch('/api/membership/purchase', { method: 'POST' });
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not buy membership'));
			await showAlert({
				title: 'Membership active',
				message:
					'You now have member benefits. Your Minecraft name tag will still say External.',
				tone: 'success',
			});
		} catch (error) {
			await showAlert({
				title: 'Could not buy membership',
				message:
					error instanceof Error
						? error.message
						: 'Please check your account and try again.',
				tone: 'danger',
			});
			return;
		} finally {
			setBusy(false);
		}
		void onPurchased().catch(() => {
			window.location.reload();
		});
	}

	return (
		<section className="membershipPanel">
			<div className="membershipIntro">
				<span className="membershipEyebrow">Invited by a society member</span>
				<h3>Become a member</h3>
				<p>
					Because the player who invited you is a member, you can activate membership too.
				</p>
				<p>
					Get access to members-only shop items, the membership duck, member rewards and
					discounts, and the other gameplay perks. Your name tag will still say External.
				</p>
			</div>
			<div className="membershipPurchase">
				<div>
					<small>One-time price</small>
					<strong>
						<DabloonAmount amount={300} format="full" />
					</strong>
				</div>
				<p>
					Join the Minecraft server before paying. The Dabloons will come from your
					in-game balance.
				</p>
				<button type="button" disabled={busy} onClick={() => void purchase()}>
					{busy ? 'Processing...' : 'Pay 300 Dabloons'}
				</button>
			</div>
		</section>
	);
}
