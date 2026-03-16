interface DiscordTokenResponse {
  access_token: string
  token_type: string
  expires_in: number
  refresh_token: string
  scope: string
}

export interface DiscordUser {
  id: string
  username: string
  global_name: string | null
  avatar: string | null
  email?: string | null
}

export class DiscordOAuthService {
  constructor(
    private readonly clientId: string,
    private readonly clientSecret: string,
    private readonly redirectUri: string,
  ) {}

  getAuthorizationUrl(state: string): string {
    const url = new URL('https://discord.com/oauth2/authorize')
    url.searchParams.set('response_type', 'code')
    url.searchParams.set('client_id', this.clientId)
    url.searchParams.set('scope', 'identify email')
    url.searchParams.set('redirect_uri', this.redirectUri)
    url.searchParams.set('prompt', 'consent')
    url.searchParams.set('state', state)
    return url.toString()
  }

  async exchangeCode(code: string): Promise<DiscordTokenResponse> {
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: this.redirectUri,
    })

    const basic = Buffer.from(`${this.clientId}:${this.clientSecret}`).toString('base64')

    const response = await fetch('https://discord.com/api/oauth2/token', {
      method: 'POST',
      headers: {
        authorization: `Basic ${basic}`,
        'content-type': 'application/x-www-form-urlencoded',
      },
      body,
    })

    if (!response.ok) {
      const text = await response.text()
      throw new Error(`Discord token exchange failed: ${response.status} ${text}`)
    }

    return (await response.json()) as DiscordTokenResponse
  }

  async getCurrentUser(accessToken: string): Promise<DiscordUser> {
    const response = await fetch('https://discord.com/api/users/@me', {
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      const text = await response.text()
      throw new Error(`Discord user lookup failed: ${response.status} ${text}`)
    }

    return (await response.json()) as DiscordUser
  }
}
