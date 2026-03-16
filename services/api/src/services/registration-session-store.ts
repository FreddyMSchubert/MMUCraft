import { createHash, randomInt, randomUUID } from 'node:crypto'

export type RegistrationStatus =
  | 'awaiting_email'
  | 'awaiting_email_verification'
  | 'awaiting_discord'
  | 'complete'

export interface RegistrationSession {
  id: string
  playerUuid: string
  username: string
  createdAt: string
  expiresAt: string
  email: string | null
  emailVerificationCodeHash: string | null
  emailVerifiedAt: string | null
  status: RegistrationStatus
  accountId: string | null
}

interface DiscordState {
  state: string
  mode: 'registration' | 'login'
  registrationSessionId: string | null
  createdAt: number
}

function hashCode(sessionId: string, code: string): string {
  return createHash('sha256').update(`${sessionId}:${code}`).digest('hex')
}

export class RegistrationSessionStore {
  private readonly sessions = new Map<string, RegistrationSession>()
  private readonly sessionByPlayerUuid = new Map<string, string>()
  private readonly discordStates = new Map<string, DiscordState>()

  constructor(private readonly ttlMs: number) {}

  private cleanup(): void {
    const now = Date.now()

    for (const [id, session] of this.sessions.entries()) {
      if (Date.parse(session.expiresAt) <= now) {
        this.sessions.delete(id)
        this.sessionByPlayerUuid.delete(session.playerUuid)
      }
    }

    for (const [state, payload] of this.discordStates.entries()) {
      if (payload.createdAt + this.ttlMs <= now) {
        this.discordStates.delete(state)
      }
    }
  }

  createOrReuse(playerUuid: string, username: string): RegistrationSession {
    this.cleanup()

    const existingId = this.sessionByPlayerUuid.get(playerUuid)
    if (existingId) {
      const existing = this.sessions.get(existingId)
      if (existing) {
        existing.username = username
        return existing
      }
    }

    const now = new Date()
    const session: RegistrationSession = {
      id: randomUUID(),
      playerUuid,
      username,
      createdAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + this.ttlMs).toISOString(),
      email: null,
      emailVerificationCodeHash: null,
      emailVerifiedAt: null,
      status: 'awaiting_email',
      accountId: null,
    }

    this.sessions.set(session.id, session)
    this.sessionByPlayerUuid.set(playerUuid, session.id)
    return session
  }

  get(sessionId: string): RegistrationSession | null {
    this.cleanup()
    return this.sessions.get(sessionId) ?? null
  }

  saveEmail(sessionId: string, email: string): { session: RegistrationSession; code: string } | null {
    const session = this.get(sessionId)
    if (!session || session.status === 'complete') {
      return null
    }

    const code = String(randomInt(100000, 1000000))
    session.email = email
    session.emailVerificationCodeHash = hashCode(session.id, code)
    session.emailVerifiedAt = null
    session.status = 'awaiting_email_verification'
    return { session, code }
  }

  verifyEmail(sessionId: string, code: string): RegistrationSession | null {
    const session = this.get(sessionId)
    if (!session || session.status !== 'awaiting_email_verification' || !session.emailVerificationCodeHash) {
      return null
    }

    if (hashCode(session.id, code) !== session.emailVerificationCodeHash) {
      return null
    }

    session.emailVerificationCodeHash = null
    session.emailVerifiedAt = new Date().toISOString()
    session.status = 'awaiting_discord'
    return session
  }

  createDiscordState(mode: 'registration' | 'login', registrationSessionId?: string): string {
    this.cleanup()

    const state = randomUUID()
    this.discordStates.set(state, {
      state,
      mode,
      registrationSessionId: registrationSessionId ?? null,
      createdAt: Date.now(),
    })

    return state
  }

  consumeDiscordState(state: string): DiscordState | null {
    this.cleanup()
    const payload = this.discordStates.get(state) ?? null
    if (payload) {
      this.discordStates.delete(state)
    }
    return payload
  }

  markComplete(sessionId: string, accountId: string): RegistrationSession | null {
    const session = this.get(sessionId)
    if (!session) {
      return null
    }

    session.accountId = accountId
    session.status = 'complete'
    return session
  }
}
