import { NextResponse } from 'next/server'
import { getApiBaseUrl } from '@/lib/env'

interface EmailRouteContext {
  params: Promise<{ sessionId: string }>
}

export async function POST(request: Request, context: EmailRouteContext) {
  const { sessionId } = await context.params
  const formData = await request.formData()
  const email = String(formData.get('email') ?? '')

  const response = await fetch(new URL(`/api/registration-sessions/${encodeURIComponent(sessionId)}/email`, `${getApiBaseUrl()}/`), {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json',
    },
    body: JSON.stringify({ email }),
    cache: 'no-store',
  })

  if (response.ok) {
    return NextResponse.redirect(new URL(`/register/${encodeURIComponent(sessionId)}?email=sent`, request.url), 303)
  }

  let message = 'unknown_error'
  try {
    const payload = (await response.json()) as { error?: string }
    message = encodeURIComponent(payload.error ?? message)
  } catch {
    // ignore
  }

  return NextResponse.redirect(
    new URL(`/register/${encodeURIComponent(sessionId)}?emailError=${message}`, request.url),
    303,
  )
}
