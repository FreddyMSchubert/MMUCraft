const MMU_EMAIL_REGEX = /^[^\s@]+@(mmu\.ac\.uk|stu\.mmu\.ac\.uk)$/i

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase()
}

export function isAllowedMmuEmail(value: string): boolean {
  return MMU_EMAIL_REGEX.test(normalizeEmail(value))
}

export function ensureNonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}
