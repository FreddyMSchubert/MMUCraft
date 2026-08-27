import { readFileSync } from 'node:fs';
import wordListPath from 'word-list';

const WORDS = `\n${readFileSync(wordListPath, 'utf8')}\n`;

export function GET(request: Request) {
	const guess = new URL(request.url).searchParams.get('guess') ?? '';
	// ponytail: This linear scan keeps memory low. Index the list if Wordle traffic makes it slow.
	const isWord = /^[a-z]{3,32}$/.test(guess) && WORDS.includes(`\n${guess}\n`);
	return Response.json({ isWord });
}
