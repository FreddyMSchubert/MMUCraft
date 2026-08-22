import type { MinecraftProfile } from './player-statistics';

export async function fetchMinecraftProfileByUuid(
	uuidInput: string,
	fallbackName = '',
	signal?: AbortSignal,
): Promise<MinecraftProfile> {
	const uuid = uuidInput.replaceAll('-', '');
	if (!/^[0-9a-f]{32}$/i.test(uuid)) throw new Error('Invalid Mojang UUID');
	const response = await fetch(
		`https://sessionserver.mojang.com/session/minecraft/profile/${encodeURIComponent(uuid)}`,
		{ cache: 'no-store', signal },
	);
	if (!response.ok) throw new Error('Mojang profile lookup failed');

	const body = (await response.json().catch(() => null)) as {
		id?: unknown;
		name?: unknown;
		properties?: { name?: unknown; value?: unknown }[];
	} | null;
	const textures = body?.properties?.find((property) => property.name === 'textures');
	const encoded = typeof textures?.value === 'string' ? textures.value : '';
	const decoded = encoded
		? (JSON.parse(Buffer.from(encoded, 'base64').toString('utf8')) as {
				textures?: { SKIN?: { url?: unknown; metadata?: { model?: unknown } } };
			})
		: null;
	const skinUrl =
		typeof decoded?.textures?.SKIN?.url === 'string'
			? decoded.textures.SKIN.url.replace(/^http:\/\//, 'https://')
			: null;

	return {
		uuid: typeof body?.id === 'string' ? body.id : uuid,
		name: typeof body?.name === 'string' ? body.name : fallbackName,
		skinUrl,
		model:
			typeof decoded?.textures?.SKIN?.metadata?.model === 'string'
				? decoded.textures.SKIN.metadata.model
				: null,
		fetchedAtUnixMs: Date.now(),
	};
}
