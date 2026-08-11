'use client'

import { FormEvent, useRef, useState } from 'react'
import { ASSETS } from '@/lib/assets'

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

const TEXTURE_BASE = `${ASSETS.minecraft.vanilla}/textures`
const AUTH_CODE_ITEMS = [
	{ name: 'Apple', image: `${TEXTURE_BASE}/item/apple.png` },
	{ name: 'Axe', image: `${TEXTURE_BASE}/item/golden_axe.png` },
	{ name: 'Beetroot', image: `${TEXTURE_BASE}/item/beetroot.png` },
	{ name: 'Coal', image: `${TEXTURE_BASE}/item/coal.png` },
	{ name: 'Copper', image: `${TEXTURE_BASE}/item/raw_copper.png` },
	{ name: 'Creeper', image: `${TEXTURE_BASE}/entity/creeper/creeper.png`, head: true },
	{ name: 'Diamond', image: `${TEXTURE_BASE}/item/diamond.png` },
	{ name: 'Egg', image: `${TEXTURE_BASE}/item/egg.png` },
	{ name: 'Emerald', image: `${TEXTURE_BASE}/item/emerald.png` },
	{ name: 'Fish', image: `${TEXTURE_BASE}/item/tropical_fish.png` },
	{ name: 'Flint and Steel', image: `${TEXTURE_BASE}/item/flint_and_steel.png` },
	{ name: 'Flower', image: `${TEXTURE_BASE}/block/red_tulip.png` },
	{ name: 'Gold Ingot', image: `${TEXTURE_BASE}/item/gold_ingot.png` },
	{ name: 'Iron', image: `${TEXTURE_BASE}/item/raw_iron.png` },
	{ name: 'Lapis Lazuli', image: `${TEXTURE_BASE}/item/lapis_lazuli.png` },
	{ name: 'Lava Bucket', image: `${TEXTURE_BASE}/item/lava_bucket.png` },
	{ name: 'Lily Pad', image: `${TEXTURE_BASE}/block/lily_pad.png` },
	{ name: 'Melon Slice', image: `${TEXTURE_BASE}/item/melon_slice.png` },
	{ name: 'Mushroom', image: `${TEXTURE_BASE}/block/red_mushroom.png` },
	{ name: 'Music Disk', image: `${TEXTURE_BASE}/item/music_disc_cat.png` },
	{ name: 'Netherite', image: `${TEXTURE_BASE}/item/netherite_scrap.png` },
	{ name: 'Pickaxe', image: `${TEXTURE_BASE}/item/iron_pickaxe.png` },
	{ name: 'Potato', image: `${TEXTURE_BASE}/item/potato.png` },
	{ name: 'Potion', image: `${TEXTURE_BASE}/item/potion.png` },
	{ name: 'Quartz', image: `${TEXTURE_BASE}/item/quartz.png` },
	{ name: 'Redstone', image: `${TEXTURE_BASE}/item/redstone.png` },
	{ name: 'Shovel', image: `${TEXTURE_BASE}/item/copper_shovel.png` },
	{ name: 'Slimeball', image: `${TEXTURE_BASE}/item/slime_ball.png` },
	{ name: 'Spear', image: `${TEXTURE_BASE}/item/diamond_spear.png` },
	{ name: 'Sword', image: `${TEXTURE_BASE}/item/wooden_sword.png` },
	{ name: 'Totem', image: `${TEXTURE_BASE}/item/totem_of_undying.png` },
	{ name: 'Trident', image: `${TEXTURE_BASE}/item/trident.png` },
	{ name: 'Wheat', image: `${TEXTURE_BASE}/item/wheat.png` },
	{ name: 'Zombie', image: `${TEXTURE_BASE}/entity/zombie/zombie.png`, head: true },
] as const
const AUTH_CODE_LENGTH = 3
const SIGNUP_PROGRESS: Partial<Record<Step, number>> = {
	email: 1,
	'email-code': 2,
	'minecraft-username': 3,
	'minecraft-code': 4,
	rules: 5,
	done: 5,
}

function emptyAuthCode() {
	return Array<string>(AUTH_CODE_LENGTH).fill('')
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

export function AuthPanel({ onSignedIn }: { onSignedIn?: () => void }) {
	const [step, setStep] = useState<Step>('email')
	const [isSigningIn, setIsSigningIn] = useState(false)
	const [email, setEmail] = useState('')
	const [flowId, setFlowId] = useState('')
	const [authCode, setAuthCode] = useState(emptyAuthCode)
	const [minecraftUsername, setMinecraftUsername] = useState('')
	const [deliveryMessage, setDeliveryMessage] = useState('')
	const [acceptedRules, setAcceptedRules] = useState<boolean[]>(() => SERVER_RULES.map(() => false))
	const [error, setError] = useState('')
	const [busy, setBusy] = useState(false)
	const running = useRef(false)
	const allRulesAccepted = acceptedRules.every(Boolean)
	const signupProgress = isSigningIn ? undefined : SIGNUP_PROGRESS[step]

	async function run(action: () => Promise<void>) {
		if (running.current) return
		running.current = true
		setBusy(true)
		setError('')

		try {
			await action()
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Something went wrong')
		} finally {
			running.current = false
			setBusy(false)
		}
	}

	function submitEmail(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			const result = await postJson<{ flowId: string; delivery: 'sent' | 'manual' }>('/api/auth/signup', { email })
			setFlowId(result.flowId)
			setDeliveryMessage(verificationMessage(result.delivery, email))
			setAuthCode(emptyAuthCode())
			setStep('email-code')
		})
	}

	function submitEmailCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-email', { flowId, code: authCode.join('|') })
			setAuthCode(emptyAuthCode())
			setStep('minecraft-username')
		})
	}

	function submitMinecraftUsername(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/minecraft-username', { flowId, minecraftUsername })
			setAuthCode(emptyAuthCode())
			setStep('minecraft-code')
		})
	}

	function submitMinecraftCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-minecraft', { flowId, code: authCode.join('|') })
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
			const result = await postJson<{ flowId: string; delivery: 'sent' | 'manual'; timeoutEnded: boolean }>('/api/auth/signin', { email })
			setFlowId(result.flowId)
			setDeliveryMessage(`${result.timeoutEnded ? 'Your timeout has ended and Minecraft server access was restored. Rejoin the server now. ' : ''}${verificationMessage(result.delivery, email)}`)
			setAuthCode(emptyAuthCode())
			setStep('signin-code')
		})
	}

	function submitSignInCode(event: FormEvent) {
		event.preventDefault()

		void run(async () => {
			await postJson('/api/auth/verify-signin', { flowId, code: authCode.join('|') })
			setStep('done')
			onSignedIn?.()
		})
	}

	return (
		<section className="authCard">
			{signupProgress !== undefined && (
				<div className="authSignupProgress">
					<span>{step === 'done' ? 'Signup complete' : `Signup step ${signupProgress} of 5`}</span>
					<progress value={signupProgress} max={5} />
				</div>
			)}
			{step === 'email' && (
				<form onSubmit={submitEmail} className="authForm">
					<h2>Join the server</h2>
					<p>Verify your MMU email to start signup: [student_id]@stu.mmu.ac.uk, or anything @mmu.ac.uk.</p>
					<p>If you are not from MMU and want to join the server, please have someone you know at MMU contact the committee. We&apos;re happy to have you!</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="12345678@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<button disabled={busy}>Sign up</button>
					<button type="button" disabled={busy} onClick={() => { setIsSigningIn(true); setStep('signin') }}>
						Already signed up?
					</button>
				</form>
			)}

			{step === 'signin' && (
				<form onSubmit={submitSignIn} className="authForm">
					<h2>Sign in</h2>
					<p>We&apos;ll send a verification code to your signup email. Please input the minecraft items in order.</p>
					<input
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						placeholder="12345678@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<button disabled={busy}>Sign in</button>
					<button type="button" disabled={busy} onClick={() => { setIsSigningIn(false); setStep('email') }}>
						Back to signup
					</button>
				</form>
			)}

			{step === 'email-code' && (
				<form onSubmit={submitEmailCode} className="authForm">
					<h2>Verify email</h2>
					<p>{deliveryMessage}</p>
					<AuthCodeInputs value={authCode} onChange={setAuthCode} />
					<button disabled={busy}>Verify email</button>
				</form>
			)}

			{step === 'signin-code' && (
				<form onSubmit={submitSignInCode} className="authForm">
					<h2>Verify sign in</h2>
					<p>{deliveryMessage}</p>
					<AuthCodeInputs value={authCode} onChange={setAuthCode} />
					<button disabled={busy}>Sign in</button>
				</form>
			)}

			{step === 'minecraft-username' && (
				<form onSubmit={submitMinecraftUsername} className="authForm">
					<h2>Minecraft username</h2>
					<p>To ensure nobody can impersonate you, please enter the exact Java Edition username you will join with.</p>
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
					<p>Join with <strong>Java Edition 26.2</strong> at <strong>mmuminecraftsociety.co.uk</strong>.</p>
					<p>When you join the server, you will be given another verification code. Please input the minecraft items in order.</p>
					<AuthCodeInputs value={authCode} onChange={setAuthCode} />
					<button disabled={busy}>Verify Minecraft code</button>
				</form>
			)}

			{step === 'rules' && (
				<form onSubmit={submitRules} className="authForm">
					<h2>Accept the rules</h2>

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
						<br></br>
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

function AuthCodeInputs({ value, onChange }: {
	value: string[]
	onChange: (value: string[]) => void
}) {
	return (
		<div className="authCodeInputs" role="group" aria-label="Three-item verification code">
			{value.map((selected, index) => (
				<label className="authCodeInput" key={index}>
					<span className="authCodePosition">{index + 1}</span>
					<AuthCodeIcon itemName={selected} />
					<select
						value={selected}
						onChange={(event) => onChange(value.map((item, itemIndex) => (
							itemIndex === index ? event.target.value : item
						)))}
						aria-label={`Code item ${index + 1}`}
						required
					>
						<option value="">Choose an item</option>
						{AUTH_CODE_ITEMS.map((item) => (
							<option value={item.name} key={item.name}>{item.name}</option>
						))}
					</select>
				</label>
			))}
		</div>
	)
}

function AuthCodeIcon({ itemName }: { itemName: string }) {
	const item = AUTH_CODE_ITEMS.find((candidate) => candidate.name === itemName)
	if (!item) return <span className="authCodeIcon authCodeIconEmpty" aria-hidden="true">?</span>

	return <span
		className={`authCodeIcon${'head' in item ? ' authCodeHeadIcon' : ''}`}
		style={{ backgroundImage: `url(${item.image})` }}
		role="img"
		aria-label={'head' in item ? `${item.name} head` : item.name}
	/>
}

function verificationMessage(delivery: 'sent' | 'manual', email: string) {
	return delivery === 'sent'
		? `We sent a three-item code to ${email}. It expires in 10 minutes. Please input the minecraft items in order.`
		: 'Email delivery is currently unavailable. Contact the administrators; they can find this active request and give you the code.'
}
