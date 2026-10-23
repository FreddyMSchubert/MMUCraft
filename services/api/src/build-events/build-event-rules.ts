import { BadRequestException } from '@nestjs/common';

export interface BuildCategory {
	id: string;
	name: string;
	description: string;
	min: number;
	max: number;
}

export interface BuildDates {
	starts_at_unix_ms: number;
	build_ends_at_unix_ms: number;
	voting_starts_at_unix_ms: number;
	voting_ends_at_unix_ms: number;
	warp_ends_at_unix_ms: number;
}

export function buildStage(event: BuildDates, now = Date.now()) {
	if (now < event.starts_at_unix_ms) return 'upcoming';
	if (now < event.build_ends_at_unix_ms) return 'building';
	if (now < event.voting_starts_at_unix_ms) return 'preparing';
	if (now < event.voting_ends_at_unix_ms) return 'voting';
	if (now < event.warp_ends_at_unix_ms) return 'visiting';
	return 'ended';
}

export function driveImageUrl(input: string) {
	let url: URL;
	try {
		url = new URL(input);
	} catch {
		throw new BadRequestException('Use a Google Drive image share link');
	}
	if (url.protocol !== 'https:' || url.hostname !== 'drive.google.com')
		throw new BadRequestException('Use a Google Drive image share link');
	const pathMatch = /^\/file\/d\/([a-zA-Z0-9_-]+)(?:\/|$)/.exec(url.pathname);
	const id =
		pathMatch?.[1] ??
		(['/open', '/uc'].includes(url.pathname) ? url.searchParams.get('id') : null);
	if (!id || !/^[a-zA-Z0-9_-]+$/.test(id))
		throw new BadRequestException('Use a link to one Google Drive image file');
	return `https://drive.google.com/thumbnail?id=${id}&sz=w1920`;
}

export function insideBuild(
	position: { dimension: string; chunkX: number; chunkZ: number },
	area: {
		dimension: string;
		min_chunk_x: number;
		max_chunk_x: number;
		min_chunk_z: number;
		max_chunk_z: number;
	},
) {
	return (
		position.dimension === area.dimension &&
		position.chunkX >= area.min_chunk_x &&
		position.chunkX <= area.max_chunk_x &&
		position.chunkZ >= area.min_chunk_z &&
		position.chunkZ <= area.max_chunk_z
	);
}

export function buildScore(
	categories: BuildCategory[],
	votes: { scores: Record<string, number> }[],
) {
	if (votes.length === 0) return null;
	const averages = categories.map((category) => ({
		id: category.id,
		average:
			votes.reduce((sum, vote) => sum + (vote.scores[category.id] ?? 0), 0) / votes.length,
	}));
	return { averages, total: averages.reduce((sum, entry) => sum + entry.average, 0) };
}
