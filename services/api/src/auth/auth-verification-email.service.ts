import { HttpException, HttpStatus, Injectable, ServiceUnavailableException } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { and, eq, gte, lt } from 'drizzle-orm';
import { ASSETS } from '../assets';
import { DatabaseService, emailSendEvents } from '../database/database.service';
import { AUTH_CODE_ITEMS, displayAuthCode, hashSecret, normalizeIpBucket } from './auth.util';

const FIVE_MINUTES_MS = 5 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const EMAIL_SEND_LIMITS = { fiveMinutes: 2, day: 8 };
const IP_SEND_LIMITS = { fiveMinutes: 10, day: 30 };
const AUTH_CODE_IMAGE_BASE = `${ASSETS.minecraft.vanilla}/textures/`;
const AUTH_CODE_IMAGES: Partial<Record<(typeof AUTH_CODE_ITEMS)[number], string>> = {
	Apple: 'item/apple.png',
	Coal: 'item/coal.png',
	Diamond: 'item/diamond.png',
	Egg: 'item/egg.png',
	'Gold Ingot': 'item/gold_ingot.png',
	Iron: 'item/raw_iron.png',
	'Lapis Lazuli': 'item/lapis_lazuli.png',
	Pickaxe: 'item/iron_pickaxe.png',
	Redstone: 'item/redstone.png',
	Sword: 'item/wooden_sword.png',
	Totem: 'item/totem_of_undying.png',
	Wheat: 'item/wheat.png',
};

@Injectable()
export class AuthVerificationEmailService {
	constructor(private readonly database: DatabaseService) {}

	reserveSend(email: string, sourceIp: string, now: number) {
		const emailHash = hashSecret(`email:${email}`);
		const ipHash = hashSecret(`ip:${normalizeIpBucket(sourceIp)}`);
		const dayCutoff = now - DAY_MS;
		this.database.connection.transaction((tx) => {
			tx.delete(emailSendEvents).where(lt(emailSendEvents.sent_at_unix_ms, dayCutoff)).run();
			const emailEvents = tx
				.select({ sentAt: emailSendEvents.sent_at_unix_ms })
				.from(emailSendEvents)
				.where(
					and(
						eq(emailSendEvents.email_hash, emailHash),
						gte(emailSendEvents.sent_at_unix_ms, dayCutoff),
					),
				)
				.all();
			const ipEvents = tx
				.select({ sentAt: emailSendEvents.sent_at_unix_ms })
				.from(emailSendEvents)
				.where(
					and(
						eq(emailSendEvents.ip_hash, ipHash),
						gte(emailSendEvents.sent_at_unix_ms, dayCutoff),
					),
				)
				.all();
			const emailFiveMinuteRetry = retryAtForLimit(
				emailEvents,
				EMAIL_SEND_LIMITS.fiveMinutes,
				FIVE_MINUTES_MS,
				now,
			);
			const emailDayRetry = retryAtForLimit(emailEvents, EMAIL_SEND_LIMITS.day, DAY_MS, now);
			const ipFiveMinuteRetry = retryAtForLimit(
				ipEvents,
				IP_SEND_LIMITS.fiveMinutes,
				FIVE_MINUTES_MS,
				now,
			);
			const ipDayRetry = retryAtForLimit(ipEvents, IP_SEND_LIMITS.day, DAY_MS, now);
			const emailRetries = [emailFiveMinuteRetry, emailDayRetry].filter(
				(retry): retry is number => retry !== null,
			);
			const ipRetries = [ipFiveMinuteRetry, ipDayRetry].filter(
				(retry): retry is number => retry !== null,
			);
			if (emailRetries.length || ipRetries.length) {
				const retryAfterSeconds = Math.max(
					1,
					Math.ceil((Math.max(...emailRetries, ...ipRetries) - now) / 1000),
				);
				throw new HttpException(
					{
						statusCode: HttpStatus.TOO_MANY_REQUESTS,
						error: 'Too Many Requests',
						message: emailRateLimitMessage({
							emailFiveMinutes: emailFiveMinuteRetry !== null,
							emailDay: emailDayRetry !== null,
							ipFiveMinutes: ipFiveMinuteRetry !== null,
							ipDay: ipDayRetry !== null,
							retryAfterSeconds,
						}),
						rateLimit:
							emailRetries.length && ipRetries.length
								? 'email-and-network'
								: emailRetries.length
									? 'email'
									: 'network',
						retryAfterSeconds,
					},
					HttpStatus.TOO_MANY_REQUESTS,
				);
			}
			tx.insert(emailSendEvents)
				.values({
					id: randomUUID(),
					email_hash: emailHash,
					ip_hash: ipHash,
					sent_at_unix_ms: now,
				})
				.run();
		});
	}

	async deliverCode(email: string, code: string, kind: 'signup' | 'signin') {
		const apiKey = process.env.RESEND_API_KEY;
		const from = process.env.RESEND_FROM ?? 'MMU Minecraft Society <onboarding@resend.dev>';
		const recipientDomain = email.split('@')[1] ?? 'invalid';
		if (!apiKey) {
			console.warn('[auth-email] WARN Delivery skipped', {
				kind,
				recipientDomain,
				reason: 'RESEND_API_KEY is missing from the API process environment',
			});
			throw new ServiceUnavailableException('Verification email could not be sent');
		}
		try {
			const response = await fetch('https://api.resend.com/emails', {
				method: 'POST',
				signal: AbortSignal.timeout(10_000),
				headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
				body: JSON.stringify({
					from,
					to: [email],
					subject: `Your MMU Minecraft Society ${kind} code`,
					text: `Your verification code is ${displayAuthCode(code)}. It expires in 10 minutes. If you did not request this, you can ignore this email.`,
					html: verificationCodeEmailHtml(code),
				}),
			});
			if (!response.ok) {
				console.error('[auth-email] ERROR Resend rejected verification email', {
					kind,
					recipientDomain,
					status: response.status,
					response: await response.text(),
				});
				throw new ServiceUnavailableException('Verification email could not be sent');
			}
		} catch (error) {
			if (error instanceof ServiceUnavailableException) throw error;
			console.error('[auth-email] ERROR Resend request failed', {
				kind,
				recipientDomain,
				error: error instanceof Error ? `${error.name}: ${error.message}` : String(error),
			});
			throw new ServiceUnavailableException('Verification email could not be sent');
		}
	}
}

function retryAtForLimit(
	events: { sentAt: number }[],
	limit: number,
	windowMs: number,
	now: number,
) {
	const eventsInWindow = events.filter((event) => event.sentAt >= now - windowMs);
	return eventsInWindow.length < limit
		? null
		: Math.min(...eventsInWindow.map((event) => event.sentAt)) + windowMs + 1;
}

function emailRateLimitMessage(limits: {
	emailFiveMinutes: boolean;
	emailDay: boolean;
	ipFiveMinutes: boolean;
	ipDay: boolean;
	retryAfterSeconds: number;
}) {
	const emailLimits = [
		limits.emailFiveMinutes ? '2 emails in 5 minutes' : '',
		limits.emailDay ? '8 emails in 24 hours' : '',
	].filter(Boolean);
	const networkLimits = [
		limits.ipFiveMinutes ? '10 emails in 5 minutes' : '',
		limits.ipDay ? '30 emails in 24 hours' : '',
	].filter(Boolean);
	const retry = `Try again in ${formatDuration(limits.retryAfterSeconds)}.`;

	if (emailLimits.length && networkLimits.length) {
		return `Both limits were reached: this email address reached ${emailLimits.join(' and ')}, and this network reached ${networkLimits.join(' and ')}. ${retry} A different network will not clear the email-address limit. If you still need help, contact the committee.`;
	}
	if (emailLimits.length) {
		return `Email-address limit reached: this address reached ${emailLimits.join(' and ')}. ${retry} If you did not make these requests or still need help, contact the committee.`;
	}
	return `Network limit reached: this network reached ${networkLimits.join(' and ')}. ${retry} This can happen on shared university, accommodation, workplace, or public Wi-Fi. You can wait or try a different trusted connection, such as mobile data. If you still need help, contact the committee.`;
}

function verificationCodeEmailHtml(code: string) {
	const items = code
		.split('|')
		.map((item) => {
			const image = AUTH_CODE_IMAGES[item as keyof typeof AUTH_CODE_IMAGES];
			return `<td style="padding: 8px; text-align: center; font-weight: bold">${image ? `<img src="${AUTH_CODE_IMAGE_BASE}${image}" alt="" width="40" height="40" style="display: block; margin: 0 auto 4px; image-rendering: pixelated; object-fit: contain">` : ''}${item}</td>`;
		})
		.join('');
	return `<p>Your verification code is:</p><table role="presentation"><tr>${items}</tr></table><p>It expires in 10 minutes. If you did not request this, you can ignore this email.</p>`;
}

function formatDuration(totalSeconds: number) {
	const hours = Math.floor(totalSeconds / 3600);
	const minutes = Math.floor((totalSeconds % 3600) / 60);
	const seconds = totalSeconds % 60;
	return [
		hours ? `${hours} hour${hours === 1 ? '' : 's'}` : '',
		minutes ? `${minutes} minute${minutes === 1 ? '' : 's'}` : '',
		!hours && seconds ? `${seconds} second${seconds === 1 ? '' : 's'}` : '',
	]
		.filter(Boolean)
		.join(' ');
}
