import { Body, Controller, Delete, Get, Headers, Param, Patch, Post, Query } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { ClaimAdministrationService } from './claim-administration.service';
import { ClaimPurchasingService, CreateClaimInput } from './claim-purchasing.service';
import { ClaimAppearanceInput, ClaimsService } from './claims.service';

@Controller('api/claims')
export class ClaimsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly claimPurchasing: ClaimPurchasingService,
		private readonly claims: ClaimsService,
	) {}

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		return this.claims.list(this.auth.requireSession(cookieHeader));
	}

	@Get('current')
	current(@Headers('cookie') cookieHeader: string | undefined) {
		return this.claimPurchasing.getCurrentChunk(this.auth.requireSession(cookieHeader));
	}

	@Post()
	create(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: CreateClaimInput | undefined,
	) {
		return this.claimPurchasing.create(this.auth.requireSession(cookieHeader), body ?? {});
	}

	@Delete(':claimId')
	remove(@Headers('cookie') cookieHeader: string | undefined, @Param('claimId') claimId: string) {
		return this.claims.remove(this.auth.requireSession(cookieHeader), claimId);
	}

	@Patch(':claimId/appearance')
	updateAppearance(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Body() body: ClaimAppearanceInput | undefined,
	) {
		return this.claims.updateAppearance(
			this.auth.requireSession(cookieHeader),
			claimId,
			body ?? {},
		);
	}

	@Post(':claimId/members')
	addMember(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Body() body: { userId?: number } | undefined,
	) {
		return this.claims.addMember(this.auth.requireSession(cookieHeader), claimId, body?.userId);
	}

	@Delete(':claimId/members/:userId')
	removeMember(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Param('userId') userId: string,
	) {
		return this.claims.removeMember(this.auth.requireSession(cookieHeader), claimId, userId);
	}
}

@Controller('api/admin/claims')
export class AdminClaimsController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly claimAdministration: ClaimAdministrationService,
	) {}

	@Get()
	list(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('offset') offset: string | undefined,
		@Query('limit') limit: string | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.claimAdministration.list(offset, limit);
	}

	@Delete(':claimId')
	remove(@Headers('cookie') cookieHeader: string | undefined, @Param('claimId') claimId: string) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.claimAdministration.remove(claimId);
	}
}
