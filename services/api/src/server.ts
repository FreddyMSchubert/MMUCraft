import { buildApp } from './app.js'

const app = buildApp()

async function shutdown(signal: string): Promise<void> {
  app.log.info({ signal }, 'Shutting down')
  await app.close()
  process.exit(0)
}

process.once('SIGINT', () => {
  void shutdown('SIGINT')
})

process.once('SIGTERM', () => {
  void shutdown('SIGTERM')
})

try {
  const address = await app.listen({
    port: app.config.port,
    host: app.config.host,
  })

  app.log.info(`API listening at ${address}`)
} catch (error) {
  app.log.error(error)
  process.exit(1)
}
