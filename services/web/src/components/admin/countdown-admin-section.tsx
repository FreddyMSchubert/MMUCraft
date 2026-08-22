'use client';

import { formatLondonDateTime } from './admin-api';
import type { AdminTabController } from './use-admin-tab-controller';

export function CountdownAdminSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		saveCountdown,
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
		savingCountdown,
		editingCountdownId,
		countdowns,
		resetCountdownForm,
		busyCountdownId,
		editCountdown,
		moveCountdown,
		removeCountdown,
	} = controller;
	return (
		<>
			{activeSection === 'countdowns' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Countdowns</h3>
						<p>
							Create up to four countdowns. Enter the date and time in British time.
						</p>
					</div>
					<form className="countdownForm" onSubmit={saveCountdown}>
						<label>
							Heading
							<input
								value={countdownHeading}
								onChange={(event) => {
									setCountdownHeading(event.target.value);
								}}
								maxLength={80}
								required
							/>
						</label>
						<label>
							Date and time (UK)
							<input
								type="datetime-local"
								value={countdownTarget}
								onChange={(event) => {
									setCountdownTarget(event.target.value);
								}}
								required
							/>
						</label>
						<label className="countdownAbstractInput">
							Abstract
							<textarea
								value={countdownDescription}
								onChange={(event) => {
									setCountdownDescription(event.target.value);
								}}
								maxLength={500}
								rows={3}
								required
							/>
						</label>
						<label className="countdownImageInput">
							Background image URL (optional)
							<input
								type="url"
								value={countdownBackgroundImageUrl}
								onChange={(event) => {
									setCountdownBackgroundImageUrl(event.target.value);
								}}
								placeholder="https://example.com/event.jpg"
								pattern="https://.*"
								maxLength={2000}
							/>
						</label>
						<fieldset className="countdownColorOptions">
							<legend>Colors</legend>
							<label>
								Heading{' '}
								<input
									type="color"
									value={countdownHeadingColor}
									onChange={(event) => {
										setCountdownHeadingColor(event.target.value);
									}}
								/>
								<code>{countdownHeadingColor}</code>
							</label>
							<label>
								Abstract{' '}
								<input
									type="color"
									value={countdownDescriptionColor}
									onChange={(event) => {
										setCountdownDescriptionColor(event.target.value);
									}}
								/>
								<code>{countdownDescriptionColor}</code>
							</label>
							<label>
								Background{' '}
								<input
									type="color"
									value={countdownBackgroundColor}
									onChange={(event) => {
										setCountdownBackgroundColor(event.target.value);
									}}
								/>
								<code>{countdownBackgroundColor}</code>
							</label>
							<label>
								Background opacity{' '}
								<input
									type="range"
									min="0"
									max="100"
									step="1"
									value={countdownBackgroundAlpha}
									onChange={(event) => {
										setCountdownBackgroundAlpha(Number(event.target.value));
									}}
								/>
								<output>{countdownBackgroundAlpha}%</output>
							</label>
						</fieldset>
						<div className="countdownFormActions">
							<button
								disabled={
									savingCountdown ||
									(editingCountdownId === null && countdowns.length >= 4)
								}
							>
								{savingCountdown
									? 'Saving...'
									: editingCountdownId === null
										? countdowns.length >= 4
											? 'Maximum of 4 reached'
											: 'Create countdown'
										: 'Save changes'}
							</button>
							{editingCountdownId !== null && (
								<button type="button" onClick={resetCountdownForm}>
									Cancel editing
								</button>
							)}
						</div>
					</form>

					<div className="countdownAdminList">
						{countdowns.map((countdown, index) => (
							<article key={countdown.id}>
								<div>
									<strong>{countdown.heading}</strong>
									<span>{formatLondonDateTime(countdown.targetAtUnixMs)}</span>
									{countdown.backgroundImageUrl && (
										<span>
											Background image: {countdown.backgroundImageUrl}
										</span>
									)}
									<p>{countdown.description}</p>
								</div>
								<div className="countdownAdminActions">
									<button
										type="button"
										disabled={busyCountdownId !== null}
										onClick={() => {
											editCountdown(countdown);
										}}
									>
										Edit
									</button>
									<button
										type="button"
										aria-label={`Move ${countdown.heading} up`}
										disabled={index === 0 || busyCountdownId !== null}
										onClick={() => void moveCountdown(countdown, 'up')}
									>
										↑
									</button>
									<button
										type="button"
										aria-label={`Move ${countdown.heading} down`}
										disabled={
											index === countdowns.length - 1 ||
											busyCountdownId !== null
										}
										onClick={() => void moveCountdown(countdown, 'down')}
									>
										↓
									</button>
									<button
										type="button"
										disabled={busyCountdownId !== null}
										onClick={() => void removeCountdown(countdown)}
									>
										Delete
									</button>
								</div>
							</article>
						))}
						{countdowns.length === 0 && <p>No countdowns are active.</p>}
					</div>
				</section>
			)}
		</>
	);
}
