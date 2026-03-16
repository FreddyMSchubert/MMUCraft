import type { FastifyPluginAsync } from 'fastify'
import { ensureNonEmptyString, isAllowedMmuEmail, normalizeEmail } from '../services/validation.js'

const registrationRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get<{ Params: { sessionId: string } }>('/registration-sessions/:sessionId', async (request, reply) => {
    const session = fastify.registrationSessions.get(request.params.sessionId)
    if (!session) {
      reply.code(404)
      return { error: 'Registration session not found' }
    }

    return session
  })

  fastify.post<{
    Params: { sessionId: string }
    Body: { email?: string }
  }>('/registration-sessions/:sessionId/email', async (request, reply) => {
    const session = fastify.registrationSessions.get(request.params.sessionId)
    if (!session) {
      reply.code(404)
      return { error: 'Registration session not found' }
    }

    const email = ensureNonEmptyString(request.body?.email)
    if (!email) {
      reply.code(400)
      return { error: 'Email is required' }
    }

    const normalizedEmail = normalizeEmail(email)
    if (!isAllowedMmuEmail(normalizedEmail)) {
      reply.code(400)
      return { error: 'Email must end in @mmu.ac.uk or @stu.mmu.ac.uk' }
    }

    const existingByEmail = fastify.accountStore.getAccountByEmail(normalizedEmail)
    if (existingByEmail) {
      reply.code(409)
      return { error: 'That MMU email is already linked to another account' }
    }

    const result = fastify.registrationSessions.saveEmail(session.id, normalizedEmail)
    if (!result) {
      reply.code(400)
      return { error: 'Unable to update registration session' }
    }

    fastify.log.info(
      {
        sessionId: session.id,
        email: normalizedEmail,
        verificationCode: result.code,
        playerUuid: session.playerUuid,
        username: session.username,
      },
      'MMU email verification code generated',
    )

    return {
      ok: true,
      status: result.session.status,
      email: result.session.email,
    }
  })

  fastify.post<{
    Params: { sessionId: string }
    Body: { code?: string }
  }>('/registration-sessions/:sessionId/verify-email', async (request, reply) => {
    const code = ensureNonEmptyString(request.body?.code)
    if (!code) {
      reply.code(400)
      return { error: 'Verification code is required' }
    }

    const session = fastify.registrationSessions.verifyEmail(request.params.sessionId, code)
    if (!session) {
      reply.code(400)
      return { error: 'Invalid verification code or session state' }
    }

    return {
      ok: true,
      status: session.status,
      emailVerifiedAt: session.emailVerifiedAt,
    }
  })
}

export default registrationRoutes
