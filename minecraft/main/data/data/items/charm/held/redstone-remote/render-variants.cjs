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
  screenHighlight: { saturation: 0.8, brightness: 1 },
  screen: { saturation: 0.8, brightness: 0.68 },
  button: { saturation: 0.8, brightness: 1 },
  antennaOn: { saturation: 0.8, brightness: 1 },
  antennaOff: { saturation: 1, brightness: 0.3 },
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

function hsv(hue, saturation, brightness) {
  const chroma = brightness * saturation;
  const sector = (((hue % 360) + 360) % 360) / 60;
  const secondary = chroma * (1 - Math.abs(sector % 2 - 1));
  const pairs = [[chroma, secondary, 0], [secondary, chroma, 0], [0, chroma, secondary],
    [0, secondary, chroma], [secondary, 0, chroma], [chroma, 0, secondary]];
  return pairs[Math.floor(sector)].map((part) => Math.round((part + brightness - chroma) * 255));
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
    const paint = (settings) => hsv(hue, settings.saturation, settings.brightness);
    for (const state of ['off', 'on']) {
      const lit = state === 'on';
      const palette = {
        antenna: paint(lit ? COLOR.antennaOn : COLOR.antennaOff),
        screenHighlight: paint(COLOR.screenHighlight),
        screen: paint(COLOR.screen),
        button: paint(COLOR.button),
      };
      const pixels = Buffer.from(original);
      for (let offset = 0; offset < pixels.length; offset += 4) {
        const role = originalToRole.get(key(original, offset));
        if (role) pixels.set(palette[role], offset);
      }
      const file = channel === 1 && !lit ? 'texture.png' : `texture-${channel}-${state}.png`;
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
