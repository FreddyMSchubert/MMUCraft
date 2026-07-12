export interface SignupFlow {
	email: string
	step: 'email' | 'minecraft-username' | 'minecraft-code' | 'rules'
	emailCodeHash: string
	emailCodeExpiresAt: number
	minecraftUsername?: string
	minecraftUuid?: string
	minecraftCodeHash?: string
	minecraftCodeExpiresAt?: number
	updatedAt: number
}

export const signupFlows = new Map<string, SignupFlow>()
