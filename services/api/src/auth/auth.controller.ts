import { Body, Controller, Get, Headers, HttpCode, Post, Req, Res } from '@nestjs/common'
import type { FastifyReply, FastifyRequest } from 'fastify'
import { AuthService } from './auth.service'

@Controller('api/auth')
export class AuthController {
	constructor(private readonly auth: AuthService) { }

	@Post('signup')
	createSignup(@Body() body: { email?: string }, @Req() request: FastifyRequest) {
		return this.auth.createSignup(body.email ?? '', clientIp(request))
	}

	@Post('verify-email')
	verifyEmail(@Body() body: { flowId?: string; code?: string }) {
		return this.auth.verifyEmailCode(body.flowId ?? '', body.code ?? '')
	}

	@Post('minecraft-username')
	async setMinecraftUsername(@Body() body: { flowId?: string; minecraftUsername?: string }) {
		return await this.auth.setMinecraftUsername(
			body.flowId ?? '',
			body.minecraftUsername ?? '',
		)
	}

	@Post('verify-minecraft')
	async verifyMinecraft(@Body() body: { flowId?: string; code?: string }) {
		return await this.auth.verifyMinecraftCode(body.flowId ?? '', body.code ?? '')
	}

	@Post('accept-rules')
	async acceptRules(
		@Body() body: { flowId?: string },
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		const session = await this.auth.acceptRules(body.flowId ?? '')
		this.setSessionCookie(response, session.token, session.maxAgeSeconds)

		return { ok: true }
	}

	@Post('signin')
	async signIn(
		@Body() body: { email?: string },
		@Req() request: FastifyRequest,
	) {
		return await this.auth.signIn(body.email ?? '', clientIp(request))
	}

	@Post('verify-signin')
	async verifySignIn(
		@Body() body: { flowId?: string; code?: string },
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		const session = await this.auth.verifySignIn(body.flowId ?? '', body.code ?? '')
		this.setSessionCookie(response, session.token, session.maxAgeSeconds)

		return { ok: true }
	}

	@Get('me')
	me(@Headers('cookie') cookieHeader: string | undefined) {
		return { user: this.auth.getSession(cookieHeader) }
	}

	@Post('signout')
	@HttpCode(204)
	signOut(
		@Headers('cookie') cookieHeader: string | undefined,
		@Res({ passthrough: true }) response: FastifyReply,
	) {
		this.auth.deleteSession(cookieHeader)
		response.header('Set-Cookie', 'mcstack_session=; HttpOnly; Path=/; SameSite=Lax; Max-Age=0')
	}

	private setSessionCookie(response: FastifyReply, token: string, maxAgeSeconds: number) {
		const secure = process.env.COOKIE_SECURE === 'true' ? '; Secure' : ''

		response.header(
			'Set-Cookie',
			[
				`mcstack_session=${encodeURIComponent(token)}`,
				'HttpOnly',
				'Path=/',
				'SameSite=Lax',
				`Max-Age=${maxAgeSeconds}`,
				secure,
			].filter(Boolean).join('; '),
		)
	}
}

function clientIp(request: FastifyRequest) {
	const realIp = request.headers['x-real-ip']
	return typeof realIp === 'string' ? realIp : request.ip
}
