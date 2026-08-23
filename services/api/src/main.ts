import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, type NestFastifyApplication } from '@nestjs/platform-fastify';
import { AppModule } from './app.module';
import { ShutdownService } from './shutdown';
import { MonitoringService } from './monitoring/monitoring.service';

async function bootstrap() {
	const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter());
	app.enableShutdownHooks();
	const server = app.getHttpAdapter().getInstance();
	const shutdown = app.get(ShutdownService);
	const monitoring = app.get(MonitoringService);
	const trackedRequests = new WeakSet();
	server.addHook('onRequest', async (request, reply) => {
		monitoring.beginRequest(request);
		const path = request.url.split('?', 1)[0];
		if (path === '/api/internal/shutdown') return;
		if (!shutdown.beginRequest()) {
			await reply.code(503).send({ message: 'Server restart in progress' });
			return;
		}
		if (!path?.endsWith('/events')) trackedRequests.add(request);
		else shutdown.endRequest();
	});
	server.addHook('onResponse', (request, reply) => {
		monitoring.finishRequest(request, reply.statusCode);
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
