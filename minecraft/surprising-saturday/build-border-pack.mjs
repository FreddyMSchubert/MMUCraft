import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(new URL('../../services/web/package.json', import.meta.url));
const sharp = require('sharp');
const { zipSync, strToU8 } = require('fflate');
const output = path.resolve(here, '../../services/web/public/packs/chunklocked.zip');
const colours = {
  white: [249, 255, 254], orange: [249, 128, 29], magenta: [199, 78, 189],
  light_blue: [58, 179, 218], yellow: [254, 216, 61], lime: [128, 199, 31],
  pink: [243, 139, 170], gray: [71, 79, 82], light_gray: [157, 157, 151],
  cyan: [22, 156, 156], purple: [137, 50, 184], blue: [60, 68, 170],
  brown: [131, 84, 50], green: [94, 124, 22], red: [176, 46, 38], black: [29, 29, 33],
};
const facings = ['north', 'east', 'south', 'west', 'up', 'down'];
const files = {
  'pack.mcmeta': strToU8(JSON.stringify({ pack: {
    min_format: [97, 1], max_format: [97, 1],
    description: 'Chunklocked animated chunk borders',
  } })),
};

// Vanilla 26.3 assets/minecraft/textures/misc/forcefield.png.
const source = await readFile(path.join(here, 'border-pack/forcefield.png'));
const { data: pixels, info } = await sharp(source).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
if (info.width !== 16 || info.height !== 16) throw new Error('Expected the 16x16 vanilla forcefield texture');

for (const [index, [name, rgb]] of Object.entries(colours).entries()) {
  const width = 128; // Vanilla border UV advances 0.5 per block: 16 blocks contain eight repeats.
  const height = 256; // Each display is 32 blocks tall: sixteen repeats.
  const frames = 8; // Shift one source pixel every two ticks; the stripe pattern repeats after eight.
  const image = Buffer.alloc(width * height * frames * 4);
  for (let frame = 0; frame < frames; frame++) {
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const src = ((y % 16) * 16 + ((x + frame) % 16)) * 4;
        const dst = ((frame * height + y) * width + x) * 4;
        const shade = pixels[src] / 201;
        for (let channel = 0; channel < 3; channel++) {
          image[dst + channel] = Math.round(Math.max(55, rgb[channel]) * shade);
        }
        image[dst + 3] = Math.round(pixels[src + 3] * 0.8);
      }
    }
  }
  const texture = `assets/chunklocked/textures/block/border_${name}.png`;
  files[texture] = await sharp(image, { raw: { width, height: height * frames, channels: 4 } })
    .png({ compressionLevel: 9 }).toBuffer();
  files[`${texture}.mcmeta`] = strToU8(JSON.stringify({ animation: {
    frametime: 2, width, height, interpolate: false,
  } }));
  files[`assets/chunklocked/models/block/border_${name}.json`] = strToU8(JSON.stringify({
    ambientocclusion: false,
    textures: { wall: `chunklocked:block/border_${name}`, particle: `chunklocked:block/border_${name}` },
    elements: [{
      from: [0, 0, 0], to: [16, 16, 16], shade_direction_override: 'up',
      faces: Object.fromEntries(['north', 'east', 'south', 'west'].map(face =>
        [face, { uv: [0, 0, 16, 16], texture: '#wall' }])),
    }],
  }));
  const block = index < 12 ? 'command_block' : 'chain_command_block';
  const state = index % 12;
  const key = `conditional=${state >= 6},facing=${facings[state % 6]}`;
  const stateFile = `assets/minecraft/blockstates/${block}.json`;
  const variants = files[stateFile] ??= {};
  variants[key] = { model: `chunklocked:block/border_${name}` };
}

// Keep chain command block states outside the four dye slots on their vanilla models.
const chain = files['assets/minecraft/blockstates/chain_command_block.json'];
for (const conditional of [false, true]) {
  for (const facing of facings) {
    const key = `conditional=${conditional},facing=${facing}`;
    if (chain[key]) continue;
    const rotation = facing === 'up' ? { x: 270 } : facing === 'down' ? { x: 90 }
      : facing === 'east' ? { y: 90 } : facing === 'south' ? { y: 180 }
      : facing === 'west' ? { y: 270 } : {};
    chain[key] = { model: `minecraft:block/chain_command_block${conditional ? '_conditional' : ''}`, ...rotation };
  }
}
for (const block of ['command_block', 'chain_command_block']) {
  const stateFile = `assets/minecraft/blockstates/${block}.json`;
  files[stateFile] = strToU8(JSON.stringify({ variants: files[stateFile] }));
}

await mkdir(path.dirname(output), { recursive: true });
const archive = zipSync(files, { level: 9, mtime: new Date('1980-01-01T00:00:00Z') });
await writeFile(output, archive);
const sha1 = createHash('sha1').update(archive).digest('hex');
const propertiesPath = path.join(here, 'server.properties');
const properties = await readFile(propertiesPath, 'utf8');
if (!/^resource-pack-sha1=[^\r\n]*/m.test(properties)) throw new Error('server.properties has no resource-pack-sha1');
await writeFile(propertiesPath, properties.replace(/^resource-pack-sha1=[^\r\n]*/m, `resource-pack-sha1=${sha1}`));
console.log(`${output} (${archive.length} bytes, SHA1 ${sha1})`);
