import { Controller, Get } from '@nestjs/common'

@Controller('api')
export class HealthController {
	@Get('health')
	getHealth() {
		return { ok: true }
	}
}
