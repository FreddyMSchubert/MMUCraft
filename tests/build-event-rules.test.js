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
const { BuildEventsService } = require('../services/api/dist/build-events/build-events.service');
const { DatabaseService } = require('../services/api/dist/database/database.service');
const {
	buildEvents,
	buildSubmissions,
	buildVotes,
	users,
} = require('../services/api/dist/database/schema');

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

test('revealed public builds follow place and can hide one place', () => {
	const previousUrl = process.env.DATABASE_URL;
	const previousMigrations = process.env.DATABASE_MIGRATIONS_PATH;
	process.env.DATABASE_URL = ':memory:';
	process.env.DATABASE_MIGRATIONS_PATH = join(__dirname, '../services/api/drizzle');
	let db;
	try {
		db = new DatabaseService();
		const now = Date.now();
		db.connection
			.insert(users)
			.values({
				id: 1,
				email: 'voter@example.com',
				minecraft_username: 'Voter',
				whitelisted_at_unix_ms: now,
				rules_accepted_at_unix_ms: now,
				created_at_unix_ms: now,
			})
			.run();
		db.connection
			.insert(buildEvents)
			.values({
				id: 1,
				title: 'Test builds',
				description: '',
				starts_at_unix_ms: now - 5000,
				build_ends_at_unix_ms: now - 4000,
				voting_starts_at_unix_ms: now - 3000,
				voting_ends_at_unix_ms: now - 2000,
				warp_ends_at_unix_ms: now + 100000,
				min_team_size: 1,
				max_team_size: 3,
				categories_json: JSON.stringify([
					{ id: 'score', name: 'Score', description: '', min: 1, max: 10 },
				]),
			})
			.run();
		for (const [id, title] of [
			[1, 'Low score'],
			[2, 'High score'],
		])
			db.connection
				.insert(buildSubmissions)
				.values({
					id,
					event_id: 1,
					title,
					description: '',
					images_json: '[]',
					dimension: 'minecraft:overworld',
					block_x: 0,
					block_y: 64,
					block_z: 0,
					min_chunk_x: 0,
					max_chunk_x: 0,
					min_chunk_z: 0,
					max_chunk_z: 0,
				})
				.run();
		for (const [submissionId, score] of [
			[1, 1],
			[2, 10],
		])
			db.connection
				.insert(buildVotes)
				.values({
					submission_id: submissionId,
					voter_user_id: 1,
					scores_json: JSON.stringify({ score }),
					cast_at_unix_ms: now - 2500,
				})
				.run();

		const events = new BuildEventsService(db, {});
		assert.deepEqual(
			events.detail('1').submissions.map((item) => item.title),
			['Low score', 'High score'],
		);
		events.reveal('1', true);
		assert.deepEqual(
			events.detail('1').submissions.map((item) => [item.title, item.rank]),
			[
				['High score', 1],
				['Low score', 2],
			],
		);
		events.setPublicRankHidden('1', '2', true);
		assert.deepEqual(
			events.detail('1').submissions.map((item) => item.title),
			['Low score', 'High score'],
		);
		assert.equal(events.detail('1').submissions[1].rank, undefined);
		assert.equal(events.detail('1').submissions[1].score, undefined);
		assert.equal(events.detail('1').submissions[0].rank, 2);
		assert.equal(events.detail('1').submissions[0].score.total, 1);
		assert.equal(events.detail('1', undefined, true).submissions[0].rank, 1);
		assert.equal(events.detail('1', undefined, true).submissions[0].publicRankHidden, true);
		events.setPublicRankHidden('1', '1', true);
		const previousRandom = Math.random;
		try {
			Math.random = () => 0;
			assert.deepEqual(
				events.detail('1').submissions.map((item) => item.title),
				['High score', 'Low score'],
			);
			Math.random = () => 0.99;
			assert.deepEqual(
				events.detail('1').submissions.map((item) => item.title),
				['Low score', 'High score'],
			);
		} finally {
			Math.random = previousRandom;
		}
		events.setPublicRankHidden('1', '1', false);
		events.setPublicRankHidden('1', '2', false);
		assert.equal(events.detail('1').submissions[0].rank, 1);
		db.connection
			.update(buildVotes)
			.set({ scores_json: JSON.stringify({ score: 10 }) })
			.run();
		assert.deepEqual(
			events.detail('1').submissions.map((item) => item.rank),
			[1, 1],
		);
	} finally {
		db?.onModuleDestroy();
		if (previousUrl === undefined) delete process.env.DATABASE_URL;
		else process.env.DATABASE_URL = previousUrl;
		if (previousMigrations === undefined) delete process.env.DATABASE_MIGRATIONS_PATH;
		else process.env.DATABASE_MIGRATIONS_PATH = previousMigrations;
	}
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
		const warpMigration = readFileSync(
			join(__dirname, '../services/api/drizzle/0054_many_sunset_bain.sql'),
			'utf8',
		);
		db.exec(warpMigration);
		assert.deepEqual(
			db
				.prepare(
					'SELECT post_voting_warp_enabled AS enabled FROM build_submissions ORDER BY id',
				)
				.all()
				.map((row) => row.enabled),
			[0, 0],
		);
		db.exec('UPDATE build_submissions SET post_voting_warp_enabled = 1 WHERE id = 1');
		assert.equal(
			db
				.prepare(
					'SELECT post_voting_warp_enabled AS enabled FROM build_submissions WHERE id = 1',
				)
				.get().enabled,
			1,
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
