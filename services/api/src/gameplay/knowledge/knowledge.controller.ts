import { Body, Controller, Get, Headers, Post } from '@nestjs/common';
import { AuthSessionService } from '../../auth/auth-session.service';
import { KnowledgeService } from './knowledge.service';

@Controller('api/knowledge')
export class KnowledgeController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly knowledge: KnowledgeService,
	) {}

	@Get()
	getKnowledge(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.auth.requireSession(cookieHeader);
		return this.knowledge.getKnowledgeForUser(user.id);
	}

	@Post('read')
	markRead(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { knowledgeId?: string },
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.knowledge.markRead(user, body.knowledgeId);
	}
}
