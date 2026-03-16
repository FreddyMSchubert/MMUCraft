import { cookies } from 'next/headers'
import type { Account, MeResponse, RegistrationSession } from '@/lib/types'
import { getApiBaseUrl } from '@/lib/env'

const API_TIMEOUT_MS = 5_000

function toApiUrl(pathname: string): string {
  return new URL(pathname, `${getApiBaseUrl()}/`).toString()
}

async function getCookieHeader(): Promise<string | undefined> {
  const store = await cookies()
  const parts = store.getAll().map(({ name, value }) => `${name}=${value}`)
  return parts.length > 0 ? parts.join('; ') : undefined
}

async function fetchJson<T>(pathname: string, init?: RequestInit): Promise<T | null> {
  try {
    const cookieHeader = await getCookieHeader()
    const response = await fetch(toApiUrl(pathname), {
      cache: 'no-store',
      ...init,
      headers: {
        accept: 'application/json',
        ...(cookieHeader ? { cookie: cookieHeader } : {}),
        ...(init?.headers ?? {}),
      },
      signal: AbortSignal.timeout(API_TIMEOUT_MS),
    })

    if (!response.ok) {
      return null
    }

    return (await response.json()) as T
  } catch {
    return null
  }
}

export async function getMe(): Promise<Account | null> {
  const payload = await fetchJson<MeResponse>('/api/me')
  return payload?.account ?? null
}

export async function getRegistrationSession(sessionId: string): Promise<RegistrationSession | null> {
  return fetchJson<RegistrationSession>(`/api/registration-sessions/${encodeURIComponent(sessionId)}`)
}
