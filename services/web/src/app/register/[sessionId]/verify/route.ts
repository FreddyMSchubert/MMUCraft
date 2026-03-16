import { NextResponse } from 'next/server'
import { getApiBaseUrl } from '@/lib/env'

interface VerifyRouteContext {
  params: Promise<{ sessionId: string }>
}

export async function POST(request: Request, context: VerifyRouteContext) {
  const { sessionId } = await context.params
  const formData = await request.formData()
  const code = String(formData.get('code') ?? '')

  const response = await fetch(
    new URL(`/api/registration-sessions/${encodeURIComponent(sessionId)}/verify-email`, `${getApiBaseUrl()}/`),
    {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        accept: 'application/json',
      },
      body: JSON.stringify({ code }),
      cache: 'no-store',
    },
  )

  if (response.ok) {
    return NextResponse.redirect(new URL(`/register/${encodeURIComponent(sessionId)}`, request.url), 303)
  }

  let message = 'unknown_error'
  try {
    const payload = (await response.json()) as { error?: string }
    message = encodeURIComponent(payload.error ?? message)
  } catch {
    // ignore
  }

  return NextResponse.redirect(
    new URL(`/register/${encodeURIComponent(sessionId)}?verifyError=${message}`, request.url),
    303,
  )
}
