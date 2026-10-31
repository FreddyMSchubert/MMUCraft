import {
	BadGatewayException,
	BadRequestException,
	ConflictException,
	ForbiddenException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import {
	DatabaseService,
	buildEvents,
	buildSubmissionMembers,
	buildSubmissions,
	buildVisits,
	buildVoterExclusions,
	buildVotes,
	playerProfiles,
	users,
} from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { effectivePlayerColor, playerAvatarUrl } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';
import {
	buildScore,
	buildStage,
	driveImageUrl,
	insideBuild,
	type BuildCategory,
} from './build-event-rules';

type Event = typeof buildEvents.$inferSelect;
interface Position {
	online: boolean;
	dimension: string;
	chunk_x: number;
	chunk_z: number;
	block_x: number;
	block_y: number;
	block_z: number;
	yaw: number;
	pitch: number;
}
interface WarpResult {
	online: boolean;
	moved: boolean;
	message: string;
	origin_dimension: string;
	origin_x: number;
	origin_y: number;
	origin_z: number;
	origin_yaw: number;
	origin_pitch: number;
	left_area: boolean;
}

function positiveId(input: string | number) {
	const id = Number(input);
	if (!Number.isSafeInteger(id) || id < 1) throw new NotFoundException('Build event not found');
	return id;
}

function nonempty(input: unknown, label: string) {
	if (typeof input !== 'string' || !input.trim()) throw new BadRequestException('Enter ' + label);
	return input.trim();
}

function integer(input: unknown, label: string) {
	if (!Number.isSafeInteger(input)) throw new BadRequestException('Enter a valid ' + label);
	return input as number;
}

function finite(input: unknown, label: string) {
	if (typeof input !== 'number' || !Number.isFinite(input))
		throw new BadRequestException('Enter a valid ' + label);
	return input;
}

function categoriesFrom(input: unknown): BuildCategory[] {
	if (!Array.isArray(input) || input.length === 0)
		throw new BadRequestException('Add at least one voting category');
	const categories = input.map((raw: unknown) => {
		if (!raw || typeof raw !== 'object')
			throw new BadRequestException('Invalid voting category');
		const item = raw as Record<string, unknown>;
		const id = nonempty(item.id, 'a category ID');
		if (!/^[a-zA-Z0-9_-]+$/.test(id)) throw new BadRequestException('Invalid category ID');
		const name = nonempty(item.name, 'a category name');
		const description = typeof item.description === 'string' ? item.description.trim() : '';
		const min = integer(item.min, 'category minimum');
		const max = integer(item.max, 'category maximum');
		if (min >= max) throw new BadRequestException('Category maximum must exceed its minimum');
		return { id, name, description, min, max };
	});
	if (new Set(categories.map((item) => item.id)).size !== categories.length)
		throw new BadRequestException('Voting category IDs must be unique');
	return categories;
}

function eventInput(body: Record<string, unknown>) {
	const title = nonempty(body.title, 'an event title');
	const shortDescription =
		typeof body.shortDescription === 'string' ? body.shortDescription.trim() : '';
	if (shortDescription.length > 280)
		throw new BadRequestException('Short description must be 280 characters or fewer');
	const description = typeof body.description === 'string' ? body.description : '';
	const starts = integer(body.startsAtUnixMs, 'build start');
	const buildEnds = integer(body.buildEndsAtUnixMs, 'build end');
	const votingStarts = integer(body.votingStartsAtUnixMs, 'voting start');
	const votingEnds = integer(body.votingEndsAtUnixMs, 'voting end');
	const warpEnds = integer(body.warpEndsAtUnixMs, 'warp end');
	if (!(
		starts < buildEnds &&
		buildEnds <= votingStarts &&
		votingStarts < votingEnds &&
		votingEnds <= warpEnds
	))
		throw new BadRequestException('Dates must run from build start through warp end in order');
	const minTeam = integer(body.minTeamSize, 'minimum team size');
	const maxTeam = integer(body.maxTeamSize, 'maximum team size');
	if (minTeam < 1 || maxTeam < minTeam)
		throw new BadRequestException(
			'Team size must have a positive minimum no greater than its maximum',
		);
	return {
		title,
		short_description: shortDescription,
		description,
		starts_at_unix_ms: starts,
		build_ends_at_unix_ms: buildEnds,
		voting_starts_at_unix_ms: votingStarts,
		voting_ends_at_unix_ms: votingEnds,
		warp_ends_at_unix_ms: warpEnds,
		min_team_size: minTeam,
		max_team_size: maxTeam,
		categories_json: JSON.stringify(categoriesFrom(body.categories)),
	};
}

function submissionInput(body: Record<string, unknown>, event: Event) {
	const title = nonempty(body.title, 'a build title');
	const description = typeof body.description === 'string' ? body.description : '';
	if (
		!Array.isArray(body.images) ||
		body.images.length > 10 ||
		body.images.some((item) => typeof item !== 'string')
	)
		throw new BadRequestException('Add up to ten Google Drive image links');
	const images = (body.images as string[]).map((item) => item.trim()).filter(Boolean);
	images.forEach(driveImageUrl);
	if (!Array.isArray(body.memberUserIds))
		throw new BadRequestException('Select the team members');
	const memberUserIds = body.memberUserIds.map((item: unknown) => integer(item, 'team member'));
	if (memberUserIds.length < event.min_team_size || memberUserIds.length > event.max_team_size)
		throw new BadRequestException(
			`This event requires ${event.min_team_size} to ${event.max_team_size} team members`,
		);
	if (new Set(memberUserIds).size !== memberUserIds.length)
		throw new BadRequestException('A team member is selected more than once');
	const position = body.position;
	const area = body.area;
	if (!position || typeof position !== 'object' || !area || typeof area !== 'object')
		throw new BadRequestException('Enter a warp position and two corner chunks');
	const point = position as Record<string, unknown>;
	const chunks = area as Record<string, unknown>;
	const dimension = nonempty(point.dimension, 'a dimension');
	if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(dimension))
		throw new BadRequestException('Enter a valid Minecraft dimension');
	const blockX = integer(point.x, 'warp X');
	const blockY = integer(point.y, 'warp Y');
	const blockZ = integer(point.z, 'warp Z');
	const yaw = finite(point.yaw, 'yaw');
	const pitch = finite(point.pitch, 'pitch');
	if (pitch < -90 || pitch > 90)
		throw new BadRequestException('Pitch must be between -90 and 90');
	const firstX = integer(chunks.firstX, 'first chunk X');
	const firstZ = integer(chunks.firstZ, 'first chunk Z');
	const secondX = integer(chunks.secondX, 'second chunk X');
	const secondZ = integer(chunks.secondZ, 'second chunk Z');
	const minX = Math.min(firstX, secondX);
	const maxX = Math.max(firstX, secondX);
	const minZ = Math.min(firstZ, secondZ);
	const maxZ = Math.max(firstZ, secondZ);
	if (
		Math.floor(blockX / 16) < minX ||
		Math.floor(blockX / 16) > maxX ||
		Math.floor(blockZ / 16) < minZ ||
		Math.floor(blockZ / 16) > maxZ
	)
		throw new BadRequestException('The warp position must be inside the selected chunks');
	return {
		memberUserIds,
		values: {
			title,
			description,
			images_json: JSON.stringify(images),
			dimension,
			block_x: blockX,
			block_y: blockY,
			block_z: blockZ,
			yaw,
			pitch,
			min_chunk_x: minX,
			max_chunk_x: maxX,
			min_chunk_z: minZ,
			max_chunk_z: maxZ,
		},
	};
}

@Injectable()
export class BuildEventsService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	private event(idInput: string | number) {
		const event = this.database.connection
			.select()
			.from(buildEvents)
			.where(eq(buildEvents.id, positiveId(idInput)))
			.get();
		if (!event) throw new NotFoundException('Build event not found');
		return event;
	}

	private submission(eventId: number, idInput: string | number) {
		const submission = this.database.connection
			.select()
			.from(buildSubmissions)
			.where(
				and(
					eq(buildSubmissions.id, positiveId(idInput)),
					eq(buildSubmissions.event_id, eventId),
				),
			)
			.get();
		if (!submission) throw new NotFoundException('Build submission not found');
		return submission;
	}

	private summary(event: Event) {
		return {
			id: event.id,
			title: event.title,
			shortDescription: event.short_description,
			description: event.description,
			status: buildStage(event),
			startsAtUnixMs: event.starts_at_unix_ms,
			buildEndsAtUnixMs: event.build_ends_at_unix_ms,
			votingStartsAtUnixMs: event.voting_starts_at_unix_ms,
			votingEndsAtUnixMs: event.voting_ends_at_unix_ms,
			warpEndsAtUnixMs: event.warp_ends_at_unix_ms,
			minTeamSize: event.min_team_size,
			maxTeamSize: event.max_team_size,
			resultsRevealed: event.results_revealed === 1,
		};
	}

	list() {
		return this.database.connection
			.select()
			.from(buildEvents)
			.orderBy(buildEvents.starts_at_unix_ms)
			.all()
			.map((event) => this.summary(event));
	}

	detail(idInput: string, viewerUserId?: number, admin = false) {
		const event = this.event(idInput);
		const stage = buildStage(event);
		const categories = JSON.parse(event.categories_json) as BuildCategory[];
		const submissions = this.database.connection
			.select()
			.from(buildSubmissions)
			.where(eq(buildSubmissions.event_id, event.id))
			.all();
		const members = this.database.connection
			.select()
			.from(buildSubmissionMembers)
			.where(eq(buildSubmissionMembers.event_id, event.id))
			.all();
		const votes = this.database.connection
			.select()
			.from(buildVotes)
			.all()
			.filter((vote) =>
				submissions.some((submission) => submission.id === vote.submission_id),
			);
		const globalExclusions = new Set(
			this.database.connection
				.select()
				.from(buildVoterExclusions)
				.where(eq(buildVoterExclusions.event_id, event.id))
				.all()
				.map((row) => row.voter_user_id),
		);
		// ponytail: full user/profile scans are fine for the current server size; query by IDs if this grows large.
		const people = new Map(
			this.database.connection
				.select()
				.from(users)
				.all()
				.map((user) => [user.id, user]),
		);
		const profiles = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		const person = (userId: number) => {
			const user = people.get(userId);
			if (!user)
				return {
					id: userId,
					name: 'Unknown player',
					color: '#ffffff',
					avatarUrl: null,
					pronouns: '',
					isCommittee: false,
					customEmojis: [],
				};
			const profile = profiles.get(userId);
			return {
				id: userId,
				name: user.minecraft_username,
				color: effectivePlayerColor(user.minecraft_uuid, profile?.color_hex),
				avatarUrl: playerAvatarUrl(user.minecraft_uuid),
				pronouns: profile?.pronouns ?? '',
				isCommittee: user.is_committee === 1 || user.is_super_admin === 1,
				customEmojis: customPlayerEmojis(profile?.custom_emojis_json),
			};
		};
		const ranked = submissions.map((submission) => {
			const team = members.filter((member) => member.submission_id === submission.id);
			const teamIds = new Set(team.map((member) => member.user_id));
			const submissionVotes = votes.filter((vote) => vote.submission_id === submission.id);
			const counted = submissionVotes.filter(
				(vote) =>
					vote.excluded === 0 &&
					!globalExclusions.has(vote.voter_user_id) &&
					!teamIds.has(vote.voter_user_id),
			);
			const score = buildScore(
				categories,
				counted.map((vote) => ({
					scores: JSON.parse(vote.scores_json) as Record<string, number>,
				})),
			);
			const own = viewerUserId !== undefined && teamIds.has(viewerUserId);
			const voted =
				viewerUserId !== undefined &&
				submissionVotes.some((vote) => vote.voter_user_id === viewerUserId);
			return {
				id: submission.id,
				title: submission.title,
				titleWordLengths: submission.title.split(/\s+/).map((word) => word.length),
				description: submission.description,
				images: (JSON.parse(submission.images_json) as string[]).map((url) => ({
					shareUrl: url,
					imageUrl: driveImageUrl(url),
				})),
				position: {
					dimension: submission.dimension,
					x: submission.block_x,
					y: submission.block_y,
					z: submission.block_z,
					yaw: submission.yaw,
					pitch: submission.pitch,
				},
				area: {
					firstX: submission.min_chunk_x,
					firstZ: submission.min_chunk_z,
					secondX: submission.max_chunk_x,
					secondZ: submission.max_chunk_z,
				},
				team: team.map((member) => person(member.user_id)),
				memberUserIds: team.map((member) => member.user_id),
				own,
				voted,
				canWarp:
					(stage === 'visiting' &&
						(admin || submission.post_voting_warp_enabled === 1)) ||
					(stage === 'voting' && !own && !voted),
				postVotingWarpEnabled: submission.post_voting_warp_enabled === 1,
				publicRankHidden: submission.public_rank_hidden === 1,
				voteCount: counted.length,
				score,
				rank: null as number | null,
				votes: submissionVotes.map((vote) => ({
					voter: person(vote.voter_user_id),
					scores: JSON.parse(vote.scores_json) as Record<string, number>,
					castAtUnixMs: vote.cast_at_unix_ms,
					excluded: vote.excluded === 1,
					globallyExcluded: globalExclusions.has(vote.voter_user_id),
					selfVote: teamIds.has(vote.voter_user_id),
				})),
			};
		});
		const byScore = [...ranked].sort(
			(a, b) => (b.score?.total ?? -Infinity) - (a.score?.total ?? -Infinity),
		);
		for (let i = 0; i < byScore.length;) {
			const current = byScore[i];
			if (!current) break;
			let end = i;
			while (end + 1 < byScore.length) {
				const next = byScore[end + 1];
				if (
					!next ||
					!(
						(next.score === null && current.score === null) ||
						(next.score !== null &&
							current.score !== null &&
							Math.abs(next.score.total - current.score.total) < 1e-9)
					)
				)
					break;
				end++;
			}
			for (let cursor = i; cursor <= end; cursor++) {
				const entry = byScore[cursor];
				if (entry) entry.rank = current.score ? i + 1 : null;
			}
			i = end + 1;
		}
		const showResults =
			admin || (event.results_revealed === 1 && (stage === 'visiting' || stage === 'ended'));
		const revealSubmissions =
			admin || stage === 'voting' || stage === 'visiting' || stage === 'ended';
		const publicOrder =
			stage === 'voting'
				? [...ranked].sort(
						(a, b) =>
							Number(a.voted) - Number(b.voted) ||
							a.voteCount - b.voteCount ||
							a.id - b.id,
					)
				: ranked;
		const hidden = ranked.filter((submission) => submission.publicRankHidden);
		if (showResults && !admin) {
			for (let i = hidden.length - 1; i > 0; i--) {
				const j = Math.floor(Math.random() * (i + 1));
				const first = hidden[i];
				const second = hidden[j];
				if (first && second) {
					hidden[i] = second;
					hidden[j] = first;
				}
			}
		}
		const visible = admin
			? byScore
			: showResults
				? [...byScore.filter((submission) => !submission.publicRankHidden), ...hidden]
				: publicOrder;
		const eligible = ranked.filter((submission) => !submission.own);
		return {
			...this.summary(event),
			categories: revealSubmissions ? categories : [],
			uniquePlayerCount:
				stage === 'upcoming' ? 0 : new Set(members.map((member) => member.user_id)).size,
			progress: {
				voted: eligible.filter((submission) => submission.voted).length,
				total: eligible.length,
			},
			submissions: (stage === 'upcoming' && !admin ? [] : visible).map((submission) => ({
				id: submission.id,
				title: revealSubmissions ? submission.title : null,
				titleWordLengths: submission.titleWordLengths,
				description: revealSubmissions ? submission.description : null,
				images: revealSubmissions ? submission.images : [],
				position: revealSubmissions ? submission.position : null,
				area: admin ? submission.area : undefined,
				team: submission.team,
				memberUserIds: admin ? submission.memberUserIds : undefined,
				own: submission.own,
				voted: submission.voted,
				canWarp: submission.canWarp,
				postVotingWarpEnabled: admin ? submission.postVotingWarpEnabled : undefined,
				publicRankHidden: admin ? submission.publicRankHidden : undefined,
				score:
					showResults && (admin || !submission.publicRankHidden)
						? submission.score
						: undefined,
				rank: showResults && (admin || hidden.length === 0) ? submission.rank : undefined,
				voteCount: admin ? submission.voteCount : undefined,
				votes: admin ? submission.votes : undefined,
			})),
		};
	}

	create(body: Record<string, unknown>) {
		const values = eventInput(body);
		const created = this.database.connection
			.insert(buildEvents)
			.values(values)
			.returning({ id: buildEvents.id })
			.get();
		return this.detail(String(created.id), undefined, true);
	}

	update(idInput: string, body: Record<string, unknown>) {
		const event = this.event(idInput);
		const values = eventInput(body);
		const existingCategories = event.categories_json;
		const hasVotes = this.database.connection
			.select()
			.from(buildVotes)
			.all()
			.some((vote) =>
				this.database.connection
					.select()
					.from(buildSubmissions)
					.where(
						and(
							eq(buildSubmissions.id, vote.submission_id),
							eq(buildSubmissions.event_id, event.id),
						),
					)
					.get(),
			);
		if (
			values.categories_json !== existingCategories &&
			(Date.now() >= event.voting_starts_at_unix_ms ||
				Date.now() >= values.voting_starts_at_unix_ms ||
				hasVotes)
		)
			throw new ConflictException('Voting categories are locked once voting starts');
		const teamSizes = this.database.connection
			.select()
			.from(buildSubmissions)
			.where(eq(buildSubmissions.event_id, event.id))
			.all()
			.map(
				(submission) =>
					this.database.connection
						.select()
						.from(buildSubmissionMembers)
						.where(eq(buildSubmissionMembers.submission_id, submission.id))
						.all().length,
			);
		if (teamSizes.some((size) => size < values.min_team_size || size > values.max_team_size))
			throw new ConflictException(
				'The new team size would invalidate an existing submission',
			);
		this.database.connection
			.update(buildEvents)
			.set(values)
			.where(eq(buildEvents.id, event.id))
			.run();
		return this.detail(String(event.id), undefined, true);
	}

	remove(idInput: string) {
		const event = this.event(idInput);
		this.database.connection.delete(buildEvents).where(eq(buildEvents.id, event.id)).run();
		return { removed: true };
	}

	reveal(idInput: string, revealed: unknown) {
		const event = this.event(idInput);
		if (typeof revealed !== 'boolean')
			throw new BadRequestException('Choose whether results are public');
		if (revealed && Date.now() < event.voting_ends_at_unix_ms)
			throw new ConflictException('Public results can be revealed after voting closes');
		this.database.connection
			.update(buildEvents)
			.set({ results_revealed: revealed ? 1 : 0 })
			.where(eq(buildEvents.id, event.id))
			.run();
		return this.detail(String(event.id), undefined, true);
	}

	saveSubmission(
		eventIdInput: string,
		body: Record<string, unknown>,
		submissionIdInput?: string,
	) {
		const event = this.event(eventIdInput);
		const existing = submissionIdInput ? this.submission(event.id, submissionIdInput) : null;
		const { values, memberUserIds } = submissionInput(body, event);
		const selectedUsers = this.database.connection
			.select()
			.from(users)
			.all()
			.filter((user) => memberUserIds.includes(user.id));
		if (
			selectedUsers.length !== memberUserIds.length ||
			selectedUsers.some((user) => !user.minecraft_uuid)
		)
			throw new BadRequestException('Every team member needs a linked Minecraft account');
		const occupied = this.database.connection
			.select()
			.from(buildSubmissionMembers)
			.where(eq(buildSubmissionMembers.event_id, event.id))
			.all()
			.find(
				(member) =>
					memberUserIds.includes(member.user_id) && member.submission_id !== existing?.id,
			);
		if (occupied)
			throw new ConflictException(
				'A selected player is already on another team in this event',
			);
		const id = this.database.connection.transaction((tx) => {
			const submissionId = existing
				? existing.id
				: tx
						.insert(buildSubmissions)
						.values({ event_id: event.id, ...values })
						.returning({ id: buildSubmissions.id })
						.get().id;
			if (existing)
				tx.update(buildSubmissions)
					.set(values)
					.where(eq(buildSubmissions.id, submissionId))
					.run();
			tx.delete(buildSubmissionMembers)
				.where(eq(buildSubmissionMembers.submission_id, submissionId))
				.run();
			for (const userId of memberUserIds)
				tx.insert(buildSubmissionMembers)
					.values({ event_id: event.id, submission_id: submissionId, user_id: userId })
					.run();
			return submissionId;
		});
		return this.detail(String(event.id), undefined, true).submissions.find(
			(entry) => entry.id === id,
		);
	}

	removeSubmission(eventIdInput: string, submissionIdInput: string) {
		const event = this.event(eventIdInput);
		const submission = this.submission(event.id, submissionIdInput);
		this.database.connection
			.delete(buildSubmissions)
			.where(eq(buildSubmissions.id, submission.id))
			.run();
		return { removed: true };
	}

	private linkedUser(userId: number) {
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!user?.minecraft_uuid) throw new ForbiddenException('Link a Minecraft account first');
		return user;
	}

	private async position(username: string) {
		let position: Position;
		try {
			position = await this.minecraft.gameplay<Position>('GetCurrentClaimChunk', {
				minecraft_username: username,
			});
		} catch {
			throw new BadGatewayException('The main Minecraft server is unavailable');
		}
		if (!position.online) throw new ConflictException('Join the main Minecraft server first');
		return position;
	}

	async adminPosition() {
		const position = await this.position('MMU_Admin');
		return {
			dimension: position.dimension,
			x: position.block_x,
			y: position.block_y,
			z: position.block_z,
			chunkX: position.chunk_x,
			chunkZ: position.chunk_z,
			yaw: position.yaw,
			pitch: position.pitch,
		};
	}

	async vote(
		eventIdInput: string,
		submissionIdInput: string,
		voterUserId: number,
		scoresInput: unknown,
	) {
		const event = this.event(eventIdInput);
		if (buildStage(event) !== 'voting') throw new ConflictException('Voting is not open');
		const submission = this.submission(event.id, submissionIdInput);
		const user = this.linkedUser(voterUserId);
		const own = this.database.connection
			.select()
			.from(buildSubmissionMembers)
			.where(
				and(
					eq(buildSubmissionMembers.submission_id, submission.id),
					eq(buildSubmissionMembers.user_id, voterUserId),
				),
			)
			.get();
		if (own) throw new ForbiddenException('You cannot vote for your own build');
		if (
			this.database.connection
				.select()
				.from(buildVotes)
				.where(
					and(
						eq(buildVotes.submission_id, submission.id),
						eq(buildVotes.voter_user_id, voterUserId),
					),
				)
				.get()
		)
			throw new ConflictException('You have already voted for this build');
		const categories = JSON.parse(event.categories_json) as BuildCategory[];
		if (!scoresInput || typeof scoresInput !== 'object' || Array.isArray(scoresInput))
			throw new BadRequestException('Rate every voting category');
		const scores = scoresInput as Record<string, unknown>;
		if (
			Object.keys(scores).length !== categories.length ||
			categories.some(
				(category) =>
					!Number.isSafeInteger(scores[category.id]) ||
					(scores[category.id] as number) < category.min ||
					(scores[category.id] as number) > category.max,
			)
		)
			throw new BadRequestException('Rate every category with a whole number in its range');
		const position = await this.position(user.minecraft_username);
		if (
			!insideBuild(
				{
					dimension: position.dimension,
					chunkX: position.chunk_x,
					chunkZ: position.chunk_z,
				},
				submission,
			)
		)
			throw new ForbiddenException('Visit this build in Minecraft before voting');
		if (buildStage(event) !== 'voting') throw new ConflictException('Voting has closed');
		this.submission(event.id, submission.id);
		if (
			this.database.connection
				.select()
				.from(buildSubmissionMembers)
				.where(
					and(
						eq(buildSubmissionMembers.submission_id, submission.id),
						eq(buildSubmissionMembers.user_id, voterUserId),
					),
				)
				.get()
		)
			throw new ForbiddenException('You cannot vote for your own build');
		try {
			this.database.connection
				.insert(buildVotes)
				.values({
					submission_id: submission.id,
					voter_user_id: voterUserId,
					scores_json: JSON.stringify(scores),
					cast_at_unix_ms: Date.now(),
				})
				.run();
		} catch {
			throw new ConflictException('You have already voted for this build');
		}
		return { voted: true };
	}

	async eligibility(eventIdInput: string, submissionIdInput: string, userId: number) {
		const event = this.event(eventIdInput);
		const submission = this.submission(event.id, submissionIdInput);
		const user = this.linkedUser(userId);
		const position = await this.position(user.minecraft_username).catch((error: unknown) => {
			if (error instanceof ConflictException) return null;
			throw error;
		});
		const inside = Boolean(
			position &&
			insideBuild(
				{
					dimension: position.dimension,
					chunkX: position.chunk_x,
					chunkZ: position.chunk_z,
				},
				submission,
			),
		);
		const own = Boolean(
			this.database.connection
				.select()
				.from(buildSubmissionMembers)
				.where(
					and(
						eq(buildSubmissionMembers.submission_id, submission.id),
						eq(buildSubmissionMembers.user_id, userId),
					),
				)
				.get(),
		);
		const voted = Boolean(
			this.database.connection
				.select()
				.from(buildVotes)
				.where(
					and(
						eq(buildVotes.submission_id, submission.id),
						eq(buildVotes.voter_user_id, userId),
					),
				)
				.get(),
		);
		const visit = await this.visit(userId);
		const stage = buildStage(event);
		const committee = user.is_committee === 1 || user.is_super_admin === 1;
		return {
			inside,
			own,
			voted,
			canVote: stage === 'voting' && inside && !own && !voted,
			canWarp:
				(stage === 'visiting' &&
					(committee || submission.post_voting_warp_enabled === 1)) ||
				(stage === 'voting' && !own && !voted),
			canReturn: visit?.submission.id === submission.id,
		};
	}

	setVoteExclusion(
		eventIdInput: string,
		submissionIdInput: string,
		voterUserIdInput: unknown,
		excluded: unknown,
	) {
		const event = this.event(eventIdInput);
		const submission = this.submission(event.id, submissionIdInput);
		const voterUserId = integer(voterUserIdInput, 'voter');
		if (typeof excluded !== 'boolean')
			throw new BadRequestException('Choose whether to exclude this vote');
		const result = this.database.connection
			.update(buildVotes)
			.set({ excluded: excluded ? 1 : 0 })
			.where(
				and(
					eq(buildVotes.submission_id, submission.id),
					eq(buildVotes.voter_user_id, voterUserId),
				),
			)
			.run();
		if (!result.changes) throw new NotFoundException('Vote not found');
		return { excluded };
	}

	setVoterExclusion(eventIdInput: string, voterUserIdInput: string, excluded: unknown) {
		const event = this.event(eventIdInput);
		const voterUserId = positiveId(voterUserIdInput);
		if (typeof excluded !== 'boolean')
			throw new BadRequestException('Choose whether to exclude this voter');
		if (excluded)
			this.database.connection
				.insert(buildVoterExclusions)
				.values({ event_id: event.id, voter_user_id: voterUserId })
				.onConflictDoNothing()
				.run();
		else
			this.database.connection
				.delete(buildVoterExclusions)
				.where(
					and(
						eq(buildVoterExclusions.event_id, event.id),
						eq(buildVoterExclusions.voter_user_id, voterUserId),
					),
				)
				.run();
		return { excluded };
	}

	setPostVotingWarp(eventIdInput: string, submissionIdInput: string, enabled: unknown) {
		const event = this.event(eventIdInput);
		const submission = this.submission(event.id, submissionIdInput);
		if (typeof enabled !== 'boolean')
			throw new BadRequestException('Choose whether this build can be visited after voting');
		this.database.connection
			.update(buildSubmissions)
			.set({ post_voting_warp_enabled: enabled ? 1 : 0 })
			.where(eq(buildSubmissions.id, submission.id))
			.run();
		return { enabled };
	}

	setPublicRankHidden(eventIdInput: string, submissionIdInput: string, hidden: unknown) {
		const event = this.event(eventIdInput);
		const submission = this.submission(event.id, submissionIdInput);
		if (typeof hidden !== 'boolean')
			throw new BadRequestException("Choose whether this build's place is hidden");
		this.database.connection
			.update(buildSubmissions)
			.set({ public_rank_hidden: hidden ? 1 : 0 })
			.where(eq(buildSubmissions.id, submission.id))
			.run();
		return { hidden };
	}

	private async visit(userId: number) {
		const visit = this.database.connection
			.select()
			.from(buildVisits)
			.where(eq(buildVisits.user_id, userId))
			.get();
		if (!visit) return null;
		const submission = this.database.connection
			.select()
			.from(buildSubmissions)
			.where(eq(buildSubmissions.id, visit.submission_id))
			.get();
		if (!submission) return null;
		const user = this.linkedUser(userId);
		try {
			const position = await this.position(user.minecraft_username);
			if (
				insideBuild(
					{
						dimension: position.dimension,
						chunkX: position.chunk_x,
						chunkZ: position.chunk_z,
					},
					submission,
				)
			)
				return { visit, submission };
		} catch (error) {
			if (error instanceof BadGatewayException) throw error;
			return null;
		}
		this.database.connection.delete(buildVisits).where(eq(buildVisits.user_id, userId)).run();
		return null;
	}

	async visitStatus(userId: number) {
		const active = await this.visit(userId);
		return { submissionId: active?.submission.id ?? null, canReturn: Boolean(active) };
	}

	async warp(eventIdInput: string, submissionIdInput: string, userId: number) {
		const event = this.event(eventIdInput);
		const stage = buildStage(event);
		if (stage !== 'voting' && stage !== 'visiting')
			throw new ConflictException('Build warps are not open');
		const submission = this.submission(event.id, submissionIdInput);
		const user = this.linkedUser(userId);
		if (
			stage === 'visiting' &&
			submission.post_voting_warp_enabled !== 1 &&
			user.is_committee !== 1 &&
			user.is_super_admin !== 1
		)
			throw new ForbiddenException('Visits are not enabled for this build');
		if (stage === 'voting') {
			if (
				this.database.connection
					.select()
					.from(buildSubmissionMembers)
					.where(
						and(
							eq(buildSubmissionMembers.submission_id, submission.id),
							eq(buildSubmissionMembers.user_id, userId),
						),
					)
					.get()
			)
				throw new ForbiddenException('You cannot warp to your own build during voting');
			if (
				this.database.connection
					.select()
					.from(buildVotes)
					.where(
						and(
							eq(buildVotes.submission_id, submission.id),
							eq(buildVotes.voter_user_id, userId),
						),
					)
					.get()
			)
				throw new ForbiddenException('You already voted for this build');
		}
		if (await this.visit(userId))
			throw new ConflictException('Use the return warp before visiting another build');
		let result: WarpResult;
		try {
			result = await this.minecraft.gameplay<WarpResult>('TeleportBuildVisitor', {
				minecraft_username: user.minecraft_username,
				dimension: submission.dimension,
				x: submission.block_x + 0.5,
				y: submission.block_y,
				z: submission.block_z + 0.5,
				yaw: submission.yaw,
				pitch: submission.pitch,
			});
		} catch {
			throw new BadGatewayException('The main Minecraft server is unavailable');
		}
		if (!result.moved) throw new ConflictException(result.message || 'Warp failed');
		this.database.connection
			.insert(buildVisits)
			.values({
				user_id: userId,
				submission_id: submission.id,
				origin_dimension: result.origin_dimension,
				origin_x: result.origin_x,
				origin_y: result.origin_y,
				origin_z: result.origin_z,
				origin_yaw: result.origin_yaw,
				origin_pitch: result.origin_pitch,
			})
			.onConflictDoUpdate({
				target: buildVisits.user_id,
				set: {
					submission_id: submission.id,
					origin_dimension: result.origin_dimension,
					origin_x: result.origin_x,
					origin_y: result.origin_y,
					origin_z: result.origin_z,
					origin_yaw: result.origin_yaw,
					origin_pitch: result.origin_pitch,
				},
			})
			.run();
		return { warped: true, submissionId: submission.id };
	}

	async warpBack(userId: number) {
		const active = await this.visit(userId);
		if (!active) throw new ConflictException('The return warp is no longer available');
		const user = this.linkedUser(userId);
		const { visit, submission } = active;
		let result: WarpResult;
		try {
			result = await this.minecraft.gameplay<WarpResult>('TeleportBuildVisitor', {
				minecraft_username: user.minecraft_username,
				dimension: visit.origin_dimension,
				x: visit.origin_x,
				y: visit.origin_y,
				z: visit.origin_z,
				yaw: visit.origin_yaw,
				pitch: visit.origin_pitch,
				check_current_area: true,
				current_dimension: submission.dimension,
				min_chunk_x: submission.min_chunk_x,
				max_chunk_x: submission.max_chunk_x,
				min_chunk_z: submission.min_chunk_z,
				max_chunk_z: submission.max_chunk_z,
			});
		} catch {
			throw new BadGatewayException('The main Minecraft server is unavailable');
		}
		if (!result.moved) {
			if (result.left_area)
				this.database.connection
					.delete(buildVisits)
					.where(eq(buildVisits.user_id, userId))
					.run();
			throw new ConflictException(result.message || 'Return warp failed');
		}
		this.database.connection.delete(buildVisits).where(eq(buildVisits.user_id, userId)).run();
		return { returned: true };
	}
}
