import {
	Body,
	Controller,
	Get,
	Headers,
	HttpCode,
	HttpException,
	Post,
	Req,
	Res,
} from '@nestjs/common';
import type { FastifyReply, FastifyRequest } from 'fastify';
import {
	type AuthEvent,
	type AuthJourney,
	SigninAttemptLogsService,
} from '../database/signin-attempt-logs.service';
import { AuthSessionService } from './auth-session.service';
import { AuthSigninService } from './auth-signin.service';
import { AuthSignupService } from './auth-signup.service';
import { normalizeEmail } from './auth.util';

@Controller('api/auth')
export class AuthController {
	constructor(
		private readonly signup: AuthSignupService,
		private readonly sessions: AuthSessionService,
		private readonly signin: AuthSigninService,
		private readonly signinAttempts: SigninAttemptLogsService,
	) {}

	@Post('signup')
	createSignup(
		@Body() body: { email?: string; resend?: boolean },
		@Req() request: FastifyRequest,
	) {
		const email = normalizeEmail(body.email ?? '');
		return this.track('signup', body.resend ? 'email_resend' : 'email_send', email, () =>
			this.signup.createSignup(email, clientIp(request)),
		);
	}

	@Post('verify-email')
	verifyEmail(@Body() body: { flowId?: string; code?: string }) {
		const flowId = body.flowId ?? '';
		return this.track('signup', 'email_code_input', this.signup.emailForFlow(flowId), () =>
			this.signup.verifyEmailCode(flowId, body.code ?? ''),
		);
	}

	@Post('minecraft-username')
	setMinecraftUsername(@Body() body: { flowId?: string; minecraftUsername?: string }) {
		const flowId = body.flowId ?? '';
		return this.track(
			'signup',
			'minecraft_username_input',
			this.signup.emailForFlow(flowId),
			() => this.signup.setMinecraftUsername(flowId, body.minecraftUsername ?? ''),
		);
	}

	@Post('verify-minecraft')
	verifyMinecraft(@Body() body: { flowId?: string; code?: string }) {
		const flowId = body.flowId ?? '';
		return this.track(
			'signup',
			'minecraft_code_input',
			this.signup.emailForFlow(flowId),
			() => {
				this.signup.verifyMinecraftCode(flowId, body.code ?? '');
			},
		);
	}

	@Post('accept-rules')
	async acceptRules(
		@Body() body: { flowId?: string },
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		const flowId = body.flowId ?? '';
		const session = await this.track(
			'signup',
			'rules_accept',
			this.signup.emailForFlow(flowId),
			() => this.signup.acceptRules(flowId),
		);
		this.setSessionCookie(response, session.token, session.maxAgeSeconds);

		return { ok: true };
	}

	@Post('signin')
	signIn(@Body() body: { email?: string; resend?: boolean }, @Req() request: FastifyRequest) {
		const email = normalizeEmail(body.email ?? '');
		return this.track('signin', body.resend ? 'email_resend' : 'email_send', email, () =>
			this.signin.start(email, clientIp(request)),
		);
	}

	@Post('verify-signin')
	async verifySignIn(
		@Body() body: { flowId?: string; code?: string },
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		const flowId = body.flowId ?? '';
		const session = await this.track(
			'signin',
			'email_code_input',
			this.signin.emailForFlow(flowId),
			() => this.signin.verify(flowId, body.code ?? ''),
		);
		this.setSessionCookie(response, session.token, session.maxAgeSeconds);

		return { ok: true };
	}

	@Get('me')
	me(@Headers('cookie') cookieHeader: string | undefined) {
		return { user: this.sessions.getSession(cookieHeader) };
	}

	@Post('signout')
	@HttpCode(204)
	signOut(
		@Headers('cookie') cookieHeader: string | undefined,
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		this.sessions.deleteSession(cookieHeader);
		response.header(
			'Set-Cookie',
			'mcstack_session=; HttpOnly; Path=/; SameSite=Lax; Max-Age=0',
		);
	}

	private setSessionCookie(response: FastifyReply, token: string, maxAgeSeconds: number) {
		const secure = process.env.COOKIE_SECURE === 'true' ? '; Secure' : '';

		response.header(
			'Set-Cookie',
			[
				`mcstack_session=${encodeURIComponent(token)}`,
				'HttpOnly',
				'Path=/',
				'SameSite=Lax',
				`Max-Age=${maxAgeSeconds}`,
				secure,
			]
				.filter(Boolean)
				.join('; '),
		);
	}

	private async track<T>(
		journey: AuthJourney,
		event: AuthEvent,
		email: string | null,
		action: () => T | Promise<T>,
	): Promise<T> {
		try {
			const result = await action();
			this.signinAttempts.record(email, journey, event, true, null);
			return result;
		} catch (error) {
			this.signinAttempts.record(
				email,
				journey,
				event,
				false,
				error instanceof HttpException ? error.message : 'Internal error',
			);
			throw error;
		}
	}
}

function clientIp(request: FastifyRequest) {
	const realIp = request.headers['x-real-ip'];
	return typeof realIp === 'string' ? realIp : request.ip;
}
