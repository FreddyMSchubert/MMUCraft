import 'reflect-metadata'
import { NestFactory } from '@nestjs/core'
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify'
import { AppModule } from './app.module'

async function bootstrap() {
	const app = await NestFactory.create<NestFastifyApplication>(
		AppModule,
		new FastifyAdapter(),
	)

	const host = process.env.HOST ?? '0.0.0.0'
	const port = Number(process.env.PORT ?? 8080)

	await app.listen(port, host)
}

void bootstrap()
