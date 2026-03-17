import path from 'path';

const RESOURCE_PATH_SEGMENT_PATTERN = /^[a-z0-9._-]+$/;
const RESOURCE_IDENTIFIER_PATTERN = /^[a-z0-9._-]+(?:\/[a-z0-9._-]+)*$/;

export function toPosixPath(value: string): string {
  return value.split(path.sep).join('/');
}

export function assertValidResourcePath(relativePath: string, label: string): void {
  if (relativePath.length === 0) {
    throw new Error(`${label} cannot be empty.`);
  }

  const segments = relativePath.split('/');
  for (const segment of segments) {
    if (!RESOURCE_PATH_SEGMENT_PATTERN.test(segment)) {
      throw new Error(
        `${label} contains invalid resource path segment "${segment}" in "${relativePath}".`,
      );
    }
  }
}

export function assertValidResourceIdentifier(value: string, label: string): void {
  if (!RESOURCE_IDENTIFIER_PATTERN.test(value)) {
    throw new Error(
      `${label} must match Minecraft resource identifier path rules: ${value}`,
    );
  }
}

export function basenameWithoutExtension(filePath: string): string {
  return path.basename(filePath, path.extname(filePath));
}

export function replaceTrailingVariant(assetId: string, variant: string): string {
  const separatorIndex = assetId.lastIndexOf('__');
  if (separatorIndex === -1) {
    return `${assetId}__${variant}`;
  }

  return `${assetId.slice(0, separatorIndex)}__${variant}`;
}
