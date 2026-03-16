export interface Account {
  id: string
  minecraftUuid: string
  minecraftUsername: string
  email: string
  discordUserId: string
  discordUsername: string
  discordGlobalName: string | null
  discordAvatar: string | null
  createdAt: string
  updatedAt: string
}

export interface RegistrationSession {
  id: string
  playerUuid: string
  username: string
  createdAt: string
  expiresAt: string
  email: string | null
  emailVerifiedAt: string | null
  status: 'awaiting_email' | 'awaiting_email_verification' | 'awaiting_discord' | 'complete'
  accountId: string | null
}

export interface MeResponse {
  account: Account
}
