import { SiteShell } from '@/components/site-shell'
import { getMinecraftBlockTextures } from '@/lib/mc-textures'

export default async function HomePage() {
	const images = await getMinecraftBlockTextures()

	return <SiteShell images={images} />
}
