/** Keep only Markdown inside enabled drop blocks. Guards must occupy whole lines. */
export function filterDropGuards(
	markdown: string,
	enabledDrops: ReadonlySet<string>,
	itemDrops: Readonly<Partial<Record<string, string | null>>> = {},
) {
	const stack: boolean[] = [];
	return markdown
		.split(/\r?\n/)
		.filter((line) => {
			const itemStart = /^:::drop-item[ \t]+([a-z0-9._-]+)[ \t]*$/.exec(line);
			if (itemStart) {
				const drop = itemDrops[itemStart[1] ?? ''];
				stack.push(drop === null || (drop !== undefined && enabledDrops.has(drop)));
				return false;
			}
			const start = /^:::drop[ \t]+([a-z0-9._-]+(?:\/[a-z0-9._-]+)*)[ \t]*$/.exec(line);
			if (start) {
				stack.push(enabledDrops.has(start[1] ?? ''));
				return false;
			}
			if (/^:::end-drop[ \t]*$/.test(line)) {
				stack.pop();
				return false;
			}
			if (/^:::drop-indicator[ \t]*$/.test(line)) return false;
			return stack.every(Boolean);
		})
		.join('\n');
}
