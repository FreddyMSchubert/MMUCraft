import { BadRequestException } from '@nestjs/common';
import { and, eq, gte, lte, ne } from 'drizzle-orm';
import { claims } from '../database/database.service';

const MAX_CHUNK_COORDINATE = 1_875_000;
const MAX_SERVER_CLAIM_CHUNKS = 4096;

export interface ClaimCornerInput {
	dimension?: string;
	chunkX?: number;
	chunkZ?: number;
}

export interface ServerClaimAreaInput extends ClaimCornerInput {
	oppositeChunkX?: number;
	oppositeChunkZ?: number;
}

export interface ClaimArea {
	dimension: string;
	chunkX: number;
	chunkZ: number;
	chunkXEnd: number;
	chunkZEnd: number;
}

export function pointArea(input: ClaimCornerInput): ClaimArea {
	const chunkX = normalizeChunkCoordinate(input.chunkX);
	const chunkZ = normalizeChunkCoordinate(input.chunkZ);
	return {
		dimension: normalizeDimension(input.dimension),
		chunkX,
		chunkZ,
		chunkXEnd: chunkX,
		chunkZEnd: chunkZ,
	};
}

export function serverClaimArea(input: ServerClaimAreaInput): ClaimArea {
	const first = pointArea(input);
	const oppositeX = normalizeChunkCoordinate(input.oppositeChunkX);
	const oppositeZ = normalizeChunkCoordinate(input.oppositeChunkZ);
	const area = {
		dimension: first.dimension,
		chunkX: Math.min(first.chunkX, oppositeX),
		chunkZ: Math.min(first.chunkZ, oppositeZ),
		chunkXEnd: Math.max(first.chunkX, oppositeX),
		chunkZEnd: Math.max(first.chunkZ, oppositeZ),
	};
	if (
		(area.chunkXEnd - area.chunkX + 1) * (area.chunkZEnd - area.chunkZ + 1) >
		MAX_SERVER_CLAIM_CHUNKS
	) {
		throw new BadRequestException(
			`A server claim can cover at most ${MAX_SERVER_CLAIM_CHUNKS} chunks.`,
		);
	}
	return area;
}

export function overlappingClaim(area: ClaimArea, excludingId?: string) {
	return and(
		eq(claims.dimension, area.dimension),
		lte(claims.chunk_x, area.chunkXEnd),
		gte(claims.chunk_x_end, area.chunkX),
		lte(claims.chunk_z, area.chunkZEnd),
		gte(claims.chunk_z_end, area.chunkZ),
		excludingId === undefined ? undefined : ne(claims.id, excludingId),
	);
}

function normalizeDimension(value: string | undefined) {
	if (typeof value !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value)) {
		throw new BadRequestException('Invalid Minecraft dimension.');
	}
	return value;
}

function normalizeChunkCoordinate(value: number | undefined) {
	if (
		typeof value !== 'number' ||
		!Number.isInteger(value) ||
		Math.abs(value) > MAX_CHUNK_COORDINATE
	) {
		throw new BadRequestException('Invalid chunk coordinate.');
	}
	return value;
}
