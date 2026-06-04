import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const FACE_ORDER = ['north', 'east', 'south', 'west', 'up', 'down'];
const FACE_AXIS = {
  north: 'z',
  south: 'z',
  east: 'x',
  west: 'x',
  up: 'y',
  down: 'y',
};
const FACE_SIGN = {
  north: -1,
  south: 1,
  east: 1,
  west: -1,
  up: 1,
  down: -1,
};
const FACE_UV_VERTEX_ORDER = {
  north: [1, 0, 3, 2],
  south: [1, 0, 3, 2],
  east: [1, 0, 3, 2],
  west: [1, 0, 3, 2],
  up: [3, 2, 1, 0],
  down: [3, 2, 1, 0],
};

const DEGENERATE_OFFSET = 0.001;
const DEFAULT_CAMERA_POSITION = new THREE.Vector3(1.8, 1.6, 2.6);
const DEFAULT_TARGET = new THREE.Vector3(0, 0.1, 0);
const FACE_TINT_DEFAULT = -1;
const MODEL_UV_UNITS = 16;
const TICK_MS = 50;
const MISSING_TEXTURE_SIZE = 16;
const IMAGE_EXTENSION_RE = /\.(png|apng|webp|jpg|jpeg|gif|bmp)$/i;
const SINGLE_TEXTURE_KEY = '__single_texture__';

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function toRadians(deg = 0) {
  return THREE.MathUtils.degToRad(Number(deg) || 0);
}

function normalizeVector3(values, fallback = [0, 0, 0]) {
  const source = Array.isArray(values) && values.length >= 3 ? values : fallback;
  return new THREE.Vector3(Number(source[0]) || 0, Number(source[1]) || 0, Number(source[2]) || 0);
}

function normalizeMinMax(from, to) {
  return {
    from: new THREE.Vector3(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
    to: new THREE.Vector3(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)),
  };
}

function deepClone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value));
}

function mergeModels(parentModel, childModel) {
  const merged = deepClone(parentModel) ?? {};
  const child = deepClone(childModel) ?? {};

  merged.parent = child.parent ?? merged.parent;
  merged.format_version = child.format_version ?? merged.format_version;
  merged.credit = child.credit ?? merged.credit;
  merged.ambientocclusion = child.ambientocclusion ?? merged.ambientocclusion;
  merged.gui_light = child.gui_light ?? merged.gui_light;
  merged.texture_size = child.texture_size ?? merged.texture_size;
  merged.textures = { ...(merged.textures ?? {}), ...(child.textures ?? {}) };
  merged.display = { ...(merged.display ?? {}), ...(child.display ?? {}) };
  merged.elements = child.elements ?? merged.elements ?? [];
  merged.groups = child.groups ?? merged.groups ?? [];
  return merged;
}

async function resolveModelInheritance(model, modelResolver, seen = new Set()) {
  if (!model?.parent || !modelResolver) {
    return deepClone(model);
  }

  const parentId = model.parent;
  if (seen.has(parentId)) {
    throw new Error(`Circular parent model reference: ${parentId}`);
  }

  seen.add(parentId);
  const parentModel = await modelResolver(parentId);
  if (!parentModel) {
    throw new Error(`Could not resolve parent model: ${parentId}`);
  }

  const resolvedParent = await resolveModelInheritance(parentModel, modelResolver, seen);
  return mergeModels(resolvedParent, model);
}

function resolveTextureReference(textureRef, textures = {}) {
  if (!textureRef || typeof textureRef !== 'string') {
    return null;
  }

  let current = textureRef;
  const visited = new Set();

  while (current.startsWith('#')) {
    const key = current.slice(1);
    if (visited.has(key)) {
      throw new Error(`Circular texture reference: ${textureRef}`);
    }
    visited.add(key);
    current = textures[key];
    if (!current) {
      throw new Error(`Missing texture reference for ${textureRef}`);
    }
  }

  return current;
}

function inferDefaultUv(faceName, from, to) {
  switch (faceName) {
    case 'down':
      return [from.x, 16 - to.z, to.x, 16 - from.z];
    case 'up':
      return [from.x, from.z, to.x, to.z];
    case 'north':
      return [16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y];
    case 'south':
      return [from.x, 16 - to.y, to.x, 16 - from.y];
    case 'west':
      return [from.z, 16 - to.y, to.z, 16 - from.y];
    case 'east':
      return [16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y];
    default:
      return [0, 0, 16, 16];
  }
}

function getFaceVertices(faceName, from, to, collapsedAxis = null) {
  const offset = collapsedAxis === FACE_AXIS[faceName] ? DEGENERATE_OFFSET * FACE_SIGN[faceName] : 0;

  switch (faceName) {
    case 'north':
      return [
        new THREE.Vector3(from.x, to.y, from.z + offset),
        new THREE.Vector3(to.x, to.y, from.z + offset),
        new THREE.Vector3(to.x, from.y, from.z + offset),
        new THREE.Vector3(from.x, from.y, from.z + offset),
      ];
    case 'south':
      return [
        new THREE.Vector3(to.x, to.y, to.z + offset),
        new THREE.Vector3(from.x, to.y, to.z + offset),
        new THREE.Vector3(from.x, from.y, to.z + offset),
        new THREE.Vector3(to.x, from.y, to.z + offset),
      ];
    case 'east':
      return [
        new THREE.Vector3(to.x + offset, to.y, from.z),
        new THREE.Vector3(to.x + offset, to.y, to.z),
        new THREE.Vector3(to.x + offset, from.y, to.z),
        new THREE.Vector3(to.x + offset, from.y, from.z),
      ];
    case 'west':
      return [
        new THREE.Vector3(from.x + offset, to.y, to.z),
        new THREE.Vector3(from.x + offset, to.y, from.z),
        new THREE.Vector3(from.x + offset, from.y, from.z),
        new THREE.Vector3(from.x + offset, from.y, to.z),
      ];
    case 'up':
      return [
        new THREE.Vector3(from.x, to.y + offset, to.z),
        new THREE.Vector3(to.x, to.y + offset, to.z),
        new THREE.Vector3(to.x, to.y + offset, from.z),
        new THREE.Vector3(from.x, to.y + offset, from.z),
      ];
    case 'down':
      return [
        new THREE.Vector3(from.x, from.y + offset, from.z),
        new THREE.Vector3(to.x, from.y + offset, from.z),
        new THREE.Vector3(to.x, from.y + offset, to.z),
        new THREE.Vector3(from.x, from.y + offset, to.z),
      ];
    default:
      return [];
  }
}

function getCollapsedAxis(from, to) {
  if (Math.abs(to.x - from.x) < 1e-7) return 'x';
  if (Math.abs(to.y - from.y) < 1e-7) return 'y';
  if (Math.abs(to.z - from.z) < 1e-7) return 'z';
  return null;
}

function quadHasArea(vertices) {
  if (vertices.length !== 4) return false;
  const edgeA = new THREE.Vector3().subVectors(vertices[1], vertices[0]);
  const edgeB = new THREE.Vector3().subVectors(vertices[3], vertices[0]);
  return new THREE.Vector3().crossVectors(edgeA, edgeB).lengthSq() > 1e-12;
}

function rotateQuadUvs(uvs, steps) {
  const normalizedSteps = ((steps % 4) + 4) % 4;
  let current = [...uvs];
  for (let i = 0; i < normalizedSteps; i += 1) {
    current = [current[3], current[0], current[1], current[2]];
  }
  return current;
}

function getRotationSteps(rotationDegrees = 0) {
  const snapped = Math.round((Number(rotationDegrees) || 0) / 90);
  return ((snapped % 4) + 4) % 4;
}

function getUvCorners(faceName, uvRect, rotationDegrees = 0) {
  const [u1, v1, u2, v2] = uvRect.map((value) => Number(value) || 0);

  const faceSpaceCorners = [
    new THREE.Vector2(u1 / MODEL_UV_UNITS, v1 / MODEL_UV_UNITS),
    new THREE.Vector2(u2 / MODEL_UV_UNITS, v1 / MODEL_UV_UNITS),
    new THREE.Vector2(u2 / MODEL_UV_UNITS, v2 / MODEL_UV_UNITS),
    new THREE.Vector2(u1 / MODEL_UV_UNITS, v2 / MODEL_UV_UNITS),
  ];

  const rotated = rotateQuadUvs(faceSpaceCorners, getRotationSteps(rotationDegrees));
  const vertexOrder = FACE_UV_VERTEX_ORDER[faceName] ?? [0, 1, 2, 3];
  return vertexOrder.map((index) => rotated[index]);
}

function getRescaleMultiplier(angleDegrees) {
  const radians = Math.abs(toRadians(angleDegrees));
  const cosine = Math.abs(Math.cos(radians));
  if (cosine < 1e-6) return 1;
  return 1 / cosine;
}

function applySingleAxisRotation(vertex, origin, axis, angle, rescale = false) {
  const moved = vertex.clone().sub(origin);

  if (rescale && angle) {
    const factor = getRescaleMultiplier(angle);
    if (axis === 'x') moved.multiply(new THREE.Vector3(1, factor, factor));
    if (axis === 'y') moved.multiply(new THREE.Vector3(factor, 1, factor));
    if (axis === 'z') moved.multiply(new THREE.Vector3(factor, factor, 1));
  }

  moved.applyAxisAngle(
    axis === 'x' ? new THREE.Vector3(1, 0, 0) : axis === 'y' ? new THREE.Vector3(0, 1, 0) : new THREE.Vector3(0, 0, 1),
    toRadians(angle),
  );

  return moved.add(origin);
}

function applyRotationSpec(vertex, rotationSpec) {
  if (!rotationSpec) return vertex;

  const origin = normalizeVector3(rotationSpec.origin);
  let result = vertex.clone();

  if (rotationSpec.axis && Number(rotationSpec.angle || 0) !== 0) {
    return applySingleAxisRotation(result, origin, rotationSpec.axis, Number(rotationSpec.angle) || 0, Boolean(rotationSpec.rescale));
  }

  for (const axis of ['x', 'y', 'z']) {
    const angle = Number(rotationSpec[axis] || 0);
    if (!angle) continue;
    result = applySingleAxisRotation(result, origin, axis, angle, Boolean(rotationSpec.rescale));
  }

  return result;
}

function modelSpaceToWorld(vertex) {
  return new THREE.Vector3((vertex.x - 8) / 16, (vertex.y - 8) / 16, (vertex.z - 8) / 16);
}

function hsvToRgb(h, s = 1, v = 1) {
  const hue = ((h % 360) + 360) % 360;
  const c = v * s;
  const x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
  const m = v - c;

  let r = 0;
  let g = 0;
  let b = 0;

  if (hue < 60) {
    r = c; g = x; b = 0;
  } else if (hue < 120) {
    r = x; g = c; b = 0;
  } else if (hue < 180) {
    r = 0; g = c; b = x;
  } else if (hue < 240) {
    r = 0; g = x; b = c;
  } else if (hue < 300) {
    r = x; g = 0; b = c;
  } else {
    r = c; g = 0; b = x;
  }

  return {
    r: Math.round((r + m) * 255),
    g: Math.round((g + m) * 255),
    b: Math.round((b + m) * 255),
  };
}

function rgbToCss({ r, g, b }) {
  return `rgb(${r}, ${g}, ${b})`;
}

function rgbToThreeColor(rgb) {
  return new THREE.Color(rgb.r / 255, rgb.g / 255, rgb.b / 255);
}

function parseColorValue(value, fallback = { r: 255, g: 255, b: 255 }) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return {
      r: clamp(Math.round(Number(value.r) || fallback.r), 0, 255),
      g: clamp(Math.round(Number(value.g) || fallback.g), 0, 255),
      b: clamp(Math.round(Number(value.b) || fallback.b), 0, 255),
    };
  }

  if (Array.isArray(value)) {
    if (value.length >= 3 && value.every((item) => typeof item === 'number' && item >= 0 && item <= 1)) {
      const [r, g, b] = value;
      return { r: Math.round(r * 255), g: Math.round(g * 255), b: Math.round(b * 255) };
    }

    if (value.length >= 3) {
      return {
        r: clamp(Math.round(Number(value[0]) || fallback.r), 0, 255),
        g: clamp(Math.round(Number(value[1]) || fallback.g), 0, 255),
        b: clamp(Math.round(Number(value[2]) || fallback.b), 0, 255),
      };
    }
  }

  if (typeof value === 'number') {
    if (value < 0) return fallback;
    return {
      r: (value >> 16) & 255,
      g: (value >> 8) & 255,
      b: value & 255,
    };
  }

  return fallback;
}

function isLikelyLoadableTextureSource(value) {
  if (typeof value !== 'string') return true;
  return (
    value.startsWith('./')
    || value.startsWith('../')
    || value.startsWith('/')
    || value.startsWith('blob:')
    || value.startsWith('data:')
    || /^https?:/i.test(value)
    || IMAGE_EXTENSION_RE.test(value)
  );
}

function createCanvas(width, height) {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

function configureThreeTexture(texture) {
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.anisotropy = 1;
  texture.wrapS = THREE.ClampToEdgeWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  texture.flipY = false;
  texture.needsUpdate = true;
  return texture;
}

function drawMissingTexture(ctx, width, height) {
  ctx.clearRect(0, 0, width, height);
  const block = Math.max(2, Math.floor(width / 4));
  for (let y = 0; y < height; y += block) {
    for (let x = 0; x < width; x += block) {
      const isDark = ((x / block) + (y / block)) % 2 === 0;
      ctx.fillStyle = isDark ? '#111111' : '#ff00ff';
      ctx.fillRect(x, y, block, block);
    }
  }
}

async function loadImageFromSource(source) {
  let url = null;
  let revokeUrl = null;

  if (typeof source === 'string') {
    url = source;
  } else if (source instanceof Blob || source instanceof File) {
    revokeUrl = URL.createObjectURL(source);
    url = revokeUrl;
  } else if (source?.url) {
    url = source.url;
  }

  if (!url) {
    throw new Error('Unsupported texture source');
  }

  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      if (revokeUrl) URL.revokeObjectURL(revokeUrl);
      resolve(img);
    };
    img.onerror = () => {
      if (revokeUrl) URL.revokeObjectURL(revokeUrl);
      reject(new Error(`Could not load texture source: ${url}`));
    };
    img.src = url;
  });
}

class ManagedTexture {
  constructor(label = 'texture') {
    this.label = label;
    this.canvas = createCanvas(MISSING_TEXTURE_SIZE, MISSING_TEXTURE_SIZE);
    this.context = this.canvas.getContext('2d');
    this.texture = configureThreeTexture(new THREE.CanvasTexture(this.canvas));
    this.sourceImage = null;
    this.frameWidth = MISSING_TEXTURE_SIZE;
    this.frameHeight = MISSING_TEXTURE_SIZE;
    this.frameCount = 1;
    this.currentFrame = 0;
    this.frameElapsedMs = 0;
    this.isMissing = true;
    this.drawMissing();
  }

  drawMissing() {
    this.isMissing = true;
    this.frameWidth = this.canvas.width;
    this.frameHeight = this.canvas.height;
    this.frameCount = 1;
    this.currentFrame = 0;
    drawMissingTexture(this.context, this.canvas.width, this.canvas.height);
    this.texture.needsUpdate = true;
  }

  setImage(image) {
    const sourceWidth = image.naturalWidth || image.width || MISSING_TEXTURE_SIZE;
    const sourceHeight = image.naturalHeight || image.height || sourceWidth;
    const isAnimatedVerticalStrip = sourceHeight > sourceWidth;

    this.sourceImage = image;
    this.frameWidth = sourceWidth;
    this.frameHeight = isAnimatedVerticalStrip ? sourceWidth : sourceHeight;
    this.frameCount = isAnimatedVerticalStrip ? Math.max(1, Math.floor(sourceHeight / sourceWidth)) : 1;
    this.currentFrame = 0;
    this.frameElapsedMs = 0;
    this.isMissing = false;

    if (this.canvas.width !== this.frameWidth || this.canvas.height !== this.frameHeight) {
      this.canvas.width = this.frameWidth;
      this.canvas.height = this.frameHeight;
      this.context = this.canvas.getContext('2d');
    }

    this.drawFrame(0);
  }

  drawFrame(frameIndex) {
    if (!this.sourceImage) {
      this.drawMissing();
      return;
    }

    const frame = this.frameCount > 0 ? ((frameIndex % this.frameCount) + this.frameCount) % this.frameCount : 0;
    const sourceY = frame * this.frameHeight;

    this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
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
    );

    this.currentFrame = frame;
    this.texture.needsUpdate = true;
  }

  update(deltaMs, frameDelayMs) {
    if (this.frameCount <= 1) return;
    const safeDelay = Math.max(TICK_MS, Number(frameDelayMs) || TICK_MS);
    this.frameElapsedMs += deltaMs;

    while (this.frameElapsedMs >= safeDelay) {
      this.frameElapsedMs -= safeDelay;
      this.drawFrame(this.currentFrame + 1);
    }
  }

  dispose() {
    this.texture.dispose();
    this.canvas.width = 0;
    this.canvas.height = 0;
    this.sourceImage = null;
  }
}

class TextureRegistry {
  constructor(defaultOverrides = {}) {
    this.cache = new Map();
    this.handles = new Map();
    this.sources = new Map();
    for (const [key, value] of Object.entries(defaultOverrides)) {
      this.register(key, value);
    }
  }

  register(key, source) {
    if (!key || !source) return;
    this.sources.set(key, source);

    const oldHandle = this.handles.get(key);
    if (oldHandle) oldHandle.dispose();

    this.cache.delete(key);
    this.handles.delete(key);
  }

  async get(key) {
    return (await this.getHandle(key)).texture;
  }

  async getHandle(key) {
    if (this.cache.has(key)) {
      return this.cache.get(key);
    }

    const hasExplicitSource = this.sources.has(key);
    const source = hasExplicitSource ? this.sources.get(key) : key;

    const promise = this.loadManagedTexture(key, source)
      .then((handle) => {
        this.handles.set(key, handle);
        return handle;
      })
      .catch((error) => {
        const handle = new ManagedTexture(key);
        this.handles.set(key, handle);
        console.warn(error);
        return handle;
      });

    this.cache.set(key, promise);
    return promise;
  }

  async loadManagedTexture(key, source) {
    const handle = new ManagedTexture(key);
    if (!source || !isLikelyLoadableTextureSource(source)) return handle;
    const image = await loadImageFromSource(source);
    handle.setImage(image);
    return handle;
  }

  update(deltaMs, frameDelayMs) {
    for (const handle of this.handles.values()) {
      handle.update(deltaMs, frameDelayMs);
    }
  }

  dispose() {
    for (const handle of this.handles.values()) {
      handle.dispose();
    }
    this.cache.clear();
    this.handles.clear();
    this.sources.clear();
  }
}

export class MinecraftModelRenderer {
  constructor(container, options = {}) {
    this.container = container;
    this.options = {
      background: '#0b1020',
      autoRotate: true,
      rotationSpeed: 0.65,
      defaultTint: { r: 255, g: 0, b: 0 },
      textureOverrides: {},
      defaultTextureSource: null,
      modelResolver: null,
      frameDelayMs: TICK_MS,
      onAfterRender: null,
      ...options,
    };

    this.clock = new THREE.Clock();
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(this.options.background);

    this.camera = new THREE.PerspectiveCamera(40, 1, 0.01, 100);
    this.camera.position.copy(DEFAULT_CAMERA_POSITION);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, preserveDrawingBuffer: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.domElement.classList.add('renderer-canvas');
    container.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.enablePan = false;
    this.controls.target.copy(DEFAULT_TARGET);
    this.controls.minDistance = 1.1;
    this.controls.maxDistance = 8;

    this.scene.add(new THREE.AmbientLight(0xffffff, 1.35));

    this.keyLight = new THREE.DirectionalLight(0xffffff, 1.25);
    this.keyLight.position.set(2.5, 3.5, 2.25);
    this.scene.add(this.keyLight);

    this.fillLight = new THREE.DirectionalLight(0xffffff, 0.55);
    this.fillLight.position.set(-1.75, 1.8, -2.5);
    this.scene.add(this.fillLight);

    this.displayRoot = new THREE.Group();
    this.spinRoot = new THREE.Group();
    this.modelRoot = new THREE.Group();
    this.spinRoot.add(this.modelRoot);
    this.displayRoot.add(this.spinRoot);
    this.scene.add(this.displayRoot);

    this.textureRegistry = new TextureRegistry(this.options.textureOverrides);
    if (this.options.defaultTextureSource) {
      this.textureRegistry.register(SINGLE_TEXTURE_KEY, this.options.defaultTextureSource);
    }
    this.tintPalette = new Map([[0, parseColorValue(this.options.defaultTint, { r: 255, g: 0, b: 0 })]]);
    this.currentModel = null;
    this.currentResolvedModel = null;
    this.currentDisplayMode = 'gui';
    this.autoRotate = Boolean(this.options.autoRotate);
    this.faceMeshes = [];
    this.pendingTexturePaths = [];
    this.animationFrame = null;

    this.handleResize = this.handleResize.bind(this);
    this.animate = this.animate.bind(this);

    window.addEventListener('resize', this.handleResize);
    this.handleResize();
    this.animate();
  }

  destroy() {
    cancelAnimationFrame(this.animationFrame);
    window.removeEventListener('resize', this.handleResize);
    this.controls.dispose();
    this.disposeModel();
    this.textureRegistry.dispose();
    this.renderer.dispose();
    this.container.innerHTML = '';
  }

  handleResize() {
    const width = this.container.clientWidth || 800;
    const height = this.container.clientHeight || 600;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  animate() {
    const deltaMs = this.clock.getDelta() * 1000;

    if (this.autoRotate) {
      this.spinRoot.rotation.y += (deltaMs / 1000) * this.options.rotationSpeed;
    }

    this.textureRegistry.update(deltaMs, this.options.frameDelayMs);
    this.controls.update();
    this.renderer.render(this.scene, this.camera);
    this.options.onAfterRender?.(this);
    this.animationFrame = requestAnimationFrame(this.animate);
  }

  setAutoRotate(enabled) {
    this.autoRotate = Boolean(enabled);
  }

  setRotationSpeed(value) {
    this.options.rotationSpeed = Number(value) || this.options.rotationSpeed;
  }

  setFrameDelayMs(value) {
    this.options.frameDelayMs = Math.max(TICK_MS, Number(value) || TICK_MS);
  }

  setTint(index, colorValue) {
    this.tintPalette.set(index, parseColorValue(colorValue));
    for (const faceMesh of this.faceMeshes) {
      const tintIndex = faceMesh.userData.tintIndex;
      const nextColor = tintIndex > FACE_TINT_DEFAULT
        ? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
        : new THREE.Color(1, 1, 1);
      faceMesh.material.color.copy(nextColor);
      if (faceMesh.material.emissive) {
        faceMesh.material.emissive.copy(nextColor);
      }
      faceMesh.material.needsUpdate = true;
    }
  }

  getTint(index = 0) {
    return this.tintPalette.get(index) ?? { r: 255, g: 255, b: 255 };
  }

  registerTexture(key, source) {
    this.textureRegistry.register(key, source);
  }

  setSingleTexture(source) {
    this.textureRegistry.register(SINGLE_TEXTURE_KEY, source);
  }

  getPendingTexturePaths() {
    return [...this.pendingTexturePaths];
  }

  async loadModel(model, options = {}) {
    this.currentModel = deepClone(model);

    if (options.textureOverrides) {
      for (const [key, value] of Object.entries(options.textureOverrides)) {
        this.registerTexture(key, value);
      }
    }

    if (options.modelResolver) {
      this.options.modelResolver = options.modelResolver;
    }

    const resolvedModel = await resolveModelInheritance(this.currentModel, this.options.modelResolver);
    this.currentResolvedModel = resolvedModel;
    this.pendingTexturePaths = [];

    this.disposeModel();
    this.applyDisplayTransform(this.currentDisplayMode, resolvedModel.display ?? {});

    if (!Array.isArray(resolvedModel.elements) || resolvedModel.elements.length === 0) {
      throw new Error('This renderer currently needs explicit model elements.');
    }

    for (const element of resolvedModel.elements) {
      await this.buildElement(element, resolvedModel.textures ?? {});
    }

    this.fitCameraToModel();
  }

  applyDisplayTransform(mode = 'gui', displayMap = this.currentResolvedModel?.display ?? {}) {
    this.currentDisplayMode = mode;
    const transform = displayMap?.[mode] ?? {};
    const rotation = normalizeVector3(transform.rotation, [0, 0, 0]);
    const translation = normalizeVector3(transform.translation, [0, 0, 0]).multiplyScalar(1 / 16);
    const scale = normalizeVector3(transform.scale, [1, 1, 1]);

    this.modelRoot.position.copy(translation);
    this.modelRoot.rotation.set(toRadians(rotation.x), toRadians(rotation.y), toRadians(rotation.z));
    this.modelRoot.scale.copy(scale);
  }

  disposeModel() {
    for (const mesh of this.faceMeshes) {
      mesh.geometry.dispose();
      mesh.material.dispose();
      this.modelRoot.remove(mesh);
    }
    this.faceMeshes = [];
  }

  fitCameraToModel() {
    if (this.faceMeshes.length === 0) return;

    const box = new THREE.Box3().setFromObject(this.displayRoot);
    const size = box.getSize(new THREE.Vector3()).length();
    const center = box.getCenter(new THREE.Vector3());

    this.controls.target.copy(center);
    const distance = Math.max(1.8, size * 0.9 + 1.15);
    this.camera.position.copy(center.clone().add(new THREE.Vector3(distance * 0.68, distance * 0.55, distance * 0.86)));
    this.camera.near = Math.max(0.01, distance / 200);
    this.camera.far = Math.max(50, distance * 20);
    this.camera.updateProjectionMatrix();
    this.controls.update();
  }

  async buildElement(element, textures) {
    const fromVector = normalizeVector3(element.from);
    const toVector = normalizeVector3(element.to);
    const { from, to } = normalizeMinMax(fromVector, toVector);
    const collapsedAxis = getCollapsedAxis(from, to);
    const shade = element.shade !== false;
    const lightEmission = clamp(Number(element.light_emission) || 0, 0, 15);

    for (const faceName of FACE_ORDER) {
      const face = element.faces?.[faceName];
      if (!face) continue;

      const vertices = getFaceVertices(faceName, from, to, collapsedAxis);
      const rotatedVertices = vertices.map((vertex) => applyRotationSpec(vertex, element.rotation));
      if (!quadHasArea(rotatedVertices)) {
        continue;
      }

      const textureId = resolveTextureReference(face.texture, textures) ?? SINGLE_TEXTURE_KEY;
      const texture = await this.textureRegistry.get(SINGLE_TEXTURE_KEY);
      const uvRect = Array.isArray(face.uv) ? face.uv : inferDefaultUv(faceName, from, to);
      const uvCorners = getUvCorners(faceName, uvRect, face.rotation || 0);

      const geometry = new THREE.BufferGeometry();
      const worldVertices = rotatedVertices.map(modelSpaceToWorld);
      const positions = new Float32Array([
        worldVertices[0].x, worldVertices[0].y, worldVertices[0].z,
        worldVertices[1].x, worldVertices[1].y, worldVertices[1].z,
        worldVertices[2].x, worldVertices[2].y, worldVertices[2].z,
        worldVertices[3].x, worldVertices[3].y, worldVertices[3].z,
      ]);
      const uvs = new Float32Array([
        uvCorners[0].x, uvCorners[0].y,
        uvCorners[1].x, uvCorners[1].y,
        uvCorners[2].x, uvCorners[2].y,
        uvCorners[3].x, uvCorners[3].y,
      ]);
      geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      geometry.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
      geometry.setIndex([0, 1, 2, 0, 2, 3]);
      geometry.computeVertexNormals();

      const tintIndex = Number.isInteger(face.tintindex) ? face.tintindex : FACE_TINT_DEFAULT;
      const tintColor = tintIndex > FACE_TINT_DEFAULT
        ? rgbToThreeColor(this.tintPalette.get(tintIndex) ?? this.tintPalette.get(0) ?? { r: 255, g: 255, b: 255 })
        : new THREE.Color(1, 1, 1);

      const materialOptions = {
        map: texture,
        color: tintColor,
        transparent: true,
        alphaTest: 0.05,
        side: THREE.FrontSide,
        toneMapped: false,
      };

      let material;
      if (shade) {
        material = new THREE.MeshStandardMaterial({
          ...materialOptions,
          roughness: 1,
          metalness: 0,
          emissive: tintColor.clone(),
          emissiveMap: texture,
          emissiveIntensity: lightEmission / 15,
        });
      } else {
        material = new THREE.MeshBasicMaterial(materialOptions);
      }

      const mesh = new THREE.Mesh(geometry, material);
      mesh.userData = {
        tintIndex,
        faceName,
        textureId,
        shade,
        lightEmission,
      };
      this.faceMeshes.push(mesh);
      this.modelRoot.add(mesh);
    }
  }
}

export function collectTexturePaths(model) {
  const textures = model?.textures ?? {};
  const paths = new Set();

  for (const element of model?.elements ?? []) {
    for (const faceName of FACE_ORDER) {
      const face = element.faces?.[faceName];
      if (!face?.texture) continue;
      const resolved = resolveTextureReference(face.texture, textures);
      if (resolved) paths.add(resolved);
    }
  }

  if (textures.particle && typeof textures.particle === 'string' && !textures.particle.startsWith('#')) {
    paths.add(textures.particle);
  }

  return [...paths];
}

export function createHueTint(hue) {
  return hsvToRgb(hue, 1, 1);
}

export function colorToCss(color) {
  return rgbToCss(color);
}
