import { AuthPanel } from '@/components/auth-panel'
import { BackgroundGrid } from '@/components/background-grid'
import { getMinecraftBlockTextures } from '@/lib/mc-textures'

export default async function HomePage() {
	const images = await getMinecraftBlockTextures()

	return (
		<main className="page">
			<BackgroundGrid images={images} />
			<div className="content">
				<h1 className="title">MMU Minecraft Society</h1>
				<AuthPanel />
			</div>
		</main>
	)
}
