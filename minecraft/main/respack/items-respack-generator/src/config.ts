import path from 'path';
import type { ArmorMaterial, GeneratorOptions } from './types';

export const DEFAULT_NAMESPACE = 'mmu_pack';
export const DEFAULT_PACK_DESCRIPTION =
  'Official MMU MC Soc Server Resource Pack. Do not disable.';

export const PACK_MCMETA = {
  pack: {
    min_format: [75, 0],
    max_format: 75,
    description: DEFAULT_PACK_DESCRIPTION,
  },
} as const;

export const COMMAND_BLOCK_ITEM_ID = 'command_block';
export const CARVED_PUMPKIN_ITEM_ID = 'carved_pumpkin';

export const CHARM_ARMOR_MATERIALS: readonly ArmorMaterial[] = [
  'chainmail',
  'leather',
  'iron',
  'gold',
  'diamond',
  'netherite',
  'copper',
] as const;

export const LEATHER_UNDYED_COLOR = -6265536;

export function resolveOptionsFromCli(argv: readonly string[]): GeneratorOptions {
  const values = new Map<string, string>();

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith('--')) {
      throw new Error(`Unexpected argument: ${token}`);
    }

    const key = token.slice(2);
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for --${key}`);
    }

    values.set(key, value);
    index += 1;
  }

  const cwd = process.cwd();

  return {
    sourceDir: path.resolve(cwd, values.get('source') ?? 'items'),
    vanillaArmorAssetsDir: path.resolve(
      cwd,
      values.get('vanilla-armor') ?? 'vanilla_armor_assets',
    ),
    outputDir: path.resolve(cwd, values.get('output') ?? 'generated-resource-pack'),
    namespace: values.get('namespace') ?? DEFAULT_NAMESPACE,
    packDescription: values.get('description') ?? DEFAULT_PACK_DESCRIPTION,
  };
}

export function cliUsage(): string {
  return [
    'Usage:',
    '  npm run generate -- --source ./items --vanilla-armor ./vanilla_armor_assets --output ./dist-resource-pack',
    '',
    'Optional flags:',
    '  --namespace <namespace>      Resource namespace to generate under (default: mmu_pack)',
    '  --description <text>         pack.mcmeta description',
  ].join('\n');
}
