import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, type NestFastifyApplication } from '@nestjs/platform-fastify';
import { AppModule } from './app.module';
import { ShutdownService } from './shutdown';
import { MonitoringService } from './monitoring/monitoring.service';
import { AuthSessionService } from './auth/auth-session.service';
import { VelocityService } from './velocity/velocity.service';

async function bootstrap() {
	const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter(), {
		logger: ['error', 'warn'],
	});
	const httpLogger = new Logger('HTTP');
	app.enableShutdownHooks();
	const server = app.getHttpAdapter().getInstance();
	const shutdown = app.get(ShutdownService);
	const monitoring = app.get(MonitoringService);
	const sessions = app.get(AuthSessionService);
	const velocity = app.get(VelocityService);
	const trackedRequests = new WeakSet();
	server.addHook('onRequest', async (request, reply) => {
		monitoring.beginRequest(request);
		const path = request.url.split('?', 1)[0] ?? '';
		if (
			path.startsWith('/api/') &&
			!['/api/auth/', '/api/internal/', '/api/discord/avatar', '/api/health'].some((prefix) =>
				path.startsWith(prefix),
			) &&
			velocity.hasWaitingRoomPlayers() &&
			velocity.isWaitingRoomUsername(
				sessions.getSession(request.headers.cookie)?.minecraftUsername ?? '',
			)
		) {
			await reply.code(409).send({ message: 'Wait until you enter the game.' });
			return;
		}
		if (path === '/api/internal/shutdown') return;
		if (!shutdown.beginRequest()) {
			await reply.code(503).send({ message: 'Server restart in progress' });
			return;
		}
		if (!path.endsWith('/events')) trackedRequests.add(request);
		else shutdown.endRequest();
	});
	server.addHook('onResponse', (request, reply) => {
		monitoring.finishRequest(request, reply.statusCode);
		if (reply.statusCode >= 500) {
			httpLogger.error(
				`${request.method} ${request.url.split('?', 1)[0]} -> ${reply.statusCode}`,
			);
		}
		if (trackedRequests.delete(request)) shutdown.endRequest();
	});
	server.addHook('onRequestAbort', (request) => {
		monitoring.finishRequest(request, 'aborted');
		if (trackedRequests.delete(request)) shutdown.endRequest();
	});

	const host = process.env.HOST ?? '0.0.0.0';
	const port = Number(process.env.PORT ?? 8080);

	await app.listen(port, host);
}

void bootstrap();
