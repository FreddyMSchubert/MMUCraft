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

function createBaseItemDefinition(
	cases: readonly SelectorCase[],
	baseModel: string,
	includeAnimatedCharms = false,
): Record<string, unknown> {
	const modelCases = cases
		.filter(
			(selectorCase) =>
				!includeAnimatedCharms || selectorCase.when !== 'charm-redstone-remote',
		)
		.map((selectorCase) => ({
			when: selectorCase.when,
			model: createModelReference(selectorCase),
		}));
	if (includeAnimatedCharms) {
		const remote = cases.find((item) => item.when === 'charm-redstone-remote');
		if (remote) {
			const model = (frequency: number, lit: boolean) => ({
				type: 'minecraft:model',
				model:
					frequency === 1 && !lit
						? remote.modelId
						: `${remote.modelId}-${frequency}-${lit ? 'on' : 'off'}`,
			});
			const lamp = (frequency: number) => ({
				type: 'minecraft:condition',
				property: 'minecraft:custom_model_data',
				index: 0,
				on_true: model(frequency, true),
				on_false: model(frequency, false),
			});
			modelCases.push({
				when: remote.when,
				model: {
					type: 'minecraft:range_dispatch',
					property: 'minecraft:custom_model_data',
					index: 0,
					fallback: lamp(16),
					entries: Array.from({ length: 15 }, (_, index) => ({
						threshold: index + 1,
						model: lamp(index + 1),
					})),
				},
			});
		}
		const range = (
			folder: string,
			names: readonly string[],
			thresholds: readonly number[],
		) => ({
			type: 'minecraft:range_dispatch',
			property: 'minecraft:custom_model_data',
			index: 0,
			fallback: { type: 'minecraft:model', model: `general-pack:item/${folder}/${names[0]}` },
			entries: thresholds.map((threshold, index) => ({
				threshold,
				model: {
					type: 'minecraft:model',
					model: `general-pack:item/${folder}/${names[index + 1]}`,
				},
			})),
		});
		modelCases.push({
			when: 'charm-slime-detector',
			model: range(
				'slime-detector',
				Array.from({ length: 7 }, (_, n) =>
					n === 6 ? 'slime-detector-loading' : `slime-detector-${n}`,
				),
				[1, 2, 3, 4, 5, 6],
			),
		});
		modelCases.push({
			when: 'charm-wallet',
			model: range(
				'wallet',
				[0, 1, 5, 10, 50, 100, 500, 1000, 5000, 10000].map((n) => `wallet-${n}`),
				[1, 5, 10, 50, 100, 500, 1000, 5000, 10000],
			),
		});
		modelCases.push({
			when: 'charm-sculk-phial',
			model: range(
				'sculk-phial',
				Array.from({ length: 22 }, (_, n) => `sculk-phial_${n}`),
				[
					1, 71, 140, 210, 280, 349, 419, 489, 559, 628, 698, 768, 837, 907, 977, 1047,
					1116, 1186, 1256, 1325, 1395,
				],
			),
		});
	}
	return {
		model: {
			type: 'minecraft:select',
			property: 'minecraft:custom_model_data',
			index: 0,
			cases: modelCases,
			fallback: {
				type: 'minecraft:model',
				model: baseModel,
			},
		},
	};
}

export function createCommandBlockItemDefinition(
	cases: readonly SelectorCase[],
): Record<string, unknown> {
	return createBaseItemDefinition(cases, 'minecraft:item/command_block');
}

export function createHeartOfTheSeaItemDefinition(
	cases: readonly SelectorCase[],
): Record<string, unknown> {
	return createBaseItemDefinition(
		cases.filter(
			(item) =>
				!['charm-slime-detector', 'charm-wallet', 'charm-sculk-phial'].includes(item.when),
		),
		'minecraft:item/heart_of_the_sea',
		true,
	);
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
