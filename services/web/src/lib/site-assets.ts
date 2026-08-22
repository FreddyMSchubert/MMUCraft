import fs from 'node:fs';
import path from 'node:path';

const publicRoot = path.join(process.cwd(), 'public');

export function getLandingVisuals() {
	const panoramas = JSON.parse(
		fs.readFileSync(
			path.join(publicRoot, 'assets', 'landing', 'panoramas', 'manifest.source.json'),
			'utf8',
		),
	) as {
		sets: { id: string; label: string }[];
	};
	return { panorama: pick(panoramas.sets), splash: getRandomSplash() };
}

export function getSiteVisuals() {
	const directory = path.join(publicRoot, 'assets', 'site-backgrounds');
	const backgrounds = fs
		.readdirSync(directory)
		.filter((file) => /\.jpe?g$/i.test(file))
		.sort();
	return {
		background: `/assets/site-backgrounds/${encodeURIComponent(pickDaily(backgrounds))}`,
		splash: getRandomSplash(),
	};
}

function getRandomSplash() {
	const splashes = fs
		.readFileSync(path.join(publicRoot, 'assets', 'landing', 'splashes.txt'), 'utf8')
		.split(/\r?\n/)
		.map((splash) => splash.trim())
		.filter(Boolean);
	return pick(splashes);
}

function pick<T>(values: T[]) {
	if (!values.length) throw new Error('The random asset pool is empty');
	return values[Math.floor(Math.random() * values.length)];
}

function pickDaily<T>(values: T[]) {
	if (!values.length) throw new Error('The daily asset pool is empty');
	return values[Math.floor(Date.now() / 86_400_000) % values.length];
}
