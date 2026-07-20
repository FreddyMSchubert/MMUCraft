'use client'

import { FormEvent, useState } from 'react'

type Step =
	| 'email'
	| 'email-code'
	| 'minecraft-username'
	| 'minecraft-code'
	| 'rules'
	| 'done'
	| 'signin'
	| 'signin-code'

interface ApiError {
	message?: string | string[]
}

const SERVER_RULES = [
	'👿 Hate and prejudice, NSFW content, criminal behaviour and discussion is prohibited',
	'🗯️ Politics, religion, and your mother should be discussed respectfully.',
	'☢️ General toxicity is prohibited.',
	'💥 Griefing & exploiting loopholes is prohibited.',
	'‼️ Instructions from committee members are to be followed.',
	'🤡 Fun is to be had, this is an order.'
] as const

async function postJson<T>(url: string, body: unknown): Promise<T> {
	const response = await fetch(url, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body),
	})

	const data = await response.json().catch(() => null)

	if (!response.ok) {
		const error = data as ApiError | null
		const message = Array.isArray(error?.message)
			? error.message.join(', ')
			: error?.message ?? 'Request failed'

		throw new Error(message)
	}

	return data as T
}

export function AuthPanel({ onSignedIn }: { onSignedIn?: () => void }) {
	const [step, setStep] = useState<Step>('email')
	const [email, setEmail] = useState('')
	const [flowId, setFlowId] = useState('')
	const [emailCode, setEmailCode] = useState('')
	const [minecraftUsername, setMinecraftUsername] = useState('')
	const [minecraftCode, setMinecraftCode] = useState('')
	const [deliveryMessage, setDeliveryMessage] = useState('')
	const [acceptedRules, setAcceptedRules] = useState<boolean[]>(() => SERVER_RULES.map(() => false))
	const [error, setError] = useState('')
	const [busy, setBusy] = useState(false)
	const allRulesAccepted = acceptedRules.every(Boolean)

	async function run(action: () => Promise<void>) {
		setBusy(true)
		setError('')

		try {
			await action()
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Something went wrong')
		} finally {
			setBusy(false)
		}
	}

	function submitEmail(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			const result = await postJson<{ flowId: string; delivery: 'sent' | 'manual' }>('/api/auth/signup', { email })
			setFlowId(result.flowId)
			setDeliveryMessage(verificationMessage(result.delivery, email))
			setStep('email-code')
		})
	}

	function submitEmailCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-email', { flowId, code: emailCode })
			setStep('minecraft-username')
		})
	}

	function submitMinecraftUsername(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/minecraft-username', { flowId, minecraftUsername })
			setStep('minecraft-code')
		})
	}

	function submitMinecraftCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-minecraft', { flowId, code: minecraftCode })
			setStep('rules')
		})
	}

	function submitRules(event: FormEvent) {
		event.preventDefault()

		if (!allRulesAccepted) {
			setError('You must acknowledge every rule before joining')
			return
		}

		void run(async () => {
			await postJson('/api/auth/accept-rules', { flowId })
			setStep('done')
			onSignedIn?.()
		})
	}

	function setRuleAccepted(index: number, accepted: boolean) {
		setAcceptedRules((current) => current.map((value, ruleIndex) => (
			ruleIndex === index ? accepted : value
		)))
		setError('')
	}

	function submitSignIn(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			const result = await postJson<{ flowId: string; delivery: 'sent' | 'manual' }>('/api/auth/signin', { email })
			setFlowId(result.flowId)
			setDeliveryMessage(verificationMessage(result.delivery, email))
			setStep('signin-code')
		})
	}

	function submitSignInCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-signin', { flowId, code: emailCode })
			setStep('done')
			onSignedIn?.()
		})
	}

	return (
		<section className="authCard">
			{step === 'email' && (
				<form onSubmit={submitEmail} className="authForm">
					<h2>Join the server</h2>
					<p>Use your MMU email to start signup. (Must end in @stu.mmu.ac.uk or @mmu.ac.uk).</p>
					<p>If you are not an MMU member and want to join the server, please have someone you know at MMU contact the administrators. We&apos;re happy to have you!</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="you@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<button disabled={busy}>Sign up</button>
					<button type="button" disabled={busy} onClick={() => setStep('signin')}>
						Already signed up?
					</button>
				</form>
			)}

			{step === 'signin' && (
				<form onSubmit={submitSignIn} className="authForm">
					<h2>Sign in</h2>
					<p>We&apos;ll send a verification code to your signup email.</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="you@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<button disabled={busy}>Sign in</button>
					<button type="button" disabled={busy} onClick={() => setStep('email')}>
						Back to signup
					</button>
				</form>
			)}

			{step === 'email-code' && (
				<form onSubmit={submitEmailCode} className="authForm">
					<h2>Verify email</h2>
					<p>{deliveryMessage}</p>
					<input
						value={emailCode}
						onChange={(event) => setEmailCode(event.target.value)}
						placeholder="Email code"
						inputMode="numeric"
						autoComplete="one-time-code"
						pattern="[0-9]{6}"
						maxLength={6}
						required
					/>
					<button disabled={busy}>Verify email</button>
				</form>
			)}

			{step === 'signin-code' && (
				<form onSubmit={submitSignInCode} className="authForm">
					<h2>Verify sign in</h2>
					<p>{deliveryMessage}</p>
					<input
						value={emailCode}
						onChange={(event) => setEmailCode(event.target.value)}
						placeholder="Email code"
						inputMode="numeric"
						autoComplete="one-time-code"
						pattern="[0-9]{6}"
						maxLength={6}
						required
					/>
					<button disabled={busy}>Sign in</button>
				</form>
			)}

			{step === 'minecraft-username' && (
				<form onSubmit={submitMinecraftUsername} className="authForm">
					<h2>Minecraft username</h2>
					<p>Enter the exact Java Edition username you will join with.</p>
					<input
						value={minecraftUsername}
						onChange={(event) => setMinecraftUsername(event.target.value)}
						placeholder="MinecraftUsername"
					/>
					<button disabled={busy}>Continue</button>
				</form>
			)}

			{step === 'minecraft-code' && (
				<form onSubmit={submitMinecraftCode} className="authForm">
					<h2>Join the server</h2>
					<p>Try to join Minecraft. The kick message will show a code. Enter it here.</p>
					<input
						value={minecraftCode}
						onChange={(event) => setMinecraftCode(event.target.value)}
						placeholder="Minecraft code"
						autoCapitalize="characters"
					/>
					<button disabled={busy}>Verify Minecraft code</button>
				</form>
			)}

			{step === 'rules' && (
				<form onSubmit={submitRules} className="authForm">
					<h2>Accept the rules</h2>
					<p>You must accept the server rules before your Minecraft username is whitelisted.</p>

					<div className="authRules">
						<p>Check every rule to confirm you have read and understood it:</p>
						<div className="authRuleList">
							{SERVER_RULES.map((rule, index) => (
								<label className="authRule" key={rule}>
									<input
										type="checkbox"
										checked={acceptedRules[index]}
										onChange={(event) => setRuleAccepted(index, event.target.checked)}
									/>
									<span>{rule}</span>
								</label>
							))}
						</div>
						<p>If you think any of these rules are being broken or feel unwell/unsafe on the server in any way,<br/>please reach out to our Wellbeing Officer Mia or <a href="https://discord.com/channels/1396896170751692931/1415746294659551384" target="_blank">open up a ticket</a> on our discord server.</p>
					</div>

					<button disabled={busy || !allRulesAccepted}>Accept rules and finish signup</button>
				</form>
			)}

			{step === 'done' && (
				<div className="authForm">
					<h2>You are signed in</h2>
					<p>Your account is ready. If signup just completed, try joining Minecraft again.</p>
				</div>
			)}

			{error ? <p className="authError">{error}</p> : null}
		</section>
	)
}

function verificationMessage(delivery: 'sent' | 'manual', email: string) {
	return delivery === 'sent'
		? `We sent a six-digit code to ${email}. It expires in 10 minutes.`
		: 'Email delivery is currently unavailable. Contact the administrators; they can find this active request and give you the code.'
}
