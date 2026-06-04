import { MinecraftModelRenderer, createHueTint, colorToCss } from './lib/minecraft-model-renderer.js';

const DEFAULT_MODEL_URL = './assets/default-model.json';
const DEFAULT_TEXTURE_URL = './assets/amogus.png';

const viewerHost = document.querySelector('#viewer');
const modelFileInput = document.querySelector('#model-file');
const textureFileInput = document.querySelector('#texture-file');
const resetButton = document.querySelector('#reset-demo');
const displayModeSelect = document.querySelector('#display-mode');
const tintHueInput = document.querySelector('#tint-hue');
const tintSwatch = document.querySelector('#tint-swatch');
const tintReadout = document.querySelector('#tint-readout');
const frameDelayInput = document.querySelector('#frame-delay');
const frameDelayReadout = document.querySelector('#frame-delay-readout');
const autoRotateInput = document.querySelector('#auto-rotate');
const statusEl = document.querySelector('#status');
const exportPngButton = document.querySelector('#export-png');
const uploadHint = document.querySelector('#upload-hint');

const state = {
  defaultModel: null,
  model: null,
  renderer: null,
  tintHue: 0,
  frameDelayMs: 50,
  textureSource: DEFAULT_TEXTURE_URL,
};

state.renderer = new MinecraftModelRenderer(viewerHost, {
  autoRotate: true,
  defaultTint: { r: 255, g: 0, b: 0 },
  frameDelayMs: state.frameDelayMs,
  defaultTextureSource: DEFAULT_TEXTURE_URL,
});

init().catch((error) => {
  console.error(error);
  setStatus(error.message || 'Failed to initialize renderer.', true);
});

async function init() {
  state.defaultModel = await loadJson(DEFAULT_MODEL_URL);
  bindEvents();
  updateFrameDelayReadout();
  updateTextureHint();
  await loadModelIntoViewer(state.defaultModel);
  applyHueTint(0);
}

function bindEvents() {
  modelFileInput.addEventListener('change', async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      const json = JSON.parse(await file.text());
      await loadModelIntoViewer(json);
      setStatus(`Loaded model: ${file.name}`);
    } catch (error) {
      console.error(error);
      setStatus(`Could not parse model JSON: ${error.message}`, true);
    }
  });

  textureFileInput.addEventListener('change', async (event) => {
    const file = event.target.files?.[0];
    state.textureSource = file || DEFAULT_TEXTURE_URL;
    state.renderer.setSingleTexture(state.textureSource);
    updateTextureHint(file?.name ?? null);
    await rerender();
  });

  resetButton.addEventListener('click', async () => {
    tintHueInput.value = '0';
    frameDelayInput.value = '50';
    state.frameDelayMs = 50;
    state.textureSource = DEFAULT_TEXTURE_URL;
    autoRotateInput.checked = true;
    displayModeSelect.value = 'gui';
    modelFileInput.value = '';
    textureFileInput.value = '';
    state.renderer.setSingleTexture(DEFAULT_TEXTURE_URL);
    await loadModelIntoViewer(state.defaultModel);
    applyHueTint(0);
    state.renderer.setFrameDelayMs(state.frameDelayMs);
    state.renderer.setAutoRotate(true);
    updateFrameDelayReadout();
    updateTextureHint();
    setStatus('Reset to bundled demo model.');
  });

  displayModeSelect.addEventListener('change', () => {
    state.renderer.applyDisplayTransform(displayModeSelect.value);
  });

  tintHueInput.addEventListener('input', () => {
    applyHueTint(Number(tintHueInput.value));
  });

  frameDelayInput.addEventListener('input', () => {
    state.frameDelayMs = Number(frameDelayInput.value) || 50;
    state.renderer.setFrameDelayMs(state.frameDelayMs);
    updateFrameDelayReadout();
  });

  autoRotateInput.addEventListener('change', () => {
    state.renderer.setAutoRotate(autoRotateInput.checked);
  });

  exportPngButton.addEventListener('click', () => {
    const link = document.createElement('a');
    link.href = state.renderer.renderer.domElement.toDataURL('image/png');
    link.download = 'minecraft-model-render.png';
    link.click();
  });
}

async function loadModelIntoViewer(model) {
  state.model = model;
  state.renderer.setSingleTexture(state.textureSource);
  await rerender();
  state.renderer.applyDisplayTransform(displayModeSelect.value, state.renderer.currentResolvedModel?.display ?? model.display ?? {});
}

async function rerender() {
  if (!state.model) return;
  state.renderer.setSingleTexture(state.textureSource);

  try {
    await state.renderer.loadModel(state.model);
    state.renderer.setFrameDelayMs(state.frameDelayMs);
    applyHueTint(Number(tintHueInput.value));

    const faceCount = state.renderer.faceMeshes.length;
    const animatedTextureCount = countAnimatedTextures();
    const animatedText = animatedTextureCount > 0 ? ` ${animatedTextureCount} animated texture active.` : '';
    setStatus(`Rendered ${faceCount} faces.${animatedText}`);
  } catch (error) {
    console.error(error);
    setStatus(error.message || 'Render failed.', true);
  }
}

function updateTextureHint(fileName = null) {
  if (fileName) {
    uploadHint.textContent = `Using uploaded texture: ${fileName}. Every face now samples this one texture, and non-square images animate as vertical frame strips.`;
    return;
  }

  if (state.textureSource === DEFAULT_TEXTURE_URL) {
    uploadHint.textContent = 'Using the bundled demo texture. Upload one replacement texture to drive the whole model.';
    return;
  }

  uploadHint.textContent = 'Using one custom texture for the whole model.';
}

function applyHueTint(hue) {
  state.tintHue = hue;
  const tint = createHueTint(hue);
  state.renderer.setTint(0, tint);
  tintSwatch.style.background = colorToCss(tint);
  tintReadout.textContent = `${tint.r}, ${tint.g}, ${tint.b}`;
}

function updateFrameDelayReadout() {
  frameDelayReadout.textContent = `${state.frameDelayMs} ms`;
}

function countAnimatedTextures() {
  let count = 0;
  for (const handle of state.renderer.textureRegistry.handles.values()) {
    if (handle.frameCount > 1) count += 1;
  }
  return count;
}

async function loadJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Could not load ${url}`);
  }
  return response.json();
}

function setStatus(message, isError = false) {
  statusEl.textContent = message;
  statusEl.dataset.error = isError ? 'true' : 'false';
}
