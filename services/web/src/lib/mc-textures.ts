import 'server-only'

import { readdir } from 'node:fs/promises'
import { join } from 'node:path'
import sharp from 'sharp'

const BLOCK_TEXTURE_DIR = join(
    process.cwd(),
    'public',
    'assets',
    'mc_respack',
    'assets',
    'minecraft',
    'textures',
    'block',
)

const PUBLIC_PREFIX = '/assets/mc_respack/assets/minecraft/textures/block'

async function isSquareOpaqueTexture(filePath: string): Promise<boolean> {
    try {
        const image = sharp(filePath, { animated: false })
        const metadata = await image.metadata()

        if (!metadata.width || !metadata.height) return false
        if (metadata.width !== metadata.height) return false

        if (!metadata.hasAlpha) return true

        const { data, info } = await image
            .ensureAlpha()
            .raw()
            .toBuffer({ resolveWithObject: true })

        const channels = info.channels
        const alphaIndex = channels - 1

        for (let i = alphaIndex; i < data.length; i += channels) {
            if (data[i] !== 255) return false
        }

        return true
    } catch {
        return false
    }
}

async function loadBlockTextures(): Promise<string[]> {
    try {
        const entries = await readdir(BLOCK_TEXTURE_DIR, { withFileTypes: true })

        const pngFiles = entries
            .filter((entry) => entry.isFile() && /\.png$/i.test(entry.name))
            .map((entry) => entry.name)
            .sort()

        const checks = await Promise.all(
            pngFiles.map(async (fileName) => {
                const filePath = join(BLOCK_TEXTURE_DIR, fileName)
                const ok = await isSquareOpaqueTexture(filePath)
                return ok ? `${PUBLIC_PREFIX}/${encodeURIComponent(fileName)}` : null
            }),
        )

        return checks.filter((value): value is string => value !== null)
    } catch {
        return []
    }
}

const textureListPromise = loadBlockTextures()

export async function getMinecraftBlockTextures(): Promise<string[]> {
    return textureListPromise
}