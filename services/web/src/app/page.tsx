import { MinecraftHome } from '@/components/landing/minecraft-home';
import { getLandingVisuals } from '@/lib/site-assets';

export const dynamic = 'force-dynamic';

function imageVersion(tag = 'dev', releaseName = '') {
	const cleanTag = tag.trim() || 'dev';
	if (cleanTag.toLowerCase() === 'dev') return 'MMU Minecraft dev';
	const displayTag =
		/^v/i.test(cleanTag) || !/^\d+\.\d+\.\d+/.test(cleanTag) ? cleanTag : `v${cleanTag}`;
	return `MMU Minecraft ${displayTag}${releaseName.trim() ? ` - ${releaseName.trim()}` : ''}`;
}

export default function HomePage() {
	const { panorama, splash } = getLandingVisuals();

	return (
		<MinecraftHome
			panorama={panorama}
			splash={splash}
			imageVersion={imageVersion(process.env.SITE_IMAGE_TAG, process.env.SITE_RELEASE_NAME)}
			imageReleaseUrl={process.env.SITE_RELEASE_URL ?? ''}
			discordUrl={process.env.DISCORD_URL ?? ''}
			instagramUrl={process.env.INSTAGRAM_URL ?? ''}
		/>
	);
}
