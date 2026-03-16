import type { FastifyPluginAsync } from 'fastify'

function appendQuery(baseUrl: string, key: string, value: string): string {
  const url = new URL(baseUrl)
  url.searchParams.set(key, value)
  return url.toString()
}

const authRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get<{
    Querystring: {
      mode?: 'registration' | 'login'
      registrationSessionId?: string
    }
  }>('/auth/discord/start', async (request, reply) => {
    if (!fastify.discordOAuth) {
      reply.code(503)
      return { error: 'Discord OAuth is not configured' }
    }

    const mode = request.query.mode === 'registration' ? 'registration' : 'login'

    if (mode === 'registration') {
      const sessionId = request.query.registrationSessionId
      if (!sessionId) {
        reply.code(400)
        return { error: 'registrationSessionId is required for registration mode' }
      }

      const session = fastify.registrationSessions.get(sessionId)
      if (!session) {
        reply.code(404)
        return { error: 'Registration session not found' }
      }

      if (session.status !== 'awaiting_discord') {
        reply.code(400)
        return { error: 'Discord auth is not available for this registration session yet' }
      }

      const state = fastify.registrationSessions.createDiscordState('registration', sessionId)
      return reply.redirect(fastify.discordOAuth.getAuthorizationUrl(state))
    }

    const state = fastify.registrationSessions.createDiscordState('login')
    return reply.redirect(fastify.discordOAuth.getAuthorizationUrl(state))
  })

  fastify.get<{
    Querystring: {
      code?: string
      state?: string
      error?: string
    }
  }>('/auth/discord/callback', async (request, reply) => {
    if (request.query.error) {
      return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', request.query.error))
    }

    if (!fastify.discordOAuth) {
      return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', 'discord_not_configured'))
    }

    if (!request.query.code || !request.query.state) {
      return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', 'missing_code_or_state'))
    }

    const flow = fastify.registrationSessions.consumeDiscordState(request.query.state)
    if (!flow) {
      return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', 'invalid_state'))
    }

    try {
      const token = await fastify.discordOAuth.exchangeCode(request.query.code)
      const discordUser = await fastify.discordOAuth.getCurrentUser(token.access_token)

      if (flow.mode === 'registration') {
        const session = flow.registrationSessionId
          ? fastify.registrationSessions.get(flow.registrationSessionId)
          : null

        if (!session || session.status !== 'awaiting_discord' || !session.email) {
          return reply.redirect(
            appendQuery(`${fastify.config.publicWebBaseUrl}/register/${flow.registrationSessionId ?? ''}`, 'error', 'registration_session_invalid'),
          )
        }

        if (fastify.accountStore.getAccountByMinecraftUuid(session.playerUuid)) {
          return reply.redirect(
            appendQuery(`${fastify.config.publicWebBaseUrl}/register/${session.id}`, 'error', 'minecraft_uuid_already_registered'),
          )
        }

        if (fastify.accountStore.getAccountByDiscordUserId(discordUser.id)) {
          return reply.redirect(
            appendQuery(`${fastify.config.publicWebBaseUrl}/register/${session.id}`, 'error', 'discord_account_already_linked'),
          )
        }

        if (fastify.accountStore.getAccountByEmail(session.email)) {
          return reply.redirect(
            appendQuery(`${fastify.config.publicWebBaseUrl}/register/${session.id}`, 'error', 'email_already_registered'),
          )
        }

        const account = fastify.accountStore.createAccount({
          minecraftUuid: session.playerUuid,
          minecraftUsername: session.username,
          email: session.email,
          discordUserId: discordUser.id,
          discordUsername: discordUser.username,
          discordGlobalName: discordUser.global_name,
          discordAvatar: discordUser.avatar,
        })

        fastify.registrationSessions.markComplete(session.id, account.id)

        const webSession = fastify.accountStore.createWebSession(account.id, fastify.config.webSessionTtlMs)
        reply.setCookie(fastify.config.sessionCookieName, webSession.id, {
          path: '/',
          httpOnly: true,
          sameSite: 'lax',
          secure: fastify.config.sessionCookieSecure,
          maxAge: Math.floor(fastify.config.webSessionTtlMs / 1000),
        })

        return reply.redirect(`${fastify.config.publicWebBaseUrl}/register/${session.id}?done=1`)
      }

      const account = fastify.accountStore.getAccountByDiscordUserId(discordUser.id)
      if (!account) {
        return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', 'discord_not_linked'))
      }

      const webSession = fastify.accountStore.createWebSession(account.id, fastify.config.webSessionTtlMs)
      reply.setCookie(fastify.config.sessionCookieName, webSession.id, {
        path: '/',
        httpOnly: true,
        sameSite: 'lax',
        secure: fastify.config.sessionCookieSecure,
        maxAge: Math.floor(fastify.config.webSessionTtlMs / 1000),
      })

      return reply.redirect(`${fastify.config.publicWebBaseUrl}/?login=1`)
    } catch (error) {
      request.log.error({ err: error }, 'Discord callback failed')
      return reply.redirect(appendQuery(`${fastify.config.publicWebBaseUrl}/`, 'loginError', 'discord_callback_failed'))
    }
  })

  fastify.post('/auth/logout', async (request, reply) => {
    const sessionId = request.cookies[fastify.config.sessionCookieName]
    if (sessionId) {
      fastify.accountStore.deleteWebSession(sessionId)
    }

    reply.clearCookie(fastify.config.sessionCookieName, {
      path: '/',
      httpOnly: true,
      sameSite: 'lax',
      secure: fastify.config.sessionCookieSecure,
    })

    return reply.redirect(`${fastify.config.publicWebBaseUrl}/`)
  })
}

export default authRoutes
