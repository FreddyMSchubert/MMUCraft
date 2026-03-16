import type { FastifyPluginAsync } from 'fastify'
import { ensureNonEmptyString } from '../services/validation.js'

const minecraftRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get<{ Params: { playerUuid: string } }>('/minecraft/players/:playerUuid/status', async (request) => {
    const account = fastify.accountStore.getAccountByMinecraftUuid(request.params.playerUuid)

    return {
      authenticated: account !== null,
      account,
    }
  })

  fastify.post<{
    Body: {
      playerUuid?: string
      username?: string
    }
  }>('/minecraft/registration-sessions', async (request, reply) => {
    const playerUuid = ensureNonEmptyString(request.body?.playerUuid)
    const username = ensureNonEmptyString(request.body?.username)

    if (!playerUuid || !username) {
      reply.code(400)
      return { error: 'playerUuid and username are required' }
    }

    const existingAccount = fastify.accountStore.getAccountByMinecraftUuid(playerUuid)
    if (existingAccount) {
      return {
        authenticated: true,
        account: existingAccount,
      }
    }

    const session = fastify.registrationSessions.createOrReuse(playerUuid, username)

    return {
      authenticated: false,
      sessionId: session.id,
      loginUrl: `${fastify.config.publicWebBaseUrl}/register/${session.id}`,
      expiresAt: session.expiresAt,
    }
  })
}

export default minecraftRoutes
