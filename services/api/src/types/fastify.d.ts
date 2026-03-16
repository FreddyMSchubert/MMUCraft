import 'fastify'
import type { AppConfig } from '../config.js'
import type { AccountStore } from '../services/account-store.js'
import type { DiscordOAuthService } from '../services/discord-oauth.js'
import type { RegistrationSessionStore } from '../services/registration-session-store.js'

declare module 'fastify' {
  interface FastifyInstance {
    config: AppConfig
    accountStore: AccountStore
    registrationSessions: RegistrationSessionStore
    discordOAuth: DiscordOAuthService | null
  }
}

export {}
