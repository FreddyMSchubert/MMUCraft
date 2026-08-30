'use client';

import { type SyntheticEvent, useEffect, useRef, useState } from 'react';
import { AuthCodeInputs } from './auth/auth-code-inputs';
import {
	ApiRequestError,
	emptyAuthCode,
	formatCountdown,
	postJson,
	RESEND_DELAY_MS,
	SERVER_RULES,
	SIGNUP_PROGRESS,
	type AuthenticationStep,
	verificationMessage,
} from './auth/authentication-flow';

export function AuthPanel({ onSignedIn }: { onSignedIn?: () => void }) {
	const [step, setAuthenticationStep] = useState<AuthenticationStep>('email');
	const [isSigningIn, setIsSigningIn] = useState(false);
	const [studentSignup, setStudentSignup] = useState(true);
	const [studentId, setStudentId] = useState('');
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
			const signupEmail = studentSignup ? `${studentId}@stu.mmu.ac.uk` : email.trim();
			const result = await postJson<{ flowId: string }>('/api/auth/signup', {
				email: signupEmail,
			});
			setEmail(signupEmail);
			setFlowId(result.flowId);
			setDeliveryMessage(verificationMessage());
			setShowEmailHelp(false);
			setAuthCode(emptyAuthCode());
			restartResendCountdown();
			setAuthenticationStep('email-code');
		});
	}

	function submitEmailCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-email', { flowId, code: authCode.join('|') });
			setAuthCode(emptyAuthCode());
			setAuthenticationStep('minecraft-username');
		});
	}

	function submitMinecraftUsername(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/minecraft-username', { flowId, minecraftUsername });
			setAuthCode(emptyAuthCode());
			setAuthenticationStep('minecraft-code');
		});
	}

	function submitMinecraftCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-minecraft', { flowId, code: authCode.join('|') });
			setAuthenticationStep('rules');
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
			setAuthenticationStep('done');
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
				`${result.timeoutEnded ? 'Your timeout has ended and Minecraft server access was restored. Rejoin the server now. ' : ''}${verificationMessage()}`,
			);
			setShowEmailHelp(false);
			setAuthCode(emptyAuthCode());
			restartResendCountdown();
			setAuthenticationStep('signin-code');
		});
	}

	function submitSignInCode(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();

		void run(async () => {
			await postJson('/api/auth/verify-signin', { flowId, code: authCode.join('|') });
			setAuthenticationStep('done');
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
			setDeliveryMessage(verificationMessage(true));
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
					<h2>{studentSignup ? 'Hello' : 'Join the server'}</h2>
					<div className="authSignupPrompt">
						<p>
							{studentSignup ? (
								'Please enter your eight-digit student ID.'
							) : (
								<>
									Manually enter another email address. MMU staff can use their{' '}
									<strong>@mmu.ac.uk</strong> address.
								</>
							)}
						</p>
						<button
							className="authTextButton"
							type="button"
							disabled={busy}
							onClick={() => {
								setStudentSignup((current) => !current);
								setError('');
							}}
						>
							{studentSignup ? "I'm not an MMU student" : "I'm an MMU student"}
						</button>
					</div>
					{studentSignup ? (
						<input
							aria-label="Student ID"
							value={studentId}
							onChange={(event) => {
								setStudentId(event.target.value.replace(/\D/g, '').slice(0, 8));
							}}
							placeholder="12345678"
							type="text"
							inputMode="numeric"
							pattern="\d{8}"
							minLength={8}
							maxLength={8}
							autoComplete="username"
							required
						/>
					) : (
						<input
							aria-label="Email address"
							value={email}
							onChange={(event) => {
								setEmail(event.target.value);
							}}
							placeholder="you@example.com"
							type="email"
							autoComplete="email"
							required
						/>
					)}
					{!studentSignup && (
						<p className="authGuestNote">
							Not from MMU? You&apos;re welcome to join too. Ask someone you know at
							MMU to contact the committee and help arrange access.
						</p>
					)}
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
						className="authOutlinedButton"
						type="button"
						disabled={busy}
						onClick={() => {
							setIsSigningIn(true);
							setAuthenticationStep('signin');
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
							setAuthenticationStep('email');
						}}
					>
						Sign up instead
					</button>
				</form>
			)}

			{step === 'email-code' && (
				<form onSubmit={submitEmailCode} className="authForm">
					<h2>Verify email</h2>
					<p>
						{deliveryMessage}
						<strong>{email}</strong>.
						<br />
						It expires in 10 minutes.
					</p>
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
					<p>
						{deliveryMessage}
						<strong>{email}</strong>.
						<br />
						It expires in 10 minutes.
					</p>
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
						<p className="authRulesSupport">
							If you think any of these rules are being broken or feel unwell/unsafe
							on the server in any way,
							<br />
							please reach out to our Wellbeing Officer Mia or{' '}
							<a
								href="https://discord.com/channels/1396896170751692931/1415746294659551384/1415753985561854043"
								target="_blank"
								rel="noreferrer"
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
							The verification code appears in the disconnect message. Enter it here,
							then join the server again after signup finishes.
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
