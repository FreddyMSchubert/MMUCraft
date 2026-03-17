declare module 'path' {
  export const sep: string;
  export function resolve(...paths: string[]): string;
  export function join(...paths: string[]): string;
  export function dirname(path: string): string;
  export function basename(path: string, ext?: string): string;
  export function extname(path: string): string;
  export function relative(from: string, to: string): string;
  export const posix: {
    basename(path: string, ext?: string): string;
  };

  const pathModule: {
    sep: typeof sep;
    resolve: typeof resolve;
    join: typeof join;
    dirname: typeof dirname;
    basename: typeof basename;
    extname: typeof extname;
    relative: typeof relative;
    posix: typeof posix;
  };

  export default pathModule;
}

declare module 'fs/promises' {
  export interface Dirent {
    name: string;
    isDirectory(): boolean;
  }

  export interface Stats {
    isDirectory(): boolean;
  }

  export function access(path: string): Promise<void>;
  export function mkdir(path: string, options?: { recursive?: boolean }): Promise<void>;
  export function rm(
    path: string,
    options?: { recursive?: boolean; force?: boolean },
  ): Promise<void>;
  export function copyFile(source: string, destination: string): Promise<void>;
  export function readdir(
    path: string,
    options: { withFileTypes: true },
  ): Promise<Dirent[]>;
  export function readFile(path: string, encoding: string): Promise<string>;
  export function writeFile(path: string, data: string, encoding: string): Promise<void>;
  export function stat(path: string): Promise<Stats>;

  const fsPromises: {
    access: typeof access;
    mkdir: typeof mkdir;
    rm: typeof rm;
    copyFile: typeof copyFile;
    readdir: typeof readdir;
    readFile: typeof readFile;
    writeFile: typeof writeFile;
    stat: typeof stat;
  };

  export default fsPromises;
}

declare module 'sharp' {
  export interface Metadata {
    width?: number;
    height?: number;
  }

  export interface SharpInstance {
    metadata(): Promise<Metadata>;
    resize(options: {
      width: number;
      height: number;
      kernel: unknown;
      fit: 'fill';
    }): SharpInstance;
    png(): SharpInstance;
    toBuffer(): Promise<Buffer>;
    composite(inputs: Array<{ input: string }>): SharpInstance;
    toFile(path: string): Promise<void>;
  }

  export interface SharpStatic {
    (input: string | Buffer): SharpInstance;
    kernel: {
      nearest: unknown;
    };
  }

  const sharp: SharpStatic;
  export default sharp;
}

declare const process: {
  argv: string[];
  cwd(): string;
  exitCode?: number;
};

declare class Buffer {}
