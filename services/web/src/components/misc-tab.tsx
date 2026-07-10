'use client'

import { FormEvent, useState } from 'react'

export function MiscTab() {
	const [code, setCode] = useState('')
	const [busy, setBusy] = useState(false)
	const [message, setMessage] = useState('')
	const [error, setError] = useState('')

	function redeem(event: FormEvent) {
		event.preventDefault()
		setBusy(true)
		setMessage('')
		setError('')

		void (async () => {
			try {
				const response = await fetch('/api/gift-codes/redeem', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ code }),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(typeof body?.message === 'string' ? body.message : 'Could not redeem gift code')

				setCode('')
				setMessage(body?.message ?? 'Gift code redeemed.')
			} catch (caught) {
				setError(caught instanceof Error ? caught.message : 'Could not redeem gift code')
			} finally {
				setBusy(false)
			}
		})()
	}

	return (
		<div className="miscPanel">
			<div>
				<h3>Gift codes</h3>
				<p>Enter a gift code while online to receive dabloons in-game.</p>
			</div>

			<div className="giftInstructions">
				<p>Codes may expire or be first-come, first-served. Failed offline attempts do not use the code.</p>
			</div>

			<form className="redeemForm" onSubmit={redeem}>
				<label htmlFor="gift-code">Gift code</label>
				<div>
					<input
						id="gift-code"
						value={code}
						onChange={(event) => setCode(event.target.value)}
						placeholder="Enter your code"
						maxLength={64}
						required
					/>
					<button disabled={busy}>{busy ? 'Redeeming...' : 'Redeem'}</button>
				</div>
			</form>

			{message && <p className="adminMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
		</div>
	)
}
