import { Controller, Get } from '@nestjs/common'
import { CountdownsService } from './countdowns.service'

@Controller('api/countdowns')
export class CountdownsController {
	constructor(private readonly countdowns: CountdownsService) { }

	@Get()
	list() {
		return this.countdowns.list()
	}
}
