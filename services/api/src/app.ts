import cookie from '@fastify/cookie'
import 'dotenv/config'
import Fastify, { type FastifyError } from 'fastify'
import { buildConfig } from './config.js'
import authRoutes from './routes/auth.js'
import healthRoutes from './routes/health.js'
import meRoutes from './routes/me.js'
import minecraftRoutes from './routes/minecraft.js'
import registrationRoutes from './routes/registration.js'
import { AccountStore } from './services/account-store.js'
import { DiscordOAuthService } from './services/discord-oauth.js'
import { RegistrationSessionStore } from './services/registration-session-store.js'

export function buildApp() {
	const config = buildConfig()

	const app = Fastify({
		logger: {
			level: config.logLevel,
		},
	})

	const accountStore = new AccountStore(config.databasePath)
	const registrationSessions = new RegistrationSessionStore(config.registrationSessionTtlMs)
	const discordOAuth =
		config.discordClientId && config.discordClientSecret && config.discordRedirectUri
			? new DiscordOAuthService(config.discordClientId, config.discordClientSecret, config.discordRedirectUri)
			: null

	app.decorate('config', config)
	app.decorate('accountStore', accountStore)
	app.decorate('registrationSessions', registrationSessions)
	app.decorate('discordOAuth', discordOAuth)

	app.register(cookie)

	app.setErrorHandler((error: FastifyError, request, reply) => {
		request.log.error({ err: error }, 'Unhandled request error')

		const statusCode =
			typeof error.statusCode === 'number' && error.statusCode >= 400
				? error.statusCode
				: 500

		reply.code(statusCode).send({
			error: statusCode >= 500 ? 'Internal Server Error' : error.message,
		})
	})

	app.register(healthRoutes, { prefix: '/api' })
	app.register(minecraftRoutes, { prefix: '/api' })
	app.register(registrationRoutes, { prefix: '/api' })
	app.register(authRoutes, { prefix: '/api' })
	app.register(meRoutes, { prefix: '/api' })

	app.addHook('onClose', async () => {
		accountStore.close()
	})

	return app
}
