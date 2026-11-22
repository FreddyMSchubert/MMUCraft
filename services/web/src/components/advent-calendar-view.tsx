'use client';

import type { ComponentProps } from 'react';
import { AdventGrid } from '@/components/advent-grid';
import { DabloonAmount } from '@/components/dabloon-amount';

export function AdventCalendarView({
	streakRate,
	error,
	notice,
	gridKey,
	grid,
}: {
	streakRate: number;
	error?: string;
	notice?: string;
	gridKey: string | number;
	grid: ComponentProps<typeof AdventGrid>;
}) {
	return (
		<section className="adventPanel">
			<div className="adventHeader">
				<h3>Advent Calendar</h3>
				<div className="adventStreak">
					<span>Christmas streak</span>
					<strong>
						<DabloonAmount amount={streakRate} />
					</strong>
					<small>Resets if you miss a day.</small>
				</div>
			</div>
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="adventNotice" role="status">
					{notice}
				</p>
			)}
			<AdventGrid key={gridKey} {...grid} />
		</section>
	);
}
