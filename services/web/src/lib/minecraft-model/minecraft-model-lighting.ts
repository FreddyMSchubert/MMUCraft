// Approximation of Minecraft's Bright lightmap curve, with open night sky and
// no nearby block lights. Emission sets a minimum brightness, not a scene light.
export function emissionBrightness(lightEmission: number) {
	const level = Math.min(15, Math.max(0, lightEmission)) / 15;
	return 1 - (1 - level / (4 - 3 * level)) ** 4;
}

export function previewBrightness(night: boolean) {
	if (!night) return 1;
	const ambient = 0.2;
	return (1 - (1 - (ambient * 0.96 + 0.03)) ** 4) * 0.96 + 0.03;
}
