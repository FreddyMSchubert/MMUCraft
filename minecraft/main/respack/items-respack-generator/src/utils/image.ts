import sharp from 'sharp';

export interface ImageInfo {
  readonly width: number;
  readonly height: number;
}

export async function readImageInfo(filePath: string): Promise<ImageInfo> {
  const metadata = await sharp(filePath).metadata();

  if (!metadata.width || !metadata.height) {
    throw new Error(`Could not read dimensions for image: ${filePath}`);
  }

  return {
    width: metadata.width,
    height: metadata.height,
  };
}

export async function writeCompositedArmorTexture(options: {
  readonly basePngPath: string;
  readonly overlayPngPath: string;
  readonly destinationPngPath: string;
}): Promise<void> {
  const baseInfo = await readImageInfo(options.basePngPath);
  const overlayInfo = await readImageInfo(options.overlayPngPath);

  if (baseInfo.width * overlayInfo.height !== baseInfo.height * overlayInfo.width) {
    throw new Error(
      `Aspect ratio mismatch between base armor texture ${options.basePngPath} and overlay ${options.overlayPngPath}.`,
    );
  }

  const resizedBaseBuffer = await sharp(options.basePngPath)
    .resize({
      width: overlayInfo.width,
      height: overlayInfo.height,
      kernel: sharp.kernel.nearest,
      fit: 'fill',
    })
    .png()
    .toBuffer();

  await sharp(resizedBaseBuffer)
    .composite([{ input: options.overlayPngPath }])
    .png()
    .toFile(options.destinationPngPath);
}

export async function writeUpscaledCopy(options: {
  readonly sourcePngPath: string;
  readonly referencePngPath: string;
  readonly destinationPngPath: string;
}): Promise<void> {
  const sourceInfo = await readImageInfo(options.sourcePngPath);
  const referenceInfo = await readImageInfo(options.referencePngPath);

  if (sourceInfo.width * referenceInfo.height !== sourceInfo.height * referenceInfo.width) {
    throw new Error(
      `Aspect ratio mismatch between ${options.sourcePngPath} and ${options.referencePngPath}.`,
    );
  }

  await sharp(options.sourcePngPath)
    .resize({
      width: referenceInfo.width,
      height: referenceInfo.height,
      kernel: sharp.kernel.nearest,
      fit: 'fill',
    })
    .png()
    .toFile(options.destinationPngPath);
}
