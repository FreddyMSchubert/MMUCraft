import type { FastifyPluginAsync } from 'fastify'

const healthRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get('/health', async () => ({ ok: true }))
}

export default healthRoutes
