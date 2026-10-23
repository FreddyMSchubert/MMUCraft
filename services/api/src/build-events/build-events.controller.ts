import { Body, Controller, Delete, Get, Headers, Param, Patch, Post } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { BuildEventsService } from './build-events.service';

@Controller('api/build-events')
export class BuildEventsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly events: BuildEventsService,
	) {}

	@Get()
	list() {
		return this.events.list();
	}

	@Get('me/visit')
	visit(@Headers('cookie') cookie: string | undefined) {
		return this.events.visitStatus(this.auth.requireSession(cookie).id);
	}

	@Post('me/return')
	return(@Headers('cookie') cookie: string | undefined) {
		return this.events.warpBack(this.auth.requireSession(cookie).id);
	}

	@Get(':id')
	detail(@Headers('cookie') cookie: string | undefined, @Param('id') id: string) {
		return this.events.detail(id, this.auth.getSession(cookie)?.id);
	}

	@Post(':id/submissions/:submissionId/vote')
	vote(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
		@Body() body: { scores?: unknown } | undefined,
	) {
		return this.events.vote(
			id,
			submissionId,
			this.auth.requireSession(cookie).id,
			body?.scores,
		);
	}

	@Get(':id/submissions/:submissionId/eligibility')
	eligibility(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
	) {
		return this.events.eligibility(id, submissionId, this.auth.requireSession(cookie).id);
	}

	@Post(':id/submissions/:submissionId/warp')
	warp(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
	) {
		return this.events.warp(id, submissionId, this.auth.requireSession(cookie).id);
	}
}

@Controller('api/admin/build-events')
export class BuildEventsAdminController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly events: BuildEventsService,
	) {}

	@Get()
	list(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.list();
	}

	@Get('admin-position')
	position(@Headers('cookie') cookie: string | undefined) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.adminPosition();
	}

	@Post()
	create(
		@Headers('cookie') cookie: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.create(body ?? {});
	}

	@Get(':id')
	detail(@Headers('cookie') cookie: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.detail(id, undefined, true);
	}

	@Patch(':id')
	update(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.update(id, body ?? {});
	}

	@Delete(':id')
	remove(@Headers('cookie') cookie: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.remove(id);
	}

	@Patch(':id/reveal')
	reveal(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Body() body: { revealed?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.reveal(id, body?.revealed);
	}

	@Post(':id/submissions')
	addSubmission(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.saveSubmission(id, body ?? {});
	}

	@Patch(':id/submissions/:submissionId')
	editSubmission(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.saveSubmission(id, body ?? {}, submissionId);
	}

	@Delete(':id/submissions/:submissionId')
	removeSubmission(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.removeSubmission(id, submissionId);
	}

	@Patch(':id/submissions/:submissionId/post-voting-warp')
	setPostVotingWarp(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.setPostVotingWarp(id, submissionId, body?.enabled);
	}

	@Patch(':id/submissions/:submissionId/votes/:voterId')
	excludeVote(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('submissionId') submissionId: string,
		@Param('voterId') voterId: string,
		@Body() body: { excluded?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.setVoteExclusion(id, submissionId, Number(voterId), body?.excluded);
	}

	@Patch(':id/voters/:voterId')
	excludeVoter(
		@Headers('cookie') cookie: string | undefined,
		@Param('id') id: string,
		@Param('voterId') voterId: string,
		@Body() body: { excluded?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.events.setVoterExclusion(id, voterId, body?.excluded);
	}
}
