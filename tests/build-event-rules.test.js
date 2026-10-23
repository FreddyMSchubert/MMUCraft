const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const {
	buildScore,
	buildStage,
	driveImageUrl,
	insideBuild,
} = require('../services/api/dist/build-events/build-event-rules');

test('build phases change at the exact configured times', () => {
	const event = {
		starts_at_unix_ms: 10,
		build_ends_at_unix_ms: 20,
		voting_starts_at_unix_ms: 30,
		voting_ends_at_unix_ms: 40,
		warp_ends_at_unix_ms: 50,
	};
	assert.deepEqual(
		[9, 10, 20, 30, 40, 50].map((now) => buildStage(event, now)),
		['upcoming', 'building', 'preparing', 'voting', 'visiting', 'ended'],
	);
});

test('Google Drive file links become image URLs, and folders are rejected', () => {
	assert.equal(
		driveImageUrl(
			'https://drive.google.com/file/d/1QY-Z_QRMDh6d33n3FNBcGWLB8UTObAtY/view?usp=sharing',
		),
		'https://drive.google.com/thumbnail?id=1QY-Z_QRMDh6d33n3FNBcGWLB8UTObAtY&sz=w1920',
	);
	assert.throws(() => driveImageUrl('https://drive.google.com/drive/folders/abc'));
	assert.throws(() => driveImageUrl('https://example.com/file/d/abc/view'));
});

test('voting uses full chunk columns in the same dimension', () => {
	const area = {
		dimension: 'minecraft:overworld',
		min_chunk_x: -2,
		max_chunk_x: 1,
		min_chunk_z: 3,
		max_chunk_z: 4,
	};
	assert.equal(
		insideBuild({ dimension: 'minecraft:overworld', chunkX: -2, chunkZ: 4 }, area),
		true,
	);
	assert.equal(
		insideBuild({ dimension: 'minecraft:the_nether', chunkX: -2, chunkZ: 4 }, area),
		false,
	);
	assert.equal(
		insideBuild({ dimension: 'minecraft:overworld', chunkX: 2, chunkZ: 4 }, area),
		false,
	);
});

test('category averages retain decimals before summing', () => {
	const categories = [
		{ id: 'creativity', name: 'Creativity', description: '', min: 1, max: 10 },
		{ id: 'detail', name: 'Detail', description: '', min: 0, max: 5 },
	];
	assert.deepEqual(
		buildScore(categories, [
			{ scores: { creativity: 10, detail: 0 } },
			{ scores: { creativity: 9, detail: 3 } },
		]),
		{
			averages: [
				{ id: 'creativity', average: 9.5 },
				{ id: 'detail', average: 1.5 },
			],
			total: 11,
		},
	);
	assert.equal(buildScore(categories, []), null);
});

test('migration enforces one team per player and one final vote per build', () => {
	const db = new DatabaseSync(':memory:');
	try {
		db.exec('PRAGMA foreign_keys = ON; CREATE TABLE users (id INTEGER PRIMARY KEY)');
		const migration = readFileSync(
			join(__dirname, '../services/api/drizzle/0052_sad_smasher.sql'),
			'utf8',
		);
		for (const statement of migration.split('--> statement-breakpoint'))
			if (statement.trim()) db.exec(statement);
		db.exec('INSERT INTO users (id) VALUES (1), (2)');
		db.exec(
			"INSERT INTO build_events (id, title, description, starts_at_unix_ms, build_ends_at_unix_ms, voting_starts_at_unix_ms, voting_ends_at_unix_ms, warp_ends_at_unix_ms, min_team_size, max_team_size, categories_json) VALUES (1, 'Test', '', 1, 2, 3, 4, 5, 1, 3, '[]')",
		);
		db.exec(
			"INSERT INTO build_submissions (id, event_id, title, description, images_json, dimension, block_x, block_y, block_z, min_chunk_x, max_chunk_x, min_chunk_z, max_chunk_z) VALUES (1, 1, 'One', '', '[]', 'minecraft:overworld', 0, 64, 0, 0, 0, 0, 0), (2, 1, 'Two', '', '[]', 'minecraft:overworld', 0, 64, 0, 0, 0, 0, 0)",
		);
		db.exec(
			'INSERT INTO build_submission_members (event_id, submission_id, user_id) VALUES (1, 1, 1)',
		);
		assert.throws(() =>
			db.exec(
				'INSERT INTO build_submission_members (event_id, submission_id, user_id) VALUES (1, 2, 1)',
			),
		);
		db.exec(
			"INSERT INTO build_votes (submission_id, voter_user_id, scores_json, cast_at_unix_ms) VALUES (1, 2, '{}', 3)",
		);
		assert.throws(() =>
			db.exec(
				"INSERT INTO build_votes (submission_id, voter_user_id, scores_json, cast_at_unix_ms) VALUES (1, 2, '{}', 4)",
			),
		);
		db.exec('DELETE FROM build_events WHERE id = 1');
		assert.equal(db.prepare('SELECT count(*) AS count FROM build_votes').get().count, 0);
		assert.equal(
			db.prepare('SELECT count(*) AS count FROM build_submission_members').get().count,
			0,
		);
	} finally {
		db.close();
	}
});
