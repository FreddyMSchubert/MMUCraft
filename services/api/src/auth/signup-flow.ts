export interface SignupFlow {
	email: string
	step: 'email' | 'minecraft-username' | 'minecraft-code' | 'rules'
	emailCodeHash: string
	emailCodeExpiresAt: number
	minecraftUsername?: string
	minecraftUuid?: string
	minecraftCode?: string
	minecraftCodeHash?: string
	minecraftCodeExpiresAt?: number
	minecraftCodeFailedAttempts?: number
	updatedAt: number
}

export const signupFlows = new Map<string, SignupFlow>()
