import { notFound } from 'next/navigation'
import { getRegistrationSession } from '@/lib/api'
import { getPublicApiBaseUrl } from '@/lib/env'

interface RegistrationPageProps {
  params: Promise<{ sessionId: string }>
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function RegistrationPage({ params, searchParams }: RegistrationPageProps) {
  const [{ sessionId }, query] = await Promise.all([params, searchParams])
  const session = await getRegistrationSession(sessionId)
  const publicApiBaseUrl = getPublicApiBaseUrl()

  if (!session) {
    notFound()
  }

  const done = first(query.done) === '1'
  const emailStatus = first(query.email)
  const emailError = first(query.emailError)
  const verifyError = first(query.verifyError)
  const generalError = first(query.error)

  return (
    <section className="stack narrow">
      <div className="card">
        <div className="eyebrow">registration</div>
        <h2>Hello {session.username}</h2>
        <p className="muted">
          This registration session is tied to your Minecraft UUID and completes the one-time account link.
        </p>

        <div className="infoBlock">
          <div>
            <span className="muted">Minecraft UUID</span>
            <div className="mono">{session.playerUuid}</div>
          </div>
          <div>
            <span className="muted">Session status</span>
            <div>{session.status}</div>
          </div>
        </div>

        {done ? <div className="successBox">Registration complete. You can now play normally and sign in here with Discord later.</div> : null}
        {emailStatus === 'sent' ? <div className="successBox">Verification code created. Check the API container logs for the demo code.</div> : null}
        {emailError ? <div className="errorBox">Email step failed: {decodeURIComponent(emailError)}</div> : null}
        {verifyError ? <div className="errorBox">Code verification failed: {decodeURIComponent(verifyError)}</div> : null}
        {generalError ? <div className="errorBox">Registration failed: {generalError}</div> : null}

        {session.status === 'awaiting_email' ? (
          <form className="formCard" action={`/register/${encodeURIComponent(session.id)}/email`} method="post">
            <label className="label">
              MMU email
              <input
                name="email"
                type="email"
                required
                placeholder="you@stu.mmu.ac.uk"
                pattern="[^@\s]+@(mmu\.ac\.uk|stu\.mmu\.ac\.uk)"
              />
            </label>
            <div className="helpText">Only MMU staff and student domains are accepted.</div>
            <button className="button" type="submit">
              Send verification code
            </button>
          </form>
        ) : null}

        {session.status === 'awaiting_email_verification' ? (
          <form className="formCard" action={`/register/${encodeURIComponent(session.id)}/verify`} method="post">
            <div className="helpText">
              Demo mode is active, so the verification code is written to the API container logs instead of being emailed.
            </div>
            <label className="label">
              Verification code
              <input name="code" type="text" inputMode="numeric" required maxLength={6} placeholder="123456" />
            </label>
            <button className="button" type="submit">
              Verify email
            </button>
          </form>
        ) : null}

        {session.status === 'awaiting_discord' ? (
          <div className="stack">
            <div className="successBox">MMU email verified. One last hoop: link your Discord account.</div>
            <a
              className="button"
              href={`${publicApiBaseUrl}/api/auth/discord/start?mode=registration&registrationSessionId=${encodeURIComponent(session.id)}`}
            >
              Continue with Discord
            </a>
          </div>
        ) : null}

        {session.status === 'complete' && !done ? (
          <div className="successBox">This registration session has already been completed.</div>
        ) : null}
      </div>
    </section>
  )
}
