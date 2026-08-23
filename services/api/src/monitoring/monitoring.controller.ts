import { Controller, Get, Res } from '@nestjs/common';
import type { FastifyReply } from 'fastify';
import { MonitoringService } from './monitoring.service';

@Controller('internal/metrics')
export class MonitoringController {
	constructor(private readonly monitoring: MonitoringService) {}

	@Get()
	async metrics(@Res() reply: FastifyReply) {
		reply.header('content-type', this.monitoring.contentType());
		return await reply.send(await this.monitoring.render());
	}
}
