// Element emission sets a linear minimum brightness, from 0 to full brightness at 15.
export function emissionBrightness(lightEmission: number) {
	return Math.min(15, Math.max(0, lightEmission)) / 15;
}

export function previewBrightness(night: boolean) {
	return night ? 0.11 : 1;
}
