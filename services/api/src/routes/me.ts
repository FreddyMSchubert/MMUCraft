import type { FastifyPluginAsync } from 'fastify'

const meRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get('/me', async (request, reply) => {
    const sessionId = request.cookies[fastify.config.sessionCookieName]
    if (!sessionId) {
      reply.code(401)
      return { error: 'Not signed in' }
    }

    const account = fastify.accountStore.getAccountByWebSession(sessionId)
    if (!account) {
      reply.code(401)
      return { error: 'Not signed in' }
    }

    return { account }
  })
}

export default meRoutes
