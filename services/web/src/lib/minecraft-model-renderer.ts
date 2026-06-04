import * as THREE from 'three'

type FaceName = 'north' | 'east' | 'south' | 'west' | 'up' | 'down'
type PreviewView = 'basic3d' | 'cosmetic'

export interface TextureAnimationOptions {
	frameDelayMs: number
	frames: number[] | null
}

export interface MinecraftModelRendererOptions {
	autoRotate?: boolean
	background?: string | null
	defaultTint?: RgbColor
	dyeable?: boolean
	frameDelayMs?: number
	frameSequence?: number[] | null
	rotationSpeed?: number
	textureSource: string
	view?: PreviewView
}

interface RgbColor {
	r: number
	g: number
	b: number
}

interface MinecraftModel {
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

async function loadImageFromSource(url: string) {
	return await new Promise<HTMLImageElement>((resolve, reject) => {
		const image = new Image()
		image.crossOrigin = 'anonymous'
		image.onload = () => resolve(image)
		image.onerror = () => reject(new Error(`Could not load texture source: ${url}`))
		image.src = url
	})
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
	handle: ManagedTexture | null = null
	source: string
	frameSequence: number[] | null

	constructor(source: string, frameSequence: number[] | null) {
		this.source = source
		this.frameSequence = frameSequence
	}

	async get() {
		if (this.handle) return this.handle.texture

		const handle = new ManagedTexture(this.frameSequence)
		try {
			handle.setImage(await loadImageFromSource(this.source))
		} catch {
			handle.drawMissing()
		}
		this.handle = handle
		return handle.texture
	}

	update(deltaMs: number, frameDelayMs: number) {
		this.handle?.update(deltaMs, frameDelayMs)
	}

	dispose() {
		this.handle?.dispose()
		this.handle = null
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
	private readonly textureRegistry: TextureRegistry
	private readonly tintPalette = new Map<number, RgbColor>()
	private readonly faceMeshes: THREE.Mesh[] = []
	private animationFrame: number | null = null
	private currentDisplayMode = 'gui'
	private currentResolvedModel: MinecraftModel | null = null
	private destroyed = false
	private autoRotate: boolean
	private frameDelayMs: number
	private rotationSpeed: number
	private dyeable: boolean
	private view: PreviewView

	constructor(private readonly container: HTMLElement, options: MinecraftModelRendererOptions) {
		this.autoRotate = Boolean(options.autoRotate)
		this.frameDelayMs = Math.max(TICK_MS, options.frameDelayMs ?? TICK_MS)
		this.rotationSpeed = options.rotationSpeed ?? 0.85
		this.dyeable = Boolean(options.dyeable)
		this.view = options.view ?? 'basic3d'
		this.textureRegistry = new TextureRegistry(options.textureSource, options.frameSequence ?? null)
		this.tintPalette.set(0, parseColorValue(options.defaultTint, { r: 255, g: 0, b: 0 }))

		if (options.background) {
			this.scene.background = new THREE.Color(options.background)
		}

		this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: !options.background })
		this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
		this.renderer.outputColorSpace = THREE.SRGBColorSpace
		this.renderer.domElement.classList.add('shopModelCanvas')
		this.container.appendChild(this.renderer.domElement)

		this.scene.add(new THREE.AmbientLight(0xffffff, 1.4))

		const keyLight = new THREE.DirectionalLight(0xffffff, 1.25)
		keyLight.position.set(2.5, 3.5, 2.25)
		this.scene.add(keyLight)

		const fillLight = new THREE.DirectionalLight(0xffffff, 0.55)
		fillLight.position.set(-1.75, 1.8, -2.5)
		this.scene.add(fillLight)

		this.spinRoot.add(this.modelRoot)
		this.displayRoot.add(this.spinRoot)
		this.scene.add(this.displayRoot)

		this.handleResize = this.handleResize.bind(this)
		this.animate = this.animate.bind(this)
		window.addEventListener('resize', this.handleResize)
		this.handleResize()
		this.animate()
	}

	destroy() {
		this.destroyed = true
		if (this.animationFrame !== null) {
			cancelAnimationFrame(this.animationFrame)
		}
		window.removeEventListener('resize', this.handleResize)
		this.disposeModel()
		this.textureRegistry.dispose()
		this.renderer.dispose()
		this.renderer.domElement.remove()
	}

	setAutoRotate(enabled: boolean) {
		this.autoRotate = enabled
	}

	async loadModel(model: MinecraftModel) {
		if (this.destroyed) return
		this.currentResolvedModel = deepClone(model)
		this.disposeModel()
		this.applyDisplayTransform(this.currentDisplayMode)

		if (!Array.isArray(this.currentResolvedModel.elements) || this.currentResolvedModel.elements.length === 0) {
			throw new Error('This renderer needs explicit model elements.')
		}

		for (const element of this.currentResolvedModel.elements) {
			await this.buildElement(element, this.currentResolvedModel.textures ?? {})
			if (this.destroyed) return
		}

		this.fitCameraToModel()
	}

	applyDisplayTransform(mode = 'gui') {
		this.currentDisplayMode = mode
		const transform = this.currentResolvedModel?.display?.[mode] ?? {}
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
	}

	private animate() {
		if (this.destroyed) return

		const deltaMs = this.clock.getDelta() * 1000
		if (this.autoRotate) {
			this.spinRoot.rotation.y += (deltaMs / 1000) * this.rotationSpeed
		}
		if (this.dyeable) {
			this.setTint(0, hsvToRgb((performance.now() / 24) % 360, 1, 1))
		}

		this.textureRegistry.update(deltaMs, this.frameDelayMs)
		this.renderer.render(this.scene, this.camera)
		this.animationFrame = requestAnimationFrame(this.animate)
	}

	private disposeModel() {
		for (const mesh of this.faceMeshes) {
			mesh.geometry.dispose()
			if (Array.isArray(mesh.material)) {
				for (const material of mesh.material) material.dispose()
			} else {
				mesh.material.dispose()
			}
			this.modelRoot.remove(mesh)
		}
		this.faceMeshes.length = 0
	}

	private fitCameraToModel() {
		if (this.faceMeshes.length === 0) return

		const box = new THREE.Box3().setFromObject(this.displayRoot)
		const size = Math.max(0.1, box.getSize(new THREE.Vector3()).length())
		const center = box.getCenter(new THREE.Vector3())
		const distance = Math.max(1.6, size * 0.9 + 1)
		const offset = this.view === 'cosmetic'
			? new THREE.Vector3(distance * 0.24, distance * 0.32, distance * 1.22)
			: new THREE.Vector3(0, 0, distance * 1.38)

		this.camera.position.copy(center.clone().add(offset))
		this.camera.lookAt(center)
		this.camera.near = Math.max(0.01, distance / 200)
		this.camera.far = Math.max(50, distance * 20)
		this.camera.updateProjectionMatrix()
	}

	private setTint(index: number, colorValue: RgbColor) {
		this.tintPalette.set(index, parseColorValue(colorValue))
		for (const faceMesh of this.faceMeshes) {
			const tintIndex = faceMesh.userData.tintIndex as number
			const nextColor = tintIndex > FACE_TINT_DEFAULT
				? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
				: new THREE.Color(1, 1, 1)
			const materials = Array.isArray(faceMesh.material) ? faceMesh.material : [faceMesh.material]
			for (const material of materials) {
				if (hasMaterialColor(material)) material.color.copy(nextColor)
				if (hasMaterialEmissive(material)) material.emissive.copy(nextColor)
				material.needsUpdate = true
			}
		}
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

			resolveTextureReference(face.texture, textures)
			const texture = await this.textureRegistry.get()
			const uvRect = Array.isArray(face.uv) ? face.uv : inferDefaultUv(faceName, from, to)
			const uvCorners = getUvCorners(faceName, uvRect, face.rotation || 0)
			const geometry = new THREE.BufferGeometry()
			const worldVertices = rotatedVertices.map(modelSpaceToWorld)

			geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array([
				worldVertices[0]!.x, worldVertices[0]!.y, worldVertices[0]!.z,
				worldVertices[1]!.x, worldVertices[1]!.y, worldVertices[1]!.z,
				worldVertices[2]!.x, worldVertices[2]!.y, worldVertices[2]!.z,
				worldVertices[3]!.x, worldVertices[3]!.y, worldVertices[3]!.z,
			]), 3))
			geometry.setAttribute('uv', new THREE.BufferAttribute(new Float32Array([
				uvCorners[0]!.x, uvCorners[0]!.y,
				uvCorners[1]!.x, uvCorners[1]!.y,
				uvCorners[2]!.x, uvCorners[2]!.y,
				uvCorners[3]!.x, uvCorners[3]!.y,
			]), 2))
			geometry.setIndex([0, 1, 2, 0, 2, 3])
			geometry.computeVertexNormals()

			const tintIndex = Number.isInteger(face.tintindex) ? face.tintindex! : FACE_TINT_DEFAULT
			const tintColor = tintIndex > FACE_TINT_DEFAULT
				? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
				: new THREE.Color(1, 1, 1)
			const materialOptions = {
				map: texture,
				color: tintColor,
				transparent: true,
				alphaTest: 0.05,
				side: THREE.DoubleSide,
				toneMapped: false,
			}
			const material = shade
				? new THREE.MeshStandardMaterial({
					...materialOptions,
					roughness: 1,
					metalness: 0,
					emissive: tintColor.clone(),
					emissiveMap: texture,
					emissiveIntensity: lightEmission / 15,
				})
				: new THREE.MeshBasicMaterial(materialOptions)
			const mesh = new THREE.Mesh(geometry, material)
			mesh.userData = { tintIndex, faceName, shade, lightEmission }
			this.faceMeshes.push(mesh)
			this.modelRoot.add(mesh)
		}
	}
}
