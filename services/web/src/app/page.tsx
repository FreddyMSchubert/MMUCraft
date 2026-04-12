import { BackgroundGrid } from '@/components/background-grid'
import { getMinecraftBlockTextures } from '@/lib/mc-textures'

interface JokeResponse {
    id: number
    joke: string
}

async function getRandomJoke(): Promise<JokeResponse | null> {
    const id = Math.floor(Math.random() * 5) + 1
    const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080'

    try {
        const response = await fetch(`${apiBaseUrl}/jokes/${id}`, {
            cache: 'no-store',
        })

        if (!response.ok) return null
        return (await response.json()) as JokeResponse
    } catch {
        console.log("Error getting joke")
        return null
    }
}

export default async function HomePage() {
    const [images, joke] = await Promise.all([
        getMinecraftBlockTextures(),
        getRandomJoke(),
    ])

    return (
        <main className="page">
            <BackgroundGrid images={images} />
            <div className="content">
                <h1 className="title">MMU Minecraft Society</h1>
                {joke ? <p className="joke">{joke.joke}</p> : null}
            </div>
        </main>
    )
}
