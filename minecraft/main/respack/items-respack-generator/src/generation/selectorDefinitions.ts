import type { SelectorCase } from '../types';

function createModelReference(selectorCase: SelectorCase): Record<string, unknown> {
  const model: Record<string, unknown> = {
    type: 'minecraft:model',
    model: selectorCase.modelId,
  };

  if (selectorCase.tintColor !== undefined) {
    model.tints = [
      {
        type: 'minecraft:dye',
        default: selectorCase.tintColor,
      },
    ];
  }

  return model;
}

export function createCommandBlockItemDefinition(cases: readonly SelectorCase[]): Record<string, unknown> {
  return {
    model: {
      type: 'minecraft:select',
      property: 'minecraft:custom_model_data',
      index: 0,
      cases: cases.map((selectorCase) => ({
        when: selectorCase.when,
        model: createModelReference(selectorCase),
      })),
      fallback: {
        type: 'minecraft:model',
        model: 'minecraft:item/command_block',
      },
    },
  };
}

export function createCarvedPumpkinItemDefinition(cases: readonly SelectorCase[]): Record<string, unknown> {
  return {
    model: {
      type: 'minecraft:select',
      property: 'minecraft:custom_model_data',
      cases: cases.map((selectorCase) => ({
        when: selectorCase.when,
        model: createModelReference(selectorCase),
      })),
      fallback: {
        type: 'minecraft:model',
        model: 'minecraft:block/carved_pumpkin',
      },
    },
  };
}
