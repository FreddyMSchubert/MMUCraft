import fs from 'fs/promises';
import path from 'path';

export async function pathExists(targetPath: string): Promise<boolean> {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

export async function ensureDirectory(targetDirectory: string): Promise<void> {
  await fs.mkdir(targetDirectory, { recursive: true });
}

export async function resetDirectory(targetDirectory: string): Promise<void> {
  await fs.rm(targetDirectory, { recursive: true, force: true });
  await ensureDirectory(targetDirectory);
}

export async function copyFileWithDirectory(
  sourcePath: string,
  destinationPath: string,
): Promise<void> {
  await ensureDirectory(path.dirname(destinationPath));
  await fs.copyFile(sourcePath, destinationPath);
}

export async function listSubdirectories(directory: string): Promise<string[]> {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(directory, entry.name))
    .sort((left, right) => left.localeCompare(right));
}

export async function readJsonFile<T>(filePath: string): Promise<T> {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw) as T;
}

export async function writeJsonFile(
  destinationPath: string,
  value: unknown,
): Promise<void> {
  await ensureDirectory(path.dirname(destinationPath));
  await fs.writeFile(destinationPath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

export async function writeTextFile(
  destinationPath: string,
  value: string,
): Promise<void> {
  await ensureDirectory(path.dirname(destinationPath));
  await fs.writeFile(destinationPath, value, 'utf8');
}
