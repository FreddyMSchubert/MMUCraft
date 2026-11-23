import {
	Controller,
	Get,
	Header,
	Headers,
	Param,
	Query,
	Sse,
	StreamableFile,
} from '@nestjs/common';
import { createReadStream } from 'node:fs';
import { AuthSessionService } from '../auth/auth-session.service';
import { FishingService } from './fishing.service';

const NO_STORE = 'no-store, no-cache, must-revalidate, proxy-revalidate';

@Controller('api/fishing')
export class FishingController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly fishing: FishingService,
	) {}

	@Get('compendium')
	@Header('Cache-Control', NO_STORE)
	getCompendium(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('userId') userId: string | undefined,
	) {
		return this.fishing.getCompendium(this.auth.requireSession(cookieHeader), userId);
	}

	@Sse('events')
	events(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireSession(cookieHeader);
		return this.fishing.events();
	}

	@Get('texture/:fishId')
	@Header('Content-Type', 'image/png')
	@Header('Cache-Control', 'public, max-age=31536000, immutable')
	getTexture(@Param('fishId') fishId: string) {
		return new StreamableFile(createReadStream(this.fishing.getTextureFilePath(fishId)));
	}
}
