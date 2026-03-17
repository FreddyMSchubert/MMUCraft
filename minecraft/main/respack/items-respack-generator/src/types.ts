export type ItemType = 'basic' | 'basic-3d' | 'hat' | 'charm';

export interface BaseDiscoveredItem {
  readonly type: ItemType;
  readonly sourceDirectory: string;
  readonly relativeDirectory: string;
  readonly customModelData: string;
  readonly resourcePath: string;
  readonly baseName: string;
}

export interface BasicItemDefinition extends BaseDiscoveredItem {
  readonly type: 'basic';
  readonly texturePngPath: string;
  readonly textureMcmetaPath?: string;
}

export interface Basic3dItemDefinition extends BaseDiscoveredItem {
  readonly type: 'basic-3d';
  readonly modelJsonPath: string;
  readonly modelTexturePngPath: string;
  readonly modelTextureMcmetaPath?: string;
}

export interface HatItemDefinition extends BaseDiscoveredItem {
  readonly type: 'hat';
  readonly isTinted: boolean;
  readonly modelJsonPath: string;
  readonly modelTexturePngPath: string;
  readonly modelTextureMcmetaPath?: string;
}

export interface CharmItemDefinition extends BaseDiscoveredItem {
  readonly type: 'charm';
  readonly isLeggings: boolean;
  readonly equippableAssetId: string;
  readonly texturePngPath: string;
  readonly textureMcmetaPath?: string;
  readonly equippablePngPath: string;
}

export type DiscoveredItem =
  | BasicItemDefinition
  | Basic3dItemDefinition
  | HatItemDefinition
  | CharmItemDefinition;

export type EquipmentLayerType = 'humanoid' | 'humanoid_leggings';

export type ArmorMaterial =
  | 'chainmail'
  | 'leather'
  | 'iron'
  | 'gold'
  | 'diamond'
  | 'netherite'
  | 'copper';

export interface GeneratorOptions {
  readonly sourceDir: string;
  readonly vanillaArmorAssetsDir: string;
  readonly outputDir: string;
  readonly namespace: string;
  readonly packDescription: string;
}

export interface SelectorCase {
  readonly when: string;
  readonly modelId: string;
  readonly isTinted?: boolean;
}

export interface GenerationSummary {
  readonly discoveredItems: number;
  readonly basicItems: number;
  readonly basic3dItems: number;
  readonly hats: number;
  readonly charms: number;
  readonly commandBlockCases: number;
  readonly carvedPumpkinCases: number;
  readonly generatedFiles: number;
}
