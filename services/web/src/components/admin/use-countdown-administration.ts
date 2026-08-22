'use client';

import { type SyntheticEvent, useState } from 'react';
import type { Dispatch, ReactNode, SetStateAction } from 'react';
import type { Countdown } from '@/components/dynamic-countdowns';
import { apiBody, apiMessage, errorMessage, formatLondonInput } from './admin-api';

export function useCountdownAdministration({
	setCountdowns,
	reload,
	setError,
	setMessage,
}: {
	setCountdowns: Dispatch<SetStateAction<Countdown[]>>;
	reload: () => Promise<void>;
	setError: Dispatch<SetStateAction<string>>;
	setMessage: Dispatch<SetStateAction<ReactNode>>;
}) {
	const [countdownHeading, setCountdownHeading] = useState('');
	const [countdownTarget, setCountdownTarget] = useState('');
	const [countdownDescription, setCountdownDescription] = useState('');
	const [countdownHeadingColor, setCountdownHeadingColor] = useState('#ffffff');
	const [countdownDescriptionColor, setCountdownDescriptionColor] = useState('#ffffff');
	const [countdownBackgroundColor, setCountdownBackgroundColor] = useState('#000000');
	const [countdownBackgroundAlpha, setCountdownBackgroundAlpha] = useState(78);
	const [countdownBackgroundImageUrl, setCountdownBackgroundImageUrl] = useState('');
	const [editingCountdownId, setEditingCountdownId] = useState<number | null>(null);
	const [busyCountdownId, setBusyCountdownId] = useState<number | null>(null);
	const [savingCountdown, setSavingCountdown] = useState(false);

	function saveCountdown(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setSavingCountdown(true);
		setError('');
		setMessage('');
		void (async () => {
			try {
				const editing = editingCountdownId !== null;
				const response = await fetch(
					editingCountdownId === null
						? '/api/admin/countdowns'
						: `/api/admin/countdowns/${editingCountdownId}`,
					{
						method: editingCountdownId === null ? 'POST' : 'PATCH',
						headers: { 'content-type': 'application/json' },
						body: JSON.stringify({
							heading: countdownHeading,
							target: countdownTarget,
							description: countdownDescription,
							headingColor: countdownHeadingColor,
							descriptionColor: countdownDescriptionColor,
							backgroundColor: countdownBackgroundColor,
							backgroundAlpha: countdownBackgroundAlpha,
							backgroundImageUrl: countdownBackgroundImageUrl,
						}),
					},
				);
				const body = await response.json().catch(() => null);
				if (!response.ok) {
					throw new Error(
						apiMessage(
							body,
							`Failed to ${editing ? 'update' : 'create'} the countdown`,
						),
					);
				}
				const result = apiBody<{ heading: string }>(body);
				setMessage(`${editing ? 'Updated' : 'Created'} the “${result.heading}” countdown.`);
				resetCountdownForm();
				await reload();
				window.dispatchEvent(new Event('countdowns-change'));
			} catch (caught) {
				setError(
					errorMessage(
						caught,
						`Failed to ${editingCountdownId === null ? 'create' : 'update'} the countdown`,
					),
				);
			} finally {
				setSavingCountdown(false);
			}
		})();
	}

	function editCountdown(countdown: Countdown) {
		setEditingCountdownId(countdown.id);
		setCountdownHeading(countdown.heading);
		setCountdownTarget(formatLondonInput(countdown.targetAtUnixMs));
		setCountdownDescription(countdown.description);
		setCountdownHeadingColor(countdown.headingColor);
		setCountdownDescriptionColor(countdown.descriptionColor);
		setCountdownBackgroundColor(countdown.backgroundColor);
		setCountdownBackgroundAlpha(countdown.backgroundAlpha);
		setCountdownBackgroundImageUrl(countdown.backgroundImageUrl ?? '');
		setError('');
		setMessage('');
	}

	function resetCountdownForm() {
		setEditingCountdownId(null);
		setCountdownHeading('');
		setCountdownTarget('');
		setCountdownDescription('');
		setCountdownHeadingColor('#ffffff');
		setCountdownDescriptionColor('#ffffff');
		setCountdownBackgroundColor('#000000');
		setCountdownBackgroundAlpha(78);
		setCountdownBackgroundImageUrl('');
	}

	async function moveCountdown(countdown: Countdown, direction: 'up' | 'down') {
		setBusyCountdownId(countdown.id);
		setError('');
		try {
			const response = await fetch(`/api/admin/countdowns/${countdown.id}/order`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ direction }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to reorder the countdown'));
			setCountdowns(apiBody<{ countdowns: Countdown[] }>(body).countdowns);
			window.dispatchEvent(new Event('countdowns-change'));
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to reorder the countdown'));
		} finally {
			setBusyCountdownId(null);
		}
	}

	async function removeCountdown(countdown: Countdown) {
		if (!window.confirm(`Delete the “${countdown.heading}” countdown?`)) return;
		setBusyCountdownId(countdown.id);
		setError('');
		try {
			const response = await fetch(`/api/admin/countdowns/${countdown.id}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to delete the countdown'));
			setCountdowns((current) =>
				current.filter((candidate) => candidate.id !== countdown.id),
			);
			if (editingCountdownId === countdown.id) resetCountdownForm();
			setMessage(`Deleted the “${countdown.heading}” countdown.`);
			window.dispatchEvent(new Event('countdowns-change'));
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to delete the countdown'));
		} finally {
			setBusyCountdownId(null);
		}
	}

	return {
		countdownHeading,
		setCountdownHeading,
		countdownTarget,
		setCountdownTarget,
		countdownDescription,
		setCountdownDescription,
		countdownHeadingColor,
		setCountdownHeadingColor,
		countdownDescriptionColor,
		setCountdownDescriptionColor,
		countdownBackgroundColor,
		setCountdownBackgroundColor,
		countdownBackgroundAlpha,
		setCountdownBackgroundAlpha,
		countdownBackgroundImageUrl,
		setCountdownBackgroundImageUrl,
		editingCountdownId,
		busyCountdownId,
		savingCountdown,
		saveCountdown,
		editCountdown,
		resetCountdownForm,
		moveCountdown,
		removeCountdown,
	};
}
