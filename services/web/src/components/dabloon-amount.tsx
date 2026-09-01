import { Fragment, type ReactNode } from 'react';
import {
	DABLOON_SYMBOL,
	formatDabloonDelta,
	formatDabloons,
	formatDabloonWord,
} from '@/lib/dabloons';

type DabloonTone = 'default' | 'positive' | 'negative' | 'inherit';

export function DabloonAmount({
	amount,
	format = 'compact',
	tone = 'default',
}: {
	amount: number;
	format?: 'compact' | 'full' | 'delta';
	tone?: DabloonTone;
}) {
	const effectiveTone =
		format === 'delta' && tone === 'default' ? (amount < 0 ? 'negative' : 'positive') : tone;
	const text =
		format === 'full'
			? formatDabloonWord(amount)
			: format === 'delta'
				? formatDabloonDelta(amount)
				: formatDabloons(amount);

	return (
		<span
			className={`dabloonAmount dabloonTone-${effectiveTone}`}
			aria-label={readableDabloons(amount, format === 'delta')}
		>
			{text}
		</span>
	);
}

export function DabloonText({ children }: { children: string }) {
	const amountPattern = `[+−-]?[\\d,]+\\s+${DABLOON_SYMBOL}`;
	const parts = children.split(new RegExp(`(${amountPattern})`, 'gi'));
	return parts.map((part, index) => {
		if (new RegExp(`^${amountPattern}$`, 'i').test(part)) {
			const readable = `${part.replace(DABLOON_SYMBOL, '').trim()} Dabloons`;
			const tone = /^[−-]/.test(part) ? 'negative' : 'default';
			return (
				<span
					className={`dabloonAmount dabloonTone-${tone}`}
					aria-label={readable}
					key={`${part}-${index}`}
				>
					{part}
				</span>
			);
		}

		return <Fragment key={`${part}-${index}`}>{part}</Fragment>;
	});
}

export function decorateDabloonText(node: ReactNode) {
	return typeof node === 'string' ? <DabloonText>{node}</DabloonText> : node;
}

function readableDabloons(amount: number, delta: boolean) {
	const absolute = Math.abs(amount).toLocaleString('en-US');
	const word = Math.abs(amount) === 1 ? 'Dabloon' : 'Dabloons';
	if (!delta) return `${amount.toLocaleString('en-US')} ${word}`;
	return `${amount < 0 ? 'lost' : 'gained'} ${absolute} ${word}`;
}
