import Link from 'next/link'
import { StatCard } from '@/components/stat-card'
import { getMe } from '@/lib/api'
import { getPublicApiBaseUrl } from '@/lib/env'

interface HomePageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function HomePage({ searchParams }: HomePageProps) {
  const [account, query] = await Promise.all([getMe(), searchParams])
  const publicApiBaseUrl = getPublicApiBaseUrl()
  const loginError = first(query.loginError)
  const loginSuccess = first(query.login) === '1'

  return (
    <section className="stack">
      <div className="hero">
        <div className="eyebrow">account access</div>
        <h2>Use in-game /auth for first-time registration.</h2>
        <p className="muted">
          New players are registered from Minecraft. Once linked, this site becomes the persistent player
          portal and Discord login entry point.
        </p>
        <div className="linkRow" style={{ marginTop: '16px' }}>
          {account ? null : (
            <a className="button" href={`${publicApiBaseUrl}/api/auth/discord/start?mode=login`}>
              Sign in with Discord
            </a>
          )}
          <Link className="ghostLink" href="https://discord.com">
            Discord
          </Link>
        </div>
      </div>

      {loginSuccess ? <div className="successBox">Signed in successfully.</div> : null}
      {loginError ? <div className="errorBox">Discord login failed: {loginError}</div> : null}

      {account ? (
        <>
          <div className="grid">
            <StatCard label="Minecraft username" value={account.minecraftUsername} />
            <StatCard label="Discord" value={account.discordGlobalName || account.discordUsername} />
            <StatCard label="MMU email" value={account.email} />
          </div>

          <section className="card">
            <h3>Linked account</h3>
            <div className="infoBlock">
              <div>
                <span className="muted">Minecraft UUID</span>
                <div className="mono">{account.minecraftUuid}</div>
              </div>
              <div>
                <span className="muted">Discord user ID</span>
                <div className="mono">{account.discordUserId}</div>
              </div>
            </div>
            <form action="/logout" method="post">
              <button className="button" type="submit">
                Sign out
              </button>
            </form>
          </section>
        </>
      ) : (
        <section className="card">
          <h3>How first-time registration works</h3>
          <div className="infoBlock">
            <div>
              <span className="muted">1. Start in Minecraft</span>
              <div>Run /auth after joining the server.</div>
            </div>
            <div>
              <span className="muted">2. Verify MMU email</span>
              <div>The registration page accepts only @mmu.ac.uk or @stu.mmu.ac.uk addresses.</div>
            </div>
            <div>
              <span className="muted">3. Link Discord</span>
              <div>Discord becomes the login method for future visits to this site.</div>
            </div>
          </div>
        </section>
      )}
    </section>
  )
}
