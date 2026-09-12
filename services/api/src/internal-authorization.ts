import { createHash, timingSafeEqual } from 'node:crypto';
import { UnauthorizedException } from '@nestjs/common';

export function requireInternalAuthorization(
	authorization: string | undefined,
	errorMessage = 'Invalid internal API credentials',
) {
	const expected = process.env.VELOCITY_API_SECRET ?? '';
	const supplied = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
	if (!expected || !constantTimeEquals(supplied, expected)) {
		throw new UnauthorizedException(errorMessage);
	}
}

function constantTimeEquals(left: string, right: string) {
	const leftHash = createHash('sha256').update(left).digest();
	const rightHash = createHash('sha256').update(right).digest();
	return timingSafeEqual(leftHash, rightHash);
}
