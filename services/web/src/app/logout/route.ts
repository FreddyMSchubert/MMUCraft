import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { getApiBaseUrl, getSessionCookieName } from '@/lib/env'

export async function POST(request: Request) {
  const cookieName = getSessionCookieName()
  const sessionId = (await cookies()).get(cookieName)?.value

  if (sessionId) {
    await fetch(new URL('/api/auth/logout', `${getApiBaseUrl()}/`), {
      method: 'POST',
      headers: {
        cookie: `${cookieName}=${sessionId}`,
      },
      redirect: 'manual',
      cache: 'no-store',
    }).catch(() => undefined)
  }

  const response = NextResponse.redirect(new URL('/', request.url), 303)
  response.cookies.delete(cookieName)
  return response
}
