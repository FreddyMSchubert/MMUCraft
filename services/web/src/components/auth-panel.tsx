'use client';

import { type SyntheticEvent, useEffect, useRef, useState } from 'react';
import { ASSETS } from '@/lib/assets';

type Step =
	| 'email'
	| 'email-code'
	| 'minecraft-username'
	| 'minecraft-code'
	| 'rules'
	| 'done'
	| 'signin'
	| 'signin-code';

interface ApiError {
	message?: string | string[];
	retryAfterSeconds?: number;
}

class ApiRequestError extends Error {
	constructor(
		message: string,
		readonly retryAfterSeconds?: number,
	) {
		super(message);
	}
}

const SERVER_RULES = [
	'👿 Hate and prejudice, NSFW content, criminal behaviour and discussion is prohibited',
	'🗯️ Politics, religion, and your mother should be discussed respectfully.',
	'☢️ General toxicity is prohibited.',
	'💥 Griefing & exploiting loopholes is prohibited.',
	'‼️ Instructions from committee members are to be followed.',
	'🤡 Fun is to be had, this is an order.',
] as const;

const TEXTURE_BASE = `${ASSETS.minecraft.vanilla}/textures`;
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
] as const;
const AUTH_CODE_LENGTH = 3;
const RESEND_DELAY_MS = 60_000;
const SIGNUP_PROGRESS: Partial<Record<Step, number>> = {
	email: 1,
	'email-code': 2,
	'minecraft-username': 3,
	'minecraft-code': 4,
	rules: 5,
	done: 5,
};

function emptyAuthCode() {
	return Array<string>(AUTH_CODE_LENGTH).fill('');
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
	const response = await fetch(url, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body),
	});

	const data = await response.json().catch(() => null);

	if (!response.ok) {
		const error = data as ApiError | null;
		const message = Array.isArray(error?.message)
			? error.message.join(', ')
			: (error?.message ?? 'Request failed');

		throw new ApiRequestError(message, error?.retryAfterSeconds);
	}

	return data as T;
}

export function AuthPanel({ onSignedIn }: { onSignedIn?: () => void }) {
	const [step, setStep] = useState<Step>('email');
	const [isSigningIn, setIsSigningIn] = useState(false);
	const [email, setEmail] = useState('');
	const [flowId, setFlowId] = useState('');
	const [authCode, setAuthCode] = useState(emptyAuthCode);
	const [minecraftUsername, setMinecraftUsername] = useState('');
	const [deliveryMessage, setDeliveryMessage] = useState('');
	const [showEmailHelp, setShowEmailHelp] = useState(false);
	const [showMinecraftHelp, setShowMinecraftHelp] = useState(false);
	const [resendAvailableAt, setResendAvailableAt] = useState(0);
	const [resendNow, setResendNow] = useState(() => Date.now());
	const [acceptedRules, setAcceptedRules] = useState<boolean[]>(() =>
		SERVER_RULES.map(() => false),
	);
	const [error, setError] = useState('');
	const [busy, setBusy] = useState(false);
	const running = useRef(false);
	const allRulesAccepted = acceptedRules.every(Boolean);
	const signupProgress = isSigningIn ? undefined : SIGNUP_PROGRESS[step];
	const resendSeconds = Math.max(0, Math.ceil((resendAvailableAt - resendNow) / 1000));

	useEffect(() => {
		if (resendSeconds === 0) return;
		const timer = window.setInterval(() => {
			setResendNow(Date.now());
		}, 1_000);
		return () => {
			window.clearInterval(timer);
		};
	}, [resendSeconds]);

	function restartResendCountdown() {
		const now = Date.now();
		setResendNow(now);
		setResendAvailableAt(now + RESEND_DELAY_MS);
	}

	async function run(action: () => Promise<void>) {
		if (running.current) return;
		running.current = true;
		setBusy(true);
		setError('');

		try {
			await action();
		} catch (caught) {
			if (caught instanceof ApiRequestError && caught.retryAfterSeconds) {
				const now = Date.now();
				setResendNow(now);
				setResendAvailableAt(now + caught.retryAfterSeconds * 1000);
			}
			setError(caught instanceof Error ? caught.message : 'Something went wrong');
		} finally {
			running.current = false;
			setBusy(false);
		}
	}

	function submitEmail(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			const result = await postJson<{ flowId: string }>('/api/auth/signup', { email });
			setFlowId(result.flowId);
			setDeliveryMessage(verificationMessage(email));
			setShowEmailHelp(false);
			setAuthCode(emptyAuthCode());
			restartResendCountdown();
			setStep('email-code');
		});
	}

	function submitEmailCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-email', { flowId, code: authCode.join('|') });
			setAuthCode(emptyAuthCode());
			setStep('minecraft-username');
		});
	}

	function submitMinecraftUsername(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/minecraft-username', { flowId, minecraftUsername });
			setAuthCode(emptyAuthCode());
			setStep('minecraft-code');
		});
	}

	function submitMinecraftCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-minecraft', { flowId, code: authCode.join('|') });
			setStep('rules');
		});
	}

	function submitRules(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		if (!allRulesAccepted) {
			setError('You must acknowledge every rule before joining');
			return;
		}

		void run(async () => {
			await postJson('/api/auth/accept-rules', { flowId });
			setStep('done');
			onSignedIn?.();
		});
	}

	function setRuleAccepted(index: number, accepted: boolean) {
		setAcceptedRules((current) =>
			current.map((value, ruleIndex) => (ruleIndex === index ? accepted : value)),
		);
		setError('');
	}

	function submitSignIn(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			const result = await postJson<{ flowId: string; timeoutEnded: boolean }>(
				'/api/auth/signin',
				{ email },
			);
			setFlowId(result.flowId);
			setDeliveryMessage(
				`${result.timeoutEnded ? 'Your timeout has ended and Minecraft server access was restored. Rejoin the server now. ' : ''}${verificationMessage(email)}`,
			);
			setShowEmailHelp(false);
			setAuthCode(emptyAuthCode());
			restartResendCountdown();
			setStep('signin-code');
		});
	}

	function submitSignInCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-signin', { flowId, code: authCode.join('|') });
			setStep('done');
			onSignedIn?.();
		});
	}

	function resendEmail() {
		void run(async () => {
			const result =
				step === 'signin-code'
					? await postJson<{ flowId: string }>('/api/auth/signin', { email })
					: await postJson<{ flowId: string }>('/api/auth/signup', { email });
			setFlowId(result.flowId);
			setAuthCode(emptyAuthCode());
			setDeliveryMessage(verificationMessage(email, true));
			restartResendCountdown();
			setShowEmailHelp(false);
		});
	}

	return (
		<section className="authCard">
			{signupProgress !== undefined && (
				<div className="authSignupProgress">
					<span>
						{step === 'done' ? 'Signup complete' : `Signup step ${signupProgress} of 5`}
					</span>
					<progress value={signupProgress} max={5} />
				</div>
			)}
			{step === 'email' && (
				<form onSubmit={submitEmail} className="authForm">
					<h2>Join the server</h2>
					<p>
						Verify your MMU email to start signup: [student_id]@stu.mmu.ac.uk, or
						anything @mmu.ac.uk.
					</p>
					<p>
						If you are not from MMU and want to join the server, please have someone you
						know at MMU contact the committee. We&apos;re happy to have you!
					</p>
					<input
						value={email}
						onChange={(event) => {
							setEmail(event.target.value);
						}}
						placeholder="12345678@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<p className="authPrivacyNote">
						Your email address and student ID are visible only to the committee, not
						other society members.
					</p>
					<button disabled={busy || resendSeconds > 0}>
						{resendSeconds > 0
							? `Try again in ${formatCountdown(resendSeconds)}`
							: 'Sign up'}
					</button>
					<button
						type="button"
						disabled={busy}
						onClick={() => {
							setIsSigningIn(true);
							setStep('signin');
						}}
					>
						Already signed up?
					</button>
				</form>
			)}

			{step === 'signin' && (
				<form onSubmit={submitSignIn} className="authForm">
					<h2>Sign in</h2>
					<p>
						We&apos;ll send a verification code to your signup email. Please input the
						minecraft items in order.
					</p>
					<input
						value={email}
						onChange={(event) => {
							setEmail(event.target.value);
						}}
						placeholder="12345678@stu.mmu.ac.uk"
						type="email"
						autoComplete="email"
						required
					/>
					<button disabled={busy || resendSeconds > 0}>
						{resendSeconds > 0
							? `Try again in ${formatCountdown(resendSeconds)}`
							: 'Sign in'}
					</button>
					<button
						className="authOutlinedButton"
						type="button"
						disabled={busy}
						onClick={() => {
							setIsSigningIn(false);
							setStep('email');
						}}
					>
						Sign up instead
					</button>
				</form>
			)}

			{step === 'email-code' && (
				<form onSubmit={submitEmailCode} className="authForm">
					<h2>Verify email</h2>
					<p>{deliveryMessage}</p>
					<button
						className="authHelpButton"
						type="button"
						disabled={busy}
						onClick={() => {
							setShowEmailHelp(true);
						}}
					>
						I didn&apos;t get an email
					</button>
					<AuthCodeInputs value={authCode} onChange={setAuthCode} />
					<button disabled={busy}>Verify email</button>
				</form>
			)}

			{step === 'signin-code' && (
				<form onSubmit={submitSignInCode} className="authForm">
					<h2>Verify sign in</h2>
					<p>{deliveryMessage}</p>
					<button
						className="authHelpButton"
						type="button"
						disabled={busy}
						onClick={() => {
							setShowEmailHelp(true);
						}}
					>
						I didn&apos;t get an email
					</button>
					<AuthCodeInputs value={authCode} onChange={setAuthCode} />
					<button disabled={busy}>Sign in</button>
				</form>
			)}

			{step === 'minecraft-username' && (
				<form onSubmit={submitMinecraftUsername} className="authForm">
					<h2>Minecraft username</h2>
					<p>
						To ensure nobody can impersonate you, please enter the exact Java Edition
						username you will join with.
					</p>
					<input
						value={minecraftUsername}
						onChange={(event) => {
							setMinecraftUsername(event.target.value);
						}}
						placeholder="MinecraftUsername"
					/>
					<button disabled={busy}>Continue</button>
				</form>
			)}

			{step === 'minecraft-code' && (
				<form onSubmit={submitMinecraftCode} className="authForm">
					<h2>Join the server</h2>
					<p>
						Join the Minecraft server at <strong>mmuminecraftsociety.co.uk</strong>{' '}
						using <strong>Java Edition 26.2</strong>.
					</p>
					<button
						className="authHelpButton"
						type="button"
						disabled={busy}
						onClick={() => {
							setShowMinecraftHelp(true);
						}}
					>
						I couldn&apos;t get a code
					</button>
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
										onChange={(event) => {
											setRuleAccepted(index, event.target.checked);
										}}
									/>
									<span>{rule}</span>
								</label>
							))}
						</div>
						<br></br>
						<p>
							If you think any of these rules are being broken or feel unwell/unsafe
							on the server in any way,
							<br />
							please reach out to our Wellbeing Officer Mia or{' '}
							<a
								href="https://discord.com/channels/1396896170751692931/1415746294659551384"
								target="_blank"
							>
								open up a ticket
							</a>{' '}
							on our discord server.
						</p>
					</div>

					<button disabled={busy || !allRulesAccepted}>
						Accept rules and finish signup
					</button>
				</form>
			)}

			{step === 'done' && (
				<div className="authForm">
					<h2>You are signed in</h2>
					<p>
						Your account is ready. If signup just completed, try joining Minecraft
						again.
					</p>
				</div>
			)}

			{showEmailHelp && (step === 'email-code' || step === 'signin-code') && (
				<div className="authHelpBackdrop">
					<section
						className="authHelpDialog"
						role="dialog"
						aria-modal="true"
						aria-labelledby="auth-email-help-title"
					>
						<button
							className="authHelpClose"
							type="button"
							aria-label="Close"
							onClick={() => {
								setShowEmailHelp(false);
							}}
						>
							×
						</button>
						<h3 id="auth-email-help-title">Didn&apos;t get the email?</h3>
						<p>
							Check your spam folder and confirm that <strong>{email}</strong> is
							correct.
						</p>
						<p>If the email still does not arrive, contact the committee.</p>
						{error ? <p className="authError">{error}</p> : null}
						<button
							className="authSecondaryButton"
							type="button"
							disabled={busy || resendSeconds > 0}
							onClick={resendEmail}
						>
							{resendSeconds > 0
								? `Resend available in ${formatCountdown(resendSeconds)}`
								: 'Resend email'}
						</button>
					</section>
				</div>
			)}

			{showMinecraftHelp && step === 'minecraft-code' && (
				<div className="authHelpBackdrop">
					<section
						className="authHelpDialog"
						role="dialog"
						aria-modal="true"
						aria-labelledby="auth-minecraft-help-title"
					>
						<button
							className="authHelpClose"
							type="button"
							aria-label="Close"
							onClick={() => {
								setShowMinecraftHelp(false);
							}}
						>
							×
						</button>
						<h3 id="auth-minecraft-help-title">Couldn&apos;t get a code?</h3>
						<p>
							Check that your Minecraft username is correct:{' '}
							<strong>{minecraftUsername}</strong>.
						</p>
						<p>
							Join the server, wait for the verification code, then leave and join
							again if it does not appear.
						</p>
						<p>
							A message saying that you are not whitelisted is expected during signup.
							The code should appear before you are disconnected.
						</p>
						<p>If you still cannot get a code, contact the committee.</p>
					</section>
				</div>
			)}

			{error && !showEmailHelp && !showMinecraftHelp ? (
				<p className="authError">{error}</p>
			) : null}
		</section>
	);
}

function AuthCodeInputs({
	value,
	onChange,
}: {
	value: string[];
	onChange: (value: string[]) => void;
}) {
	return (
		<div className="authCodeInputs" role="group" aria-label="Three-item verification code">
			{value.map((selected, index) => (
				<label className="authCodeInput" key={index}>
					<span className="authCodePosition">{index + 1}</span>
					<AuthCodeIcon itemName={selected} />
					<select
						value={selected}
						onChange={(event) => {
							onChange(
								value.map((item, itemIndex) =>
									itemIndex === index ? event.target.value : item,
								),
							);
						}}
						aria-label={`Code item ${index + 1}`}
						required
					>
						<option value="">Choose an item</option>
						{AUTH_CODE_ITEMS.map((item) => (
							<option value={item.name} key={item.name}>
								{item.name}
							</option>
						))}
					</select>
				</label>
			))}
		</div>
	);
}

function AuthCodeIcon({ itemName }: { itemName: string }) {
	const item = AUTH_CODE_ITEMS.find((candidate) => candidate.name === itemName);
	if (!item)
		return (
			<span className="authCodeIcon authCodeIconEmpty" aria-hidden="true">
				?
			</span>
		);

	return (
		<span
			className={`authCodeIcon${'head' in item ? ' authCodeHeadIcon' : ''}`}
			style={{ backgroundImage: `url(${item.image})` }}
			role="img"
			aria-label={'head' in item ? `${item.name} head` : item.name}
		/>
	);
}

function verificationMessage(email: string, resent = false) {
	return resent
		? `We sent another three-item code to ${email}. It expires in 10 minutes.`
		: `We sent a three-item code to ${email}. It expires in 10 minutes.`;
}

function formatCountdown(totalSeconds: number) {
	if (totalSeconds <= 60) return `${totalSeconds}s`;
	const hours = Math.floor(totalSeconds / 3600);
	const minutes = Math.floor((totalSeconds % 3600) / 60);
	const seconds = totalSeconds % 60;
	return [
		hours ? `${hours}h` : '',
		minutes ? `${minutes}m` : '',
		!hours && seconds ? `${seconds}s` : '',
	]
		.filter(Boolean)
		.join(' ');
}
