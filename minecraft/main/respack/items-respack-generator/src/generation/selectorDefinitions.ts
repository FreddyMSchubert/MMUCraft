import type { SelectorCase } from '../types';

function createModelReference(selectorCase: SelectorCase): Record<string, unknown> {
	const model: Record<string, unknown> = {
		type: 'minecraft:model',
		model: selectorCase.modelId,
	};

	if (selectorCase.isTinted) {
		model.tints = [
			{
				type: 'minecraft:dye',
				default: -1,
			},
		];
	}

	if (!selectorCase.shadowModelId) {
		return model;
	}

	return {
		type: 'minecraft:select',
		property: 'minecraft:custom_model_data',
		index: 1,
		cases: [
			{
				when: 'mainmod:fishing_shadow',
				model: {
					type: 'minecraft:model',
					model: selectorCase.shadowModelId,
					tints: [{ type: 'minecraft:constant', value: 0 }],
				},
			},
		],
		fallback: model,
	};
}

export function createCommandBlockItemDefinition(
	cases: readonly SelectorCase[],
): Record<string, unknown> {
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

export function createCarvedPumpkinItemDefinition(
	cases: readonly SelectorCase[],
): Record<string, unknown> {
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
