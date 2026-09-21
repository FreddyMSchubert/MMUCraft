// Run with: node render-variants.cjs
// Install the items-respack-generator dependencies first (npm ci) if needed.
// The original remote sprite is input.png. Only its red pixels are recolored.
const path = require('node:path');
const { createRequire } = require('node:module');

const generatorPackage = path.resolve(__dirname,
  '../../../../../../respack/items-respack-generator/package.json');
const sharp = createRequire(generatorPackage)('sharp');

const CHANNELS = 16;
const HUE_START = 0;
const COLOR = {
  off: {
    screenHighlight: { lightness: 0.72, chroma: 0.08 },
    screen: { lightness: 0.58, chroma: 0.09 },
    button: { lightness: 0.72, chroma: 0.08 },
    antenna: { lightness: 0.38, chroma: 0 },
  },
  on: {
    screenHighlight: { lightness: 0.88, chroma: 0.10 },
    screen: { lightness: 0.71, chroma: 0.12 },
    button: { lightness: 0.82, chroma: 0.12 },
    antenna: { lightness: 0.92, chroma: 0.13 },
  },
};

// Exact colors from input.png; grey pixels, including button shadows and the
// two antenna connector pixels, are deliberately absent from this table.
const ORIGINAL = {
  antenna: 'dc2d25ff',       // Four pixels at the top
  screenHighlight: 'e8362aff',
  screen: '901817ff',
  button: 'ef4636ff',       // The four button faces
};
const EXPECTED_PIXELS = { antenna: 4, screenHighlight: 3, screen: 9, button: 4 };

function linearSrgb(lightness, chroma, hue) {
  const angle = hue * Math.PI / 180;
  const a = chroma * Math.cos(angle);
  const b = chroma * Math.sin(angle);
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
  ];
}

function oklch(hue, { lightness, chroma }) {
  let low = 0;
  let high = chroma;
  for (let attempt = 0; attempt < 20; attempt++) {
    const middle = (low + high) / 2;
    const rgb = linearSrgb(lightness, middle, hue);
    if (rgb.every((channel) => channel >= 0 && channel <= 1)) low = middle;
    else high = middle;
  }
  return linearSrgb(lightness, low, hue).map((channel) => {
    const value = Math.max(0, Math.min(1, channel));
    return Math.round(255 * (value <= 0.0031308
      ? 12.92 * value : 1.055 * value ** (1 / 2.4) - 0.055));
  });
}

function key(pixels, offset) {
  return pixels.subarray(offset, offset + 4).toString('hex');
}

async function main() {
  const { data: original, info } = await sharp(path.join(__dirname, 'input.png'))
    .ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  if (info.width !== 16 || info.height !== 16 || info.channels !== 4) {
    throw new Error('input.png must be a 16x16 RGBA sprite.');
  }

  const originalToRole = new Map(Object.entries(ORIGINAL).map(([role, color]) => [color, role]));
  const counts = Object.fromEntries(Object.keys(ORIGINAL).map((role) => [role, 0]));
  for (let offset = 0; offset < original.length; offset += 4) {
    const role = originalToRole.get(key(original, offset));
    if (role) counts[role]++;
  }
  for (const [role, expected] of Object.entries(EXPECTED_PIXELS)) {
    if (counts[role] !== expected) {
      throw new Error(`input.png has ${counts[role]} ${role} pixels; expected ${expected}.`);
    }
  }

  for (let channel = 1; channel <= CHANNELS; channel++) {
    const hue = HUE_START + (channel - 1) * 360 / CHANNELS;
    for (const state of ['off', 'on']) {
      const paint = (settings) => oklch(hue, settings);
      const palette = {
        antenna: paint(COLOR[state].antenna),
        screenHighlight: paint(COLOR[state].screenHighlight),
        screen: paint(COLOR[state].screen),
        button: paint(COLOR[state].button),
      };
      const pixels = Buffer.from(original);
      for (let offset = 0; offset < pixels.length; offset += 4) {
        const role = originalToRole.get(key(original, offset));
        if (role) pixels.set(palette[role], offset);
      }
      const file = channel === 1 && state === 'off' ? 'texture.png' : `texture-${channel}-${state}.png`;
      await sharp(pixels, { raw: { width: 16, height: 16, channels: 4 } })
        .png().toFile(path.join(__dirname, file));
    }
  }
  console.log('Rendered 32 redstone remote textures from input.png.');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
