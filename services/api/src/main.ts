import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, type NestFastifyApplication } from '@nestjs/platform-fastify';
import { AppModule } from './app.module';
import { ShutdownService } from './shutdown';
import { MonitoringService } from './monitoring/monitoring.service';

async function bootstrap() {
	const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter(), {
		logger: ['error', 'warn'],
	});
	const httpLogger = new Logger('HTTP');
	app.enableShutdownHooks();
	const server = app.getHttpAdapter().getInstance();
	const shutdown = app.get(ShutdownService);
	const monitoring = app.get(MonitoringService);
	const trackedRequests = new WeakSet();
	const rejectedForDrain = new WeakSet();
	server.addHook('onRequest', async (request, reply) => {
		monitoring.beginRequest(request);
		const path = request.url.split('?', 1)[0] ?? '';
		if (path === '/api/internal/shutdown') return;
		if (!shutdown.beginRequest()) {
			rejectedForDrain.add(request);
			await reply.code(503).send({ message: 'Server restart in progress' });
			return;
		}
		if (path.endsWith('/events')) shutdown.endRequest();
		else {
			trackedRequests.add(request);
			if (request.method === 'GET' || request.method === 'HEAD') {
				reply.raw.once('close', () => {
					if (trackedRequests.delete(request)) shutdown.endRequest();
				});
			}
		}
	});
	server.addHook('onResponse', (request, reply) => {
		monitoring.finishRequest(request, reply.statusCode);
		if (reply.statusCode >= 500 && !rejectedForDrain.delete(request)) {
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
