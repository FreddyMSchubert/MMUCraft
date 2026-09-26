/** Keep only Markdown inside enabled drop blocks. Guards must occupy whole lines. */
export function filterDropGuards(markdown: string, enabledDrops: ReadonlySet<string>) {
	const stack: boolean[] = [];
	return markdown
		.split(/\r?\n/)
		.filter((line) => {
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
