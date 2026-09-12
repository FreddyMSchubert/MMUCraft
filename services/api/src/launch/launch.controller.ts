import { Controller, Get } from '@nestjs/common';
import { LaunchSettingsService } from './launch-settings.service';

@Controller('api/launch')
export class LaunchController {
	constructor(private readonly launch: LaunchSettingsService) {}

	@Get()
	get() {
		return this.launch.get();
	}
}
