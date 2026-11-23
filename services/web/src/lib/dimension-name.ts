export function formatDimension(dimension: string) {
	switch (dimension) {
		case 'minecraft:overworld':
			return 'Overworld';
		case 'minecraft:the_nether':
			return 'Nether';
		case 'minecraft:the_end':
			return 'End';
		default:
			return dimension;
	}
}
