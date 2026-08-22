'use client';

import { type SyntheticEvent, useEffect, useState } from 'react';
import type { Dispatch, ReactNode, SetStateAction } from 'react';
import { apiBody, apiMessage, errorMessage } from './admin-api';
import type { AdminSection } from './admin-data.types';
import { makeDifferentGiftCodeSuggestion, makeGiftCodeSuggestion } from './gift-code-suggestions';

export function useGiftCodeAdministration({
	activeSection,
	reload,
	setError,
	setMessage,
}: {
	activeSection: AdminSection;
	reload: () => Promise<void>;
	setError: Dispatch<SetStateAction<string>>;
	setMessage: Dispatch<SetStateAction<ReactNode>>;
}) {
	const [suggestion, setSuggestion] = useState('enchanted-pickaxe');
	const [code, setCode] = useState('');
	const [amount, setAmount] = useState('');
	const [redemptionMode, setRedemptionMode] = useState<'single' | 'per_user'>('single');
	const [membersOnly, setMembersOnly] = useState(false);
	const [expiresAt, setExpiresAt] = useState('');
	const [showAllGiftCodes, setShowAllGiftCodes] = useState(false);
	const [savingGiftCode, setSavingGiftCode] = useState(false);

	useEffect(() => {
		if (activeSection !== 'gifts') return;
		const refreshSuggestion = () => {
			setSuggestion((current) => makeDifferentGiftCodeSuggestion(current));
		};
		const initialTimer = window.setTimeout(refreshSuggestion, 0);
		const interval = window.setInterval(refreshSuggestion, 5_000);
		return () => {
			window.clearTimeout(initialTimer);
			window.clearInterval(interval);
		};
	}, [activeSection]);

	function createGiftCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setSavingGiftCode(true);
		setError('');
		setMessage('');

		void (async () => {
			try {
				const response = await fetch('/api/admin/gift-codes', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						code,
						amountDabloons: Number(amount),
						redemptionMode,
						membersOnly,
						expiresAtUnixMs: expiresAt ? new Date(expiresAt).getTime() : null,
					}),
				});
				const body = await response.json().catch(() => null);
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to create gift code'));
				const created = apiBody<{ code: string; amountDabloons: number }>(body);

				setCode('');
				setAmount('');
				setRedemptionMode('single');
				setMembersOnly(false);
				setExpiresAt('');
				setSuggestion(makeGiftCodeSuggestion());
				setMessage(`Created ${created.code} for ${created.amountDabloons} dabloons.`);
				await reload();
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to create gift code'));
			} finally {
				setSavingGiftCode(false);
			}
		})();
	}

	return {
		suggestion,
		code,
		setCode,
		amount,
		setAmount,
		redemptionMode,
		setRedemptionMode,
		membersOnly,
		setMembersOnly,
		expiresAt,
		setExpiresAt,
		showAllGiftCodes,
		setShowAllGiftCodes,
		savingGiftCode,
		createGiftCode,
	};
}
