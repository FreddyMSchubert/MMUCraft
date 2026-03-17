import { cliUsage, resolveOptionsFromCli } from './config';
import { discoverItems } from './discovery/discoverItems';
import { generateResourcePack } from './generation/resourcePackGenerator';

async function main(): Promise<void> {
  const rawArgs = process.argv.slice(2);

  if (rawArgs.includes('--help')) {
    console.log(cliUsage());
    return;
  }

  const options = resolveOptionsFromCli(rawArgs);
  const items = await discoverItems(options.sourceDir);
  const summary = await generateResourcePack(items, options);

  console.log('Generation complete.');
  console.log(`  Output: ${options.outputDir}`);
  console.log(`  Items discovered: ${summary.discoveredItems}`);
  console.log(`  Basic items: ${summary.basicItems}`);
  console.log(`  Basic 3D items: ${summary.basic3dItems}`);
  console.log(`  Hats: ${summary.hats}`);
  console.log(`  Charms: ${summary.charms}`);
  console.log(`  Command block cases: ${summary.commandBlockCases}`);
  console.log(`  Carved pumpkin cases: ${summary.carvedPumpkinCases}`);
  console.log(`  Files written: ${summary.generatedFiles}`);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
