export interface SignupFlow {
	email: string;
	step: 'email' | 'minecraft-username' | 'minecraft-code' | 'rules';
	emailCodeHash: string;
	emailCodeExpiresAt: number;
	emailCodeFailedAttempts?: number;
	minecraftUsername?: string;
	minecraftUuid?: string;
	minecraftCodeHash?: string;
	minecraftCodeExpiresAt?: number;
	minecraftCodeFailedAttempts?: number;
	updatedAt: number;
}

export const signupFlows = new Map<string, SignupFlow>();

export interface SigninFlow {
	userId: number;
	codeHash: string;
	expiresAtUnixMs: number;
	failedAttempts: number;
}

export const signinFlows = new Map<string, SigninFlow>();
