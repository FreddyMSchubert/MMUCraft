'use client';

import { formatExpiry } from './admin-api';
import type { AdminTabController } from './use-admin-tab-controller';

export function GiftCodeAdminSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		createGiftCode,
		code,
		setCode,
		suggestion,
		amount,
		setAmount,
		expiresAt,
		setExpiresAt,
		redemptionMode,
		setRedemptionMode,
		membersOnly,
		setMembersOnly,
		savingGiftCode,
		giftCodes,
		showAllGiftCodes,
		setShowAllGiftCodes,
	} = controller;
	return (
		<>
			{activeSection === 'gifts' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Gift codes</h3>
						<p>
							Create a code that gives dabloons to eligible signed-in players who
							redeem it while online.
						</p>
					</div>

					<div className="adminWarnings" role="note" aria-label="Gift code warnings">
						<strong>Use gift codes carefully.</strong>
						<ul>
							<li>Use them for controlled promotions such as Freshers&apos; Fair.</li>
							<li>
								Ask whoever balances the economy before setting a value. Code names
								cannot be recreated.
							</li>
						</ul>
					</div>

					<form className="giftCodeForm" onSubmit={createGiftCode}>
						<label>
							Code
							<input
								value={code}
								onChange={(event) => {
									setCode(event.target.value);
								}}
								placeholder={suggestion}
								pattern="[A-Za-z0-9_.-]+"
								maxLength={64}
								required
							/>
						</label>
						<label>
							Dabloons
							<input
								value={amount}
								onChange={(event) => {
									setAmount(event.target.value);
								}}
								placeholder="20"
								type="number"
								min="1"
								max="2147483647"
								step="1"
								required
							/>
						</label>
						<label>
							Expires (optional)
							<input
								value={expiresAt}
								onChange={(event) => {
									setExpiresAt(event.target.value);
								}}
								type="datetime-local"
							/>
						</label>
						<fieldset className="giftCodeMode">
							<legend>Who can redeem it?</legend>
							<label>
								<input
									type="radio"
									name="redemption-mode"
									value="single"
									checked={redemptionMode === 'single'}
									onChange={() => {
										setRedemptionMode('single');
									}}
								/>
								<span>One redemption total</span>
							</label>
							<label>
								<input
									type="radio"
									name="redemption-mode"
									value="per_user"
									checked={redemptionMode === 'per_user'}
									onChange={() => {
										setRedemptionMode('per_user');
									}}
								/>
								<span>Once per player</span>
							</label>
						</fieldset>
						<label>
							<input
								type="checkbox"
								checked={membersOnly}
								onChange={(event) => {
									setMembersOnly(event.target.checked);
								}}
							/>
							Members only
						</label>
						<button disabled={savingGiftCode}>
							{savingGiftCode ? 'Creating...' : 'Create gift code'}
						</button>
					</form>

					{giftCodes.length > 0 && (
						<div className="giftCodeHistory">
							<div className="giftCodeHistoryHeader">
								<h4>Active codes</h4>
								{giftCodes.length > 5 && (
									<button
										type="button"
										onClick={() => {
											setShowAllGiftCodes((current) => !current);
										}}
									>
										{showAllGiftCodes
											? 'Show latest 5'
											: `Show all ${giftCodes.length}`}
									</button>
								)}
							</div>
							<ul>
								{(showAllGiftCodes ? giftCodes : giftCodes.slice(0, 5)).map(
									(giftCode) => (
										<li key={giftCode.code}>
											<code>{giftCode.code}</code>
											<span>{giftCode.amountDabloons} dabloons</span>
											<span>
												{giftCode.redemptionMode === 'per_user'
													? 'Once per player'
													: 'One total'}
												{giftCode.membersOnly ? ' - members only' : ''}
												{giftCode.expiresAtUnixMs
													? ` - until ${formatExpiry(giftCode.expiresAtUnixMs)}`
													: ' - no expiry'}
											</span>
											<span className="giftCodeReady">
												{`${giftCode.redemptionCount} redemption${giftCode.redemptionCount === 1 ? '' : 's'}`}
											</span>
										</li>
									),
								)}
							</ul>
						</div>
					)}
				</section>
			)}
		</>
	);
}
