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

interface ApiError {
	message?: string | string[]
}

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

export function AuthPanel() {
	const [step, setStep] = useState<Step>('email')
	const [email, setEmail] = useState('')
	const [flowId, setFlowId] = useState('')
	const [emailCode, setEmailCode] = useState('')
	const [minecraftUsername, setMinecraftUsername] = useState('')
	const [minecraftCode, setMinecraftCode] = useState('')
	const [devEmailCode, setDevEmailCode] = useState('')
	const [rulesAccepted, setRulesAccepted] = useState(false)
	const [error, setError] = useState('')
	const [busy, setBusy] = useState(false)

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
			const result = await postJson<{ flowId: string; devEmailCode: string }>('/api/auth/signup', { email })
			setFlowId(result.flowId)
			setDevEmailCode(result.devEmailCode)
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

		if (!rulesAccepted) {
			setError('You must accept the rules before joining')
			return
		}

		void run(async () => {
			await postJson('/api/auth/accept-rules', { flowId })
			setStep('done')
		})
	}

	function submitSignIn(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/signin', { email })
			setStep('done')
		})
	}

	return (
		<section className="authCard">
			{step === 'email' && (
				<form onSubmit={submitEmail} className="authForm">
					<h2>Join the server</h2>
					<p>Use your MMU email to start signup.</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="you@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
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
					<p>For now, sign-in only asks for your email.</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="you@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
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
					<p>Temporary dev code: <strong>{devEmailCode}</strong></p>
					<input
						value={emailCode}
						onChange={(event) => setEmailCode(event.target.value)}
						placeholder="Email code"
						inputMode="numeric"
					/>
					<button disabled={busy}>Verify email</button>
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
						<p>By joining, you agree to the following rules:</p>
						<ol>
							<li>You do not talk about Fight Club.</li>
							<li>YOU DO NOT. TALK. ABOUT FIGHT CLUB.</li>
							<li>Fighter yells "stop," goes limp, taps out, the fight's over. </li>
							<li>If this is your first time at Fight Club, you have to fight. </li>
						</ol>
						<p>Beyond that, you also agree to:</p>
						<ul>
							<li>All forms of hate and prejudice are prohibited.</li>
							<li>Any NSFW content is prohibited.</li>
							<li>Politics, Religion, and your mother should be discussed respectfully.</li>
							<li>Criminal behaviour and criminal discussion is prohibited.</li>
							<li>General toxicity is prohibited.</li>
							<li>Exploting loopholes is prohibited.</li>
						</ul>
					</div>

					<label>
						<input
							type="checkbox"
							checked={rulesAccepted}
							onChange={(event) => setRulesAccepted(event.target.checked)}
						/>
						{' '}
						I accept the server rules.
					</label>

					<button disabled={busy || !rulesAccepted}>Accept rules and finish signup</button>
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
