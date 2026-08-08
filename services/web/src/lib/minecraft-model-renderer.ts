import * as THREE from 'three'

type FaceName = 'north' | 'east' | 'south' | 'west' | 'up' | 'down'
type PreviewView = 'basic3d' | 'cosmetic'

export interface TextureAnimationOptions {
	frameDelayMs: number
	frames: number[] | null
}

export interface MinecraftModelRendererOptions {
	assetRoot?: string
	autoRotate?: boolean
	background?: string | null
	canvasClassName?: string
	defaultTint?: RgbColor
	dyeable?: boolean
	enableDrag?: boolean
	frameDelayMs?: number
	frameSequence?: number[] | null
	rotationSpeed?: number
	textureSource?: string
	view?: PreviewView
}

export interface MinecraftItemSource {
	assetRoot?: string
	itemId?: string
	model?: MinecraftModel
	modelUrl?: string | null
	textureUrl?: string | null
}

interface RgbColor {
	r: number
	g: number
	b: number
}

export interface MinecraftModel {
	parent?: string
	format_version?: string
	credit?: string
	ambientocclusion?: boolean
	gui_light?: string
	texture_size?: [number, number]
	textures?: Record<string, string>
	display?: Record<string, MinecraftDisplayTransform>
	elements?: MinecraftElement[]
	groups?: unknown[]
}

interface MinecraftItemDefinition {
	model?: MinecraftItemModelNode
}

interface MinecraftItemModelNode {
	base?: string
	cases?: Array<{ model?: MinecraftItemModelNode }>
	entries?: Array<{ model?: MinecraftItemModelNode }>
	fallback?: MinecraftItemModelNode
	model?: string
	models?: MinecraftItemModelNode[]
	on_false?: MinecraftItemModelNode
	on_true?: MinecraftItemModelNode
	type?: string
}

interface MinecraftDisplayTransform {
	rotation?: number[]
	translation?: number[]
	scale?: number[]
}

interface MinecraftElement {
	from?: number[]
	to?: number[]
	rotation?: MinecraftElementRotation
	shade?: boolean
	light_emission?: number
	faces?: Partial<Record<FaceName, MinecraftFace>>
}

interface MinecraftElementRotation {
	angle?: number
	axis?: 'x' | 'y' | 'z'
	origin?: number[]
	rescale?: boolean
	x?: number
	y?: number
	z?: number
}

interface MinecraftFace {
	texture?: string
	uv?: number[]
	rotation?: number
	tintindex?: number
}

const FACE_ORDER: FaceName[] = ['north', 'east', 'south', 'west', 'up', 'down']
const FACE_AXIS: Record<FaceName, 'x' | 'y' | 'z'> = {
	north: 'z',
	south: 'z',
	east: 'x',
	west: 'x',
	up: 'y',
	down: 'y',
}
const FACE_SIGN: Record<FaceName, number> = {
	north: -1,
	south: 1,
	east: 1,
	west: -1,
	up: 1,
	down: -1,
}
const FACE_UV_VERTEX_ORDER: Record<FaceName, number[]> = {
	north: [1, 0, 3, 2],
	south: [1, 0, 3, 2],
	east: [1, 0, 3, 2],
	west: [1, 0, 3, 2],
	up: [3, 2, 1, 0],
	down: [3, 2, 1, 0],
}

const DEGENERATE_OFFSET = 0.001
const FACE_TINT_DEFAULT = -1
const MODEL_UV_UNITS = 16
const TICK_MS = 50
const MISSING_TEXTURE_SIZE = 16
const imageSourceCache = new Map<string, Promise<HTMLImageElement>>()

function clamp(value: number, min: number, max: number) {
	return Math.max(min, Math.min(max, value))
}

function toRadians(degrees = 0) {
	return THREE.MathUtils.degToRad(Number(degrees) || 0)
}

function normalizeVector3(values: unknown, fallback = [0, 0, 0]) {
	const source = Array.isArray(values) && values.length >= 3 ? values : fallback
	return new THREE.Vector3(Number(source[0]) || 0, Number(source[1]) || 0, Number(source[2]) || 0)
}

function normalizeMinMax(from: THREE.Vector3, to: THREE.Vector3) {
	return {
		from: new THREE.Vector3(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
		to: new THREE.Vector3(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)),
	}
}

function deepClone<T>(value: T): T {
	return value == null ? value : JSON.parse(JSON.stringify(value)) as T
}

function resolveTextureReference(textureRef: string | undefined, textures: Record<string, string> = {}) {
	if (!textureRef) return null

	let current = textureRef
	const visited = new Set<string>()
	while (current.startsWith('#')) {
		const key = current.slice(1)
		if (visited.has(key)) return null
		visited.add(key)
		current = textures[key] ?? ''
		if (!current) return null
	}

	return current
}

function inferDefaultUv(faceName: FaceName, from: THREE.Vector3, to: THREE.Vector3) {
	switch (faceName) {
		case 'down':
			return [from.x, 16 - to.z, to.x, 16 - from.z]
		case 'up':
			return [from.x, from.z, to.x, to.z]
		case 'north':
			return [16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y]
		case 'south':
			return [from.x, 16 - to.y, to.x, 16 - from.y]
		case 'west':
			return [from.z, 16 - to.y, to.z, 16 - from.y]
		case 'east':
			return [16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y]
	}
}

function getFaceVertices(faceName: FaceName, from: THREE.Vector3, to: THREE.Vector3, collapsedAxis: 'x' | 'y' | 'z' | null) {
	const offset = collapsedAxis === FACE_AXIS[faceName] ? DEGENERATE_OFFSET * FACE_SIGN[faceName] : 0

	switch (faceName) {
		case 'north':
			return [
				new THREE.Vector3(from.x, to.y, from.z + offset),
				new THREE.Vector3(to.x, to.y, from.z + offset),
				new THREE.Vector3(to.x, from.y, from.z + offset),
				new THREE.Vector3(from.x, from.y, from.z + offset),
			]
		case 'south':
			return [
				new THREE.Vector3(to.x, to.y, to.z + offset),
				new THREE.Vector3(from.x, to.y, to.z + offset),
				new THREE.Vector3(from.x, from.y, to.z + offset),
				new THREE.Vector3(to.x, from.y, to.z + offset),
			]
		case 'east':
			return [
				new THREE.Vector3(to.x + offset, to.y, from.z),
				new THREE.Vector3(to.x + offset, to.y, to.z),
				new THREE.Vector3(to.x + offset, from.y, to.z),
				new THREE.Vector3(to.x + offset, from.y, from.z),
			]
		case 'west':
			return [
				new THREE.Vector3(from.x + offset, to.y, to.z),
				new THREE.Vector3(from.x + offset, to.y, from.z),
				new THREE.Vector3(from.x + offset, from.y, from.z),
				new THREE.Vector3(from.x + offset, from.y, to.z),
			]
		case 'up':
			return [
				new THREE.Vector3(from.x, to.y + offset, to.z),
				new THREE.Vector3(to.x, to.y + offset, to.z),
				new THREE.Vector3(to.x, to.y + offset, from.z),
				new THREE.Vector3(from.x, to.y + offset, from.z),
			]
		case 'down':
			return [
				new THREE.Vector3(from.x, from.y + offset, from.z),
				new THREE.Vector3(to.x, from.y + offset, from.z),
				new THREE.Vector3(to.x, from.y + offset, to.z),
				new THREE.Vector3(from.x, from.y + offset, to.z),
			]
	}
}

function getCollapsedAxis(from: THREE.Vector3, to: THREE.Vector3): 'x' | 'y' | 'z' | null {
	if (Math.abs(to.x - from.x) < 1e-7) return 'x'
	if (Math.abs(to.y - from.y) < 1e-7) return 'y'
	if (Math.abs(to.z - from.z) < 1e-7) return 'z'
	return null
}

function quadHasArea(vertices: THREE.Vector3[]) {
	if (vertices.length !== 4) return false
	const edgeA = new THREE.Vector3().subVectors(vertices[1]!, vertices[0]!)
	const edgeB = new THREE.Vector3().subVectors(vertices[3]!, vertices[0]!)
	return new THREE.Vector3().crossVectors(edgeA, edgeB).lengthSq() > 1e-12
}

function rotateQuadUvs(uvs: THREE.Vector2[], steps: number) {
	const normalizedSteps = ((steps % 4) + 4) % 4
	let current = [...uvs]
	for (let index = 0; index < normalizedSteps; index += 1) {
		current = [current[3]!, current[0]!, current[1]!, current[2]!]
	}
	return current
}

function getRotationSteps(rotationDegrees = 0) {
	const snapped = Math.round((Number(rotationDegrees) || 0) / 90)
	return ((snapped % 4) + 4) % 4
}

function getUvCorners(faceName: FaceName, uvRect: number[], rotationDegrees = 0) {
	const [u1, v1, u2, v2] = uvRect.map((value) => Number(value) || 0)
	const faceSpaceCorners = [
		new THREE.Vector2(u1! / MODEL_UV_UNITS, v1! / MODEL_UV_UNITS),
		new THREE.Vector2(u2! / MODEL_UV_UNITS, v1! / MODEL_UV_UNITS),
		new THREE.Vector2(u2! / MODEL_UV_UNITS, v2! / MODEL_UV_UNITS),
		new THREE.Vector2(u1! / MODEL_UV_UNITS, v2! / MODEL_UV_UNITS),
	]
	const rotated = rotateQuadUvs(faceSpaceCorners, getRotationSteps(rotationDegrees))
	return FACE_UV_VERTEX_ORDER[faceName].map((index) => rotated[index]!)
}

function getRescaleMultiplier(angleDegrees: number) {
	const radians = Math.abs(toRadians(angleDegrees))
	const cosine = Math.abs(Math.cos(radians))
	return cosine < 1e-6 ? 1 : 1 / cosine
}

function applySingleAxisRotation(
	vertex: THREE.Vector3,
	origin: THREE.Vector3,
	axis: 'x' | 'y' | 'z',
	angle: number,
	rescale = false,
) {
	const moved = vertex.clone().sub(origin)
	if (rescale && angle) {
		const factor = getRescaleMultiplier(angle)
		if (axis === 'x') moved.multiply(new THREE.Vector3(1, factor, factor))
		if (axis === 'y') moved.multiply(new THREE.Vector3(factor, 1, factor))
		if (axis === 'z') moved.multiply(new THREE.Vector3(factor, factor, 1))
	}

	const axisVector = axis === 'x'
		? new THREE.Vector3(1, 0, 0)
		: axis === 'y'
			? new THREE.Vector3(0, 1, 0)
			: new THREE.Vector3(0, 0, 1)
	return moved.applyAxisAngle(axisVector, toRadians(angle)).add(origin)
}

function applyRotationSpec(vertex: THREE.Vector3, rotationSpec?: MinecraftElementRotation) {
	if (!rotationSpec) return vertex

	const origin = normalizeVector3(rotationSpec.origin)
	let result = vertex.clone()
	if (rotationSpec.axis && Number(rotationSpec.angle || 0) !== 0) {
		return applySingleAxisRotation(
			result,
			origin,
			rotationSpec.axis,
			Number(rotationSpec.angle) || 0,
			Boolean(rotationSpec.rescale),
		)
	}

	for (const axis of ['x', 'y', 'z'] as const) {
		const angle = Number(rotationSpec[axis] || 0)
		if (!angle) continue
		result = applySingleAxisRotation(result, origin, axis, angle, Boolean(rotationSpec.rescale))
	}
	return result
}

function modelSpaceToWorld(vertex: THREE.Vector3) {
	return new THREE.Vector3((vertex.x - 8) / 16, (vertex.y - 8) / 16, (vertex.z - 8) / 16)
}

function hsvToRgb(hueInput: number, saturation = 1, value = 1): RgbColor {
	const hue = ((hueInput % 360) + 360) % 360
	const c = value * saturation
	const x = c * (1 - Math.abs(((hue / 60) % 2) - 1))
	const m = value - c
	let r = 0
	let g = 0
	let b = 0

	if (hue < 60) {
		r = c
		g = x
	} else if (hue < 120) {
		r = x
		g = c
	} else if (hue < 180) {
		g = c
		b = x
	} else if (hue < 240) {
		g = x
		b = c
	} else if (hue < 300) {
		r = x
		b = c
	} else {
		r = c
		b = x
	}

	return {
		r: Math.round((r + m) * 255),
		g: Math.round((g + m) * 255),
		b: Math.round((b + m) * 255),
	}
}

function rgbToThreeColor(rgb: RgbColor) {
	return new THREE.Color(rgb.r / 255, rgb.g / 255, rgb.b / 255)
}

function parseColorValue(value: unknown, fallback: RgbColor = { r: 255, g: 255, b: 255 }): RgbColor {
	if (value && typeof value === 'object' && !Array.isArray(value)) {
		const candidate = value as Partial<RgbColor>
		return {
			r: clamp(Math.round(Number(candidate.r) || fallback.r), 0, 255),
			g: clamp(Math.round(Number(candidate.g) || fallback.g), 0, 255),
			b: clamp(Math.round(Number(candidate.b) || fallback.b), 0, 255),
		}
	}

	return fallback
}

function hasMaterialColor(material: THREE.Material): material is THREE.Material & { color: THREE.Color } {
	return 'color' in material && material.color instanceof THREE.Color
}

function hasMaterialEmissive(material: THREE.Material): material is THREE.Material & { emissive: THREE.Color } {
	return 'emissive' in material && material.emissive instanceof THREE.Color
}

function createCanvas(width: number, height: number) {
	const canvas = document.createElement('canvas')
	canvas.width = width
	canvas.height = height
	return canvas
}

function configureThreeTexture(texture: THREE.CanvasTexture) {
	texture.colorSpace = THREE.SRGBColorSpace
	texture.magFilter = THREE.NearestFilter
	texture.minFilter = THREE.NearestFilter
	texture.generateMipmaps = false
	texture.anisotropy = 1
	texture.wrapS = THREE.ClampToEdgeWrapping
	texture.wrapT = THREE.ClampToEdgeWrapping
	texture.flipY = false
	texture.needsUpdate = true
	return texture
}

function drawMissingTexture(ctx: CanvasRenderingContext2D, width: number, height: number) {
	ctx.clearRect(0, 0, width, height)
	const block = Math.max(2, Math.floor(width / 4))
	for (let y = 0; y < height; y += block) {
		for (let x = 0; x < width; x += block) {
			ctx.fillStyle = ((x / block) + (y / block)) % 2 === 0 ? '#111111' : '#ff00ff'
			ctx.fillRect(x, y, block, block)
		}
	}
}

function loadImageFromSource(url: string) {
	const cached = imageSourceCache.get(url)
	if (cached) return cached

	const loading = (async () => {
		const response = await fetch(url, { cache: 'force-cache' })
		if (!response.ok) throw new Error(`Could not load texture source: ${url}`)
		const objectUrl = URL.createObjectURL(await response.blob())
		return await new Promise<HTMLImageElement>((resolve, reject) => {
			const image = new Image()
			image.crossOrigin = 'anonymous'
			image.onload = () => {
				URL.revokeObjectURL(objectUrl)
				resolve(image)
			}
			image.onerror = () => {
				URL.revokeObjectURL(objectUrl)
				reject(new Error(`Could not load texture source: ${url}`))
			}
			image.src = objectUrl
		})
	})()
	imageSourceCache.set(url, loading)
	void loading.catch(() => imageSourceCache.delete(url))
	return loading
}

class ManagedTexture {
	canvas = createCanvas(MISSING_TEXTURE_SIZE, MISSING_TEXTURE_SIZE)
	context = this.canvas.getContext('2d')!
	texture = configureThreeTexture(new THREE.CanvasTexture(this.canvas))
	sourceImage: HTMLImageElement | null = null
	frameWidth = MISSING_TEXTURE_SIZE
	frameHeight = MISSING_TEXTURE_SIZE
	frameCount = 1
	currentFrame = 0
	frameElapsedMs = 0
	frameSequenceIndex = 0
	frameSequence: number[] | null

	constructor(frameSequence: number[] | null) {
		this.frameSequence = frameSequence
		this.drawMissing()
	}

	drawMissing() {
		this.frameWidth = this.canvas.width
		this.frameHeight = this.canvas.height
		this.frameCount = 1
		this.currentFrame = 0
		drawMissingTexture(this.context, this.canvas.width, this.canvas.height)
		this.texture.needsUpdate = true
	}

	setImage(image: HTMLImageElement) {
		const sourceWidth = image.naturalWidth || image.width || MISSING_TEXTURE_SIZE
		const sourceHeight = image.naturalHeight || image.height || sourceWidth
		const isAnimatedVerticalStrip = sourceHeight > sourceWidth

		this.sourceImage = image
		this.frameWidth = sourceWidth
		this.frameHeight = isAnimatedVerticalStrip ? sourceWidth : sourceHeight
		this.frameCount = isAnimatedVerticalStrip ? Math.max(1, Math.floor(sourceHeight / sourceWidth)) : 1
		this.currentFrame = 0
		this.frameElapsedMs = 0
		this.frameSequenceIndex = 0

		if (this.canvas.width !== this.frameWidth || this.canvas.height !== this.frameHeight) {
			this.canvas.width = this.frameWidth
			this.canvas.height = this.frameHeight
			this.context = this.canvas.getContext('2d')!
		}

		const initialFrame = this.frameSequence?.[0] ?? 0
		this.drawFrame(initialFrame)
	}

	drawFrame(frameIndex: number) {
		if (!this.sourceImage) {
			this.drawMissing()
			return
		}

		const frame = this.frameCount > 0 ? ((frameIndex % this.frameCount) + this.frameCount) % this.frameCount : 0
		const sourceY = frame * this.frameHeight

		this.context.clearRect(0, 0, this.canvas.width, this.canvas.height)
		this.context.drawImage(
			this.sourceImage,
			0,
			sourceY,
			this.frameWidth,
			this.frameHeight,
			0,
			0,
			this.canvas.width,
			this.canvas.height,
		)

		this.currentFrame = frame
		this.texture.needsUpdate = true
	}

	update(deltaMs: number, frameDelayMs: number) {
		if (this.frameCount <= 1) return
		const safeDelay = Math.max(TICK_MS, Number(frameDelayMs) || TICK_MS)
		this.frameElapsedMs += deltaMs

		while (this.frameElapsedMs >= safeDelay) {
			this.frameElapsedMs -= safeDelay
			if (this.frameSequence?.length) {
				this.frameSequenceIndex = (this.frameSequenceIndex + 1) % this.frameSequence.length
				this.drawFrame(this.frameSequence[this.frameSequenceIndex]!)
			} else {
				this.drawFrame(this.currentFrame + 1)
			}
		}
	}

	dispose() {
		this.texture.dispose()
		this.canvas.width = 0
		this.canvas.height = 0
		this.sourceImage = null
	}
}

class TextureRegistry {
	private readonly handles = new Map<string, ManagedTexture>()

	constructor(private readonly frameSequence: number[] | null) {}

	async get(source: string | null) {
		return (await this.getHandle(source)).texture
	}

	async getHandle(source: string | null) {
		const key = source ?? ''
		const existing = this.handles.get(key)
		if (existing) return existing

		const handle = new ManagedTexture(this.frameSequence)
		if (source) {
			try {
				handle.setImage(await loadImageFromSource(source))
			} catch {
				handle.drawMissing()
			}
		}
		this.handles.set(key, handle)
		return handle
	}

	update(deltaMs: number, frameDelayMs: number) {
		for (const handle of this.handles.values()) handle.update(deltaMs, frameDelayMs)
	}

	isAnimated() {
		return [...this.handles.values()].some((handle) => handle.frameCount > 1)
	}

	dispose() {
		for (const handle of this.handles.values()) handle.dispose()
		this.handles.clear()
	}
}

interface ResolvedItemSource {
	fallbackTexture: string | null
	model: MinecraftModel
}

const modelSourceCache = new Map<string, Promise<MinecraftModel | null>>()
const itemDefinitionCache = new Map<string, Promise<MinecraftItemDefinition | null>>()

function parseResourceId(value: string, fallbackNamespace = 'minecraft') {
	const [namespace, path] = value.includes(':') ? value.split(':', 2) : [fallbackNamespace, value]
	return { namespace: namespace || fallbackNamespace, path: path || '' }
}

function qualifyModelTextures(model: MinecraftModel, namespace: string, assetRoot: string) {
	const textures = Object.fromEntries(Object.entries(model.textures ?? {}).map(([key, value]) => {
		if (value.startsWith('#') || /^(?:https?:|\/)/.test(value)) return [key, value]
		const texture = parseResourceId(value, namespace)
		if (texture.namespace !== 'minecraft') return [key, '']
		return [key, `${assetRoot}/${texture.namespace}/textures/${texture.path}.png`]
	}))
	return { ...model, textures }
}

function mergeModels(parent: MinecraftModel, child: MinecraftModel): MinecraftModel {
	return {
		...parent,
		...child,
		textures: { ...parent.textures, ...child.textures },
		display: { ...parent.display, ...child.display },
		elements: child.elements ?? parent.elements,
	}
}

async function fetchModel(url: string) {
	const cached = modelSourceCache.get(url)
	if (cached) return cached
	const loading = fetch(url, { cache: 'force-cache' })
		.then((response) => response.ok ? response.json() as Promise<MinecraftModel> : null)
		.catch(() => null)
	modelSourceCache.set(url, loading)
	return loading
}

async function fetchItemDefinition(url: string) {
	const cached = itemDefinitionCache.get(url)
	if (cached) return cached
	const loading = fetch(url, { cache: 'force-cache' })
		.then((response) => response.ok ? response.json() as Promise<MinecraftItemDefinition> : null)
		.catch(() => null)
	itemDefinitionCache.set(url, loading)
	return loading
}

function itemDefinitionModel(definition: MinecraftItemDefinition | null) {
	const findModel = (node: MinecraftItemModelNode | undefined): string | null => {
		if (!node) return null
		if (node.type === 'minecraft:model' && typeof node.model === 'string') return node.model
		if (node.type === 'minecraft:special' && node.base) return node.base
		for (const candidate of [node.on_true, node.on_false, node.fallback]) {
			const found = findModel(candidate)
			if (found) return found
		}
		for (const candidate of [...(node.models ?? []), ...(node.entries ?? []).map((entry) => entry.model), ...(node.cases ?? []).map((entry) => entry.model)]) {
			const found = findModel(candidate)
			if (found) return found
		}
		return null
	}
	return findModel(definition?.model)
}

async function resolveModelParents(
	model: MinecraftModel,
	namespace: string,
	assetRoot: string | undefined,
	seen = new Set<string>(),
): Promise<MinecraftModel> {
	const child = assetRoot ? qualifyModelTextures(model, namespace, assetRoot) : deepClone(model)
	if (!assetRoot || !model.parent || model.parent.startsWith('builtin/')) return child

	const parentId = parseResourceId(model.parent, namespace)
	if (parentId.namespace !== 'minecraft') return child
	const parentPath = parentId.path.includes('/') ? parentId.path : `item/${parentId.path}`
	const key = `${parentId.namespace}:${parentPath}`
	if (seen.has(key)) return child
	seen.add(key)

	const parent = await fetchModel(`${assetRoot}/${parentId.namespace}/models/${parentPath}.json`)
	if (!parent) return child
	return mergeModels(await resolveModelParents(parent, parentId.namespace, assetRoot, seen), child)
}

async function resolveItemSource(source: MinecraftItemSource): Promise<ResolvedItemSource> {
	const assetRoot = source.assetRoot?.replace(/\/$/, '')
	const itemId = parseResourceId(source.itemId ?? 'minecraft:air')
	if (!source.model && !source.modelUrl && source.textureUrl) {
		return {
			fallbackTexture: source.textureUrl,
			model: { parent: 'builtin/generated', textures: { layer0: source.textureUrl } },
		}
	}

	let model = source.model ?? (source.modelUrl ? await fetchModel(source.modelUrl) : null)
	let modelNamespace = itemId.namespace

	if (!model && assetRoot && source.itemId && itemId.namespace === 'minecraft') {
		const definition = await fetchItemDefinition(`${assetRoot}/${itemId.namespace}/items/${itemId.path}.json`)
		const modelIdValue = itemDefinitionModel(definition)
		if (modelIdValue) {
			const modelId = parseResourceId(modelIdValue, itemId.namespace)
			modelNamespace = modelId.namespace
			model = await fetchModel(`${assetRoot}/${modelId.namespace}/models/${modelId.path}.json`)
		} else if (!definition) {
			model = await fetchModel(`${assetRoot}/${itemId.namespace}/models/block/${itemId.path}.json`)
				?? await fetchModel(`${assetRoot}/${itemId.namespace}/models/item/${itemId.path}.json`)
		}
	}

	if (model) {
		return {
			fallbackTexture: source.textureUrl ?? null,
			model: await resolveModelParents(model, modelNamespace, assetRoot),
		}
	}

	const fallbackTexture = source.textureUrl ?? null
	return {
		fallbackTexture,
		model: { parent: 'builtin/generated', textures: fallbackTexture ? { layer0: fallbackTexture } : {} },
	}
}

function resolveModelTexture(textureRef: string | undefined, textures: Record<string, string>, fallback: string | null) {
	if (fallback) return fallback
	return resolveTextureReference(textureRef, textures)
}

function createFaceMaterial(texture: THREE.Texture, tintColor: THREE.Color, shade: boolean, lightEmission: number) {
	const options = {
		map: texture,
		color: tintColor,
		transparent: true,
		alphaTest: 0.05,
		side: THREE.DoubleSide,
		toneMapped: false,
	}
	return shade
		? new THREE.MeshStandardMaterial({
			...options,
			roughness: 1,
			metalness: 0,
			emissive: tintColor.clone(),
			emissiveMap: texture,
			emissiveIntensity: lightEmission / 15,
		})
		: new THREE.MeshBasicMaterial(options)
}

export class MinecraftModelObject {
	readonly group = new THREE.Group()
	private readonly textureRegistry: TextureRegistry
	private readonly tintPalette = new Map<number, RgbColor>()
	private readonly meshes: THREE.Mesh[] = []
	private resolvedModel: MinecraftModel | null = null
	private fallbackTexture: string | null = null

	constructor(frameSequence: number[] | null = null, defaultTint: RgbColor = { r: 255, g: 0, b: 0 }) {
		this.textureRegistry = new TextureRegistry(frameSequence)
		this.tintPalette.set(0, defaultTint)
	}

	get model() {
		return this.resolvedModel
	}

	async load(source: MinecraftItemSource) {
		this.disposeMeshes()
		const resolved = await resolveItemSource(source)
		this.resolvedModel = resolved.model
		this.fallbackTexture = resolved.fallbackTexture

		if (this.resolvedModel.elements?.length) {
			for (const element of this.resolvedModel.elements) {
				await this.buildElement(element, this.resolvedModel.textures ?? {})
			}
		} else {
			await this.buildGeneratedItem(this.resolvedModel.textures ?? {})
		}
		if (!this.meshes.length) throw new Error('Minecraft model did not produce renderable geometry.')
		return this.resolvedModel
	}

	update(deltaMs: number, frameDelayMs: number) {
		this.textureRegistry.update(deltaMs, frameDelayMs)
	}

	isAnimated() {
		return this.textureRegistry.isAnimated()
	}

	setTint(index: number, colorValue: RgbColor) {
		this.tintPalette.set(index, parseColorValue(colorValue))
		for (const mesh of this.meshes) {
			const tintIndex = mesh.userData.tintIndex as number
			const nextColor = tintIndex > FACE_TINT_DEFAULT
				? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
				: new THREE.Color(1, 1, 1)
			const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
			for (const material of materials) {
				if (hasMaterialColor(material)) material.color.copy(nextColor)
				if (hasMaterialEmissive(material)) material.emissive.copy(nextColor)
			}
		}
	}

	dispose() {
		this.disposeMeshes()
		this.textureRegistry.dispose()
	}

	private disposeMeshes() {
		for (const mesh of this.meshes) {
			mesh.geometry.dispose()
			const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
			for (const material of materials) material.dispose()
			this.group.remove(mesh)
		}
		this.meshes.length = 0
	}

	private addMesh(mesh: THREE.Mesh, metadata: Record<string, unknown>) {
		mesh.userData = metadata
		this.meshes.push(mesh)
		this.group.add(mesh)
	}

	private async buildElement(element: MinecraftElement, textures: Record<string, string>) {
		const fromVector = normalizeVector3(element.from)
		const toVector = normalizeVector3(element.to)
		const { from, to } = normalizeMinMax(fromVector, toVector)
		const collapsedAxis = getCollapsedAxis(from, to)
		const shade = element.shade !== false
		const lightEmission = clamp(Number(element.light_emission) || 0, 0, 15)

		for (const faceName of FACE_ORDER) {
			const face = element.faces?.[faceName]
			if (!face) continue
			const vertices = getFaceVertices(faceName, from, to, collapsedAxis)
			const rotatedVertices = vertices.map((vertex) => applyRotationSpec(vertex, element.rotation))
			if (!quadHasArea(rotatedVertices)) continue

			const textureSource = resolveModelTexture(face.texture, textures, this.fallbackTexture)
			const texture = await this.textureRegistry.get(textureSource)
			const uvRect = Array.isArray(face.uv) ? face.uv : inferDefaultUv(faceName, from, to)
			const uvCorners = getUvCorners(faceName, uvRect, face.rotation || 0)
			const worldVertices = rotatedVertices.map(modelSpaceToWorld)
			const geometry = new THREE.BufferGeometry()
			geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(worldVertices.flatMap((vertex) => [vertex.x, vertex.y, vertex.z])), 3))
			geometry.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(uvCorners.flatMap((uv) => [uv.x, uv.y])), 2))
			geometry.setIndex([0, 1, 2, 0, 2, 3])
			geometry.computeVertexNormals()

			const tintIndex = Number.isInteger(face.tintindex) ? face.tintindex! : FACE_TINT_DEFAULT
			const tintColor = tintIndex > FACE_TINT_DEFAULT
				? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
				: new THREE.Color(1, 1, 1)
			this.addMesh(
				new THREE.Mesh(geometry, createFaceMaterial(texture, tintColor, shade, lightEmission)),
				{ tintIndex, faceName, shade, lightEmission },
			)
		}
	}

	private async buildGeneratedItem(textures: Record<string, string>) {
		const layers = Object.entries(textures)
			.filter(([key]) => /^layer\d+$/.test(key))
			.sort(([left], [right]) => Number(left.slice(5)) - Number(right.slice(5)))
		if (!layers.length && this.fallbackTexture) layers.push(['layer0', this.fallbackTexture])

		for (let layerIndex = 0; layerIndex < layers.length; layerIndex += 1) {
			const [, textureRef] = layers[layerIndex]!
			const source = resolveModelTexture(textureRef, textures, this.fallbackTexture)
			const handle = await this.textureRegistry.getHandle(source)
			this.addGeneratedLayer(handle, layerIndex)
		}
	}

	private addGeneratedLayer(handle: ManagedTexture, layerIndex: number) {
		const width = handle.canvas.width
		const height = handle.canvas.height
		const alpha = handle.context.getImageData(0, 0, width, height).data
		const positions: number[] = []
		const uvs: number[] = []
		const indices: number[] = []
		const depth = 1 / 32 + layerIndex / 1024
		const opaque = (x: number, y: number) => x >= 0 && y >= 0 && x < width && y < height && alpha[(y * width + x) * 4 + 3]! > 0
		const addQuad = (vertices: number[][], textureUvs: number[][]) => {
			const start = positions.length / 3
			for (const vertex of vertices) positions.push(vertex[0]!, vertex[1]!, vertex[2]!)
			for (const uv of textureUvs) uvs.push(uv[0]!, uv[1]!)
			indices.push(start, start + 1, start + 2, start, start + 2, start + 3)
		}

		addQuad(
			[[-0.5, 0.5, depth], [0.5, 0.5, depth], [0.5, -0.5, depth], [-0.5, -0.5, depth]],
			[[0, 0], [1, 0], [1, 1], [0, 1]],
		)
		addQuad(
			[[0.5, 0.5, -depth], [-0.5, 0.5, -depth], [-0.5, -0.5, -depth], [0.5, -0.5, -depth]],
			[[1, 0], [0, 0], [0, 1], [1, 1]],
		)

		for (let y = 0; y < height; y += 1) {
			for (let x = 0; x < width; x += 1) {
				if (!opaque(x, y)) continue
				const left = x / width - 0.5
				const right = (x + 1) / width - 0.5
				const top = 0.5 - y / height
				const bottom = 0.5 - (y + 1) / height
				const pixelUv = Array.from({ length: 4 }, () => [(x + 0.5) / width, (y + 0.5) / height])
				if (!opaque(x - 1, y)) addQuad([[left, top, -depth], [left, top, depth], [left, bottom, depth], [left, bottom, -depth]], pixelUv)
				if (!opaque(x + 1, y)) addQuad([[right, top, depth], [right, top, -depth], [right, bottom, -depth], [right, bottom, depth]], pixelUv)
				if (!opaque(x, y - 1)) addQuad([[left, top, depth], [right, top, depth], [right, top, -depth], [left, top, -depth]], pixelUv)
				if (!opaque(x, y + 1)) addQuad([[left, bottom, -depth], [right, bottom, -depth], [right, bottom, depth], [left, bottom, depth]], pixelUv)
			}
		}

		const geometry = new THREE.BufferGeometry()
		geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(positions), 3))
		geometry.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(uvs), 2))
		geometry.setIndex(indices)
		geometry.computeVertexNormals()
		this.addMesh(
			new THREE.Mesh(geometry, createFaceMaterial(handle.texture, new THREE.Color(1, 1, 1), true, 0)),
			{ tintIndex: FACE_TINT_DEFAULT, generatedLayer: layerIndex },
		)
	}
}

export class MinecraftModelRenderer {
	private readonly clock = new THREE.Clock()
	private readonly scene = new THREE.Scene()
	private readonly camera = new THREE.PerspectiveCamera(34, 1, 0.01, 100)
	private readonly renderer: THREE.WebGLRenderer
	private readonly displayRoot = new THREE.Group()
	private readonly spinRoot = new THREE.Group()
	private readonly modelRoot = new THREE.Group()
	private readonly modelObject: MinecraftModelObject
	private readonly assetRoot: string | undefined
	private readonly fallbackTextureSource: string | undefined
	private animationFrame: number | null = null
	private currentDisplayMode = 'gui'
	private currentResolvedModel: MinecraftModel | null = null
	private destroyed = false
	private autoRotate: boolean
	private frameDelayMs: number
	private rotationSpeed: number
	private dyeable: boolean
	private view: PreviewView
	private enableDrag: boolean
	private dragging = false
	private pointerX = 0
	private pointerY = 0
	private contextLost = false
	private readonly tintPhase = Math.random() * 360

	constructor(private readonly container: HTMLElement, options: MinecraftModelRendererOptions) {
		this.autoRotate = Boolean(options.autoRotate)
		this.frameDelayMs = Math.max(TICK_MS, options.frameDelayMs ?? TICK_MS)
		this.rotationSpeed = options.rotationSpeed ?? 0.85
		this.dyeable = Boolean(options.dyeable)
		this.enableDrag = Boolean(options.enableDrag)
		this.view = options.view ?? 'basic3d'
		this.assetRoot = options.assetRoot
		this.fallbackTextureSource = options.textureSource
		if (this.view === 'cosmetic') this.spinRoot.rotation.y = Math.PI
		this.modelObject = new MinecraftModelObject(
			options.frameSequence ?? null,
			parseColorValue(options.defaultTint, { r: 255, g: 0, b: 0 }),
		)

		if (options.background) {
			this.scene.background = new THREE.Color(options.background)
		}

		this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: !options.background })
		this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
		this.renderer.outputColorSpace = THREE.SRGBColorSpace
		this.renderer.domElement.classList.add(options.canvasClassName ?? 'shopModelCanvas')
		this.renderer.domElement.addEventListener('webglcontextlost', this.handleContextLost)
		this.renderer.domElement.addEventListener('webglcontextrestored', this.handleContextRestored)
		if (this.enableDrag) {
			this.renderer.domElement.style.touchAction = 'none'
			this.renderer.domElement.style.cursor = 'grab'
			this.renderer.domElement.addEventListener('pointerdown', this.handlePointerDown)
			this.renderer.domElement.addEventListener('pointermove', this.handlePointerMove)
			this.renderer.domElement.addEventListener('pointerup', this.handlePointerUp)
			this.renderer.domElement.addEventListener('pointercancel', this.handlePointerUp)
		}
		this.container.appendChild(this.renderer.domElement)

		this.scene.add(new THREE.AmbientLight(0xffffff, 1.4))

		const keyLight = new THREE.DirectionalLight(0xffffff, 1.25)
		keyLight.position.set(2.5, 3.5, 2.25)
		this.scene.add(keyLight)

		const fillLight = new THREE.DirectionalLight(0xffffff, 0.55)
		fillLight.position.set(-1.75, 1.8, -2.5)
		this.scene.add(fillLight)

		this.modelRoot.add(this.modelObject.group)
		this.spinRoot.add(this.modelRoot)
		this.displayRoot.add(this.spinRoot)
		this.scene.add(this.displayRoot)

		this.handleResize = this.handleResize.bind(this)
		this.animate = this.animate.bind(this)
		window.addEventListener('resize', this.handleResize)
		this.handleResize()
	}

	destroy() {
		if (this.destroyed) return
		this.destroyed = true
		this.contextLost = true
		if (this.animationFrame !== null) {
			cancelAnimationFrame(this.animationFrame)
			this.animationFrame = null
		}
		window.removeEventListener('resize', this.handleResize)
		this.renderer.domElement.removeEventListener('webglcontextlost', this.handleContextLost)
		this.renderer.domElement.removeEventListener('webglcontextrestored', this.handleContextRestored)
		this.renderer.domElement.removeEventListener('pointerdown', this.handlePointerDown)
		this.renderer.domElement.removeEventListener('pointermove', this.handlePointerMove)
		this.renderer.domElement.removeEventListener('pointerup', this.handlePointerUp)
		this.renderer.domElement.removeEventListener('pointercancel', this.handlePointerUp)
		this.modelObject.dispose()
		this.renderer.dispose()
		this.renderer.domElement.remove()
	}

	setAutoRotate(enabled: boolean) {
		this.autoRotate = enabled
		this.ensureAnimating()
	}

	async loadModel(model: MinecraftModel) {
		if (this.destroyed) return
		this.currentResolvedModel = await this.modelObject.load({
			assetRoot: this.assetRoot,
			model,
			textureUrl: this.fallbackTextureSource,
		})
		this.applyDisplayTransform(this.currentDisplayMode)
		this.fitCameraToModel()
		this.ensureAnimating()
	}

	async loadItem(source: MinecraftItemSource) {
		if (this.destroyed) return
		this.currentResolvedModel = await this.modelObject.load({
			assetRoot: source.assetRoot ?? this.assetRoot,
			itemId: source.itemId,
			model: source.model,
			modelUrl: source.modelUrl,
			textureUrl: source.textureUrl ?? this.fallbackTextureSource,
		})
		this.applyDisplayTransform(this.currentDisplayMode)
		this.fitCameraToModel()
		this.ensureAnimating()
	}

	applyDisplayTransform(mode = 'gui') {
		this.currentDisplayMode = mode
		const transform = this.view === 'cosmetic' ? {} : this.currentResolvedModel?.display?.[mode] ?? {}
		const rotation = normalizeVector3(transform.rotation, [0, 0, 0])
		const translation = normalizeVector3(transform.translation, [0, 0, 0]).multiplyScalar(1 / 16)
		const scale = normalizeVector3(transform.scale, [1, 1, 1])

		this.modelRoot.position.copy(translation)
		this.modelRoot.rotation.set(toRadians(rotation.x), toRadians(rotation.y), toRadians(rotation.z))
		this.modelRoot.scale.copy(scale)
	}

	private handleResize() {
		const width = Math.max(1, this.container.clientWidth || 1)
		const height = Math.max(1, this.container.clientHeight || 1)
		this.camera.aspect = width / height
		this.camera.updateProjectionMatrix()
		this.renderer.setSize(width, height, false)
		this.ensureAnimating()
	}

	private animate() {
		if (this.destroyed || this.contextLost || !this.renderer.domElement.isConnected) return

		const deltaMs = this.clock.getDelta() * 1000
		if (this.autoRotate) {
			this.spinRoot.rotation.y += (deltaMs / 1000) * this.rotationSpeed
		}
		if (this.dyeable) {
			this.modelObject.setTint(0, hsvToRgb(((performance.now() / 28) + this.tintPhase) % 360, 0.82, 1))
		}

		this.modelObject.update(deltaMs, this.frameDelayMs)
		try {
			this.renderer.render(this.scene, this.camera)
		} catch {
			this.contextLost = true
			return
		}
		this.animationFrame = this.autoRotate || this.dyeable || this.modelObject.isAnimated()
			? requestAnimationFrame(this.animate)
			: null
	}

	private ensureAnimating() {
		if (this.destroyed || this.contextLost || this.animationFrame !== null || !this.renderer.domElement.isConnected) return
		this.clock.start()
		this.animate()
	}

	private fitCameraToModel() {
		const box = new THREE.Box3().setFromObject(this.displayRoot)
		if (box.isEmpty()) return
		const size = Math.max(0.1, box.getSize(new THREE.Vector3()).length())
		const center = box.getCenter(new THREE.Vector3())
		const distance = Math.max(1.6, size * 0.9 + 1)
		const offset = this.view === 'cosmetic'
			? new THREE.Vector3(0, distance * 0.28, distance * 1.12)
			: new THREE.Vector3(0, 0, distance * 1.28)

		this.camera.position.copy(center.clone().add(offset))
		this.camera.lookAt(center)
		this.camera.near = Math.max(0.01, distance / 200)
		this.camera.far = Math.max(50, distance * 20)
		this.camera.updateProjectionMatrix()
	}

	private handlePointerDown = (event: PointerEvent) => {
		if (!this.enableDrag) return
		this.dragging = true
		this.pointerX = event.clientX
		this.pointerY = event.clientY
		this.renderer.domElement.setPointerCapture(event.pointerId)
		this.renderer.domElement.style.cursor = 'grabbing'
	}

	private handleContextLost = (event: Event) => {
		event.preventDefault()
		this.contextLost = true
		if (this.animationFrame !== null) cancelAnimationFrame(this.animationFrame)
		this.animationFrame = null
	}

	private handleContextRestored = () => {
		if (this.destroyed) return
		this.contextLost = false
		this.clock.start()
		this.animate()
	}

	private handlePointerMove = (event: PointerEvent) => {
		if (!this.dragging) return
		const deltaX = event.clientX - this.pointerX
		const deltaY = event.clientY - this.pointerY
		this.pointerX = event.clientX
		this.pointerY = event.clientY
		this.spinRoot.rotation.y += deltaX * 0.012
		this.spinRoot.rotation.x = clamp(this.spinRoot.rotation.x + deltaY * 0.008, -0.75, 0.75)
		this.ensureAnimating()
	}

	private handlePointerUp = (event: PointerEvent) => {
		if (!this.dragging) return
		this.dragging = false
		if (this.renderer.domElement.hasPointerCapture(event.pointerId)) this.renderer.domElement.releasePointerCapture(event.pointerId)
		this.renderer.domElement.style.cursor = 'grab'
	}
}
