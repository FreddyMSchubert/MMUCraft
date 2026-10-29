'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useCallback, useEffect, useRef, useState, type SyntheticEvent } from 'react';
import { createPortal } from 'react-dom';
import { PlayerName, type PlayerEmoji } from './player-name';
import { useSiteAlert } from './site-alert';
import { EventDescription, ObfuscatedEventName } from './surprising-saturday-tab';

export type BuildStage = 'upcoming' | 'building' | 'preparing' | 'voting' | 'visiting' | 'ended';

export interface BuildEventSummary {
	id: number;
	title: string;
	shortDescription: string;
	description: string;
	status: BuildStage;
	startsAtUnixMs: number;
	buildEndsAtUnixMs: number;
	votingStartsAtUnixMs: number;
	votingEndsAtUnixMs: number;
	warpEndsAtUnixMs: number;
	minTeamSize: number;
	maxTeamSize: number;
	resultsRevealed: boolean;
}

export interface BuildCategory {
	id: string;
	name: string;
	description: string;
	min: number;
	max: number;
}

export interface BuildMember {
	id: number;
	name: string;
	color: string;
	avatarUrl: string | null;
	pronouns: string;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
}

export interface BuildSubmission {
	id: number;
	title: string | null;
	titleWordLengths: number[];
	description: string | null;
	images: { shareUrl: string; imageUrl: string }[];
	position: {
		dimension: string;
		x: number;
		y: number;
		z: number;
		yaw: number;
		pitch: number;
	} | null;
	area?: { firstX: number; firstZ: number; secondX: number; secondZ: number };
	team: BuildMember[];
	memberUserIds?: number[];
	own: boolean;
	voted: boolean;
	canWarp: boolean;
	postVotingWarpEnabled?: boolean;
	score?: { averages: { id: string; average: number }[]; total: number } | null;
	rank?: number | null;
	voteCount?: number;
	votes?: {
		voter: BuildMember;
		scores: Record<string, number>;
		castAtUnixMs: number;
		excluded: boolean;
		globallyExcluded: boolean;
		selfVote: boolean;
	}[];
}

export interface BuildEventDetail extends BuildEventSummary {
	categories: BuildCategory[];
	uniquePlayerCount: number;
	progress: { voted: number; total: number };
	submissions: BuildSubmission[];
}

function responseMessage(body: unknown, fallback: string) {
	if (body && typeof body === 'object' && 'message' in body) {
		const message = (body as { message?: unknown }).message;
		if (typeof message === 'string') return message;
	}
	return fallback;
}

async function loadBuildEvent(eventId: number): Promise<BuildEventDetail> {
	const response = await fetch('/api/build-events/' + String(eventId), {
		cache: 'no-store',
	});
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(responseMessage(body, 'Could not load the build event'));
	return body as BuildEventDetail;
}

function remainingTime(target: number, now: number) {
	const total = Math.max(0, Math.ceil((target - now) / 1000));
	const days = Math.floor(total / 86400);
	const hours = Math.floor((total % 86400) / 3600);
	const minutes = Math.floor((total % 3600) / 60);
	const seconds = total % 60;
	return (
		(days ? String(days) + 'd ' : '') +
		[hours, minutes, seconds].map((part) => String(part).padStart(2, '0')).join(':')
	);
}

function buildCoordinates(position: NonNullable<BuildSubmission['position']>) {
	return `${position.dimension} at ${position.x}, ${position.y}, ${position.z}`;
}

export function BuildTeam({ members }: { members: BuildMember[] }) {
	return (
		<div className="buildTeam">
			<span>By</span>
			{members.map((member) => (
				<Link
					className="eventEarningPlayer"
					key={member.id}
					href={'/play/players/' + encodeURIComponent(member.name)}
				>
					{member.avatarUrl ? (
						<Image unoptimized src={member.avatarUrl} alt="" width={28} height={28} />
					) : (
						<span className="eventEarningHeadFallback">{member.name.charAt(0)}</span>
					)}
					<PlayerName
						name={member.name}
						color={member.color}
						isCommittee={member.isCommittee}
						customEmojis={member.customEmojis}
					/>
					{member.pronouns && (
						<span className="eventPlayerPronouns"> ({member.pronouns})</span>
					)}
				</Link>
			))}
		</div>
	);
}

export function BuildGallery({
	images,
	title,
	paused = false,
}: {
	images: BuildSubmission['images'];
	title: string;
	paused?: boolean;
}) {
	const [index, setIndex] = useState(0);
	const [imageStatus, setImageStatus] = useState<Record<string, 'loaded' | 'error'>>({});
	const [fullyVisible, setFullyVisible] = useState(false);
	const galleryRef = useRef<HTMLDivElement>(null);
	const imageUrls = images.map((image) => image.imageUrl).join('\n');
	useEffect(() => {
		let inViewport = false;
		const updateVisibility = () => {
			setFullyVisible(inViewport && document.visibilityState === 'visible');
		};
		const observer = new IntersectionObserver(
			([entry]) => {
				inViewport = entry.intersectionRatio === 1;
				updateVisibility();
			},
			{ threshold: 1 },
		);
		if (galleryRef.current) observer.observe(galleryRef.current);
		document.addEventListener('visibilitychange', updateVisibility);
		return () => {
			observer.disconnect();
			document.removeEventListener('visibilitychange', updateVisibility);
		};
	}, []);
	useEffect(() => {
		const urls = imageUrls.split('\n').filter(Boolean);
		const preloaders = urls.map((url) => {
			const preload = new window.Image();
			preload.onload = () => {
				setImageStatus((current) => ({ ...current, [url]: 'loaded' }));
			};
			preload.onerror = () => {
				setImageStatus((current) => ({ ...current, [url]: 'error' }));
			};
			preload.src = url;
			return preload;
		});
		return () => {
			for (const preload of preloaders) {
				preload.onload = null;
				preload.onerror = null;
			}
		};
	}, [imageUrls]);
	useEffect(() => {
		if (
			images.length < 2 ||
			paused ||
			!fullyVisible ||
			window.matchMedia('(prefers-reduced-motion: reduce)').matches
		)
			return;
		const timer = window.setTimeout(
			() => {
				setIndex((current) => (current + 1) % images.length);
			},
			4500 + Math.random() * 3000,
		);
		return () => {
			window.clearTimeout(timer);
		};
	}, [images.length, index, paused, fullyVisible]);
	const visibleIndex = images.length > 0 ? index % images.length : 0;
	const current = images.at(visibleIndex);
	const currentStatus = current ? imageStatus[current.imageUrl] : null;
	return (
		<div ref={galleryRef} className="buildGallery" aria-label={'Screenshots for ' + title}>
			{current ? (
				<Image
					key={current.imageUrl}
					unoptimized
					fill
					loading="eager"
					className={
						currentStatus === 'loaded'
							? 'buildGalleryImageLoaded'
							: 'buildGalleryImageLoading'
					}
					onLoad={() => {
						setImageStatus((state) => ({ ...state, [current.imageUrl]: 'loaded' }));
					}}
					onError={() => {
						setImageStatus((state) => ({ ...state, [current.imageUrl]: 'error' }));
					}}
					sizes="(max-width: 850px) 100vw, 50vw"
					src={current.imageUrl}
					alt={
						'Screenshot ' +
						String(visibleIndex + 1) +
						' of ' +
						String(images.length) +
						' for ' +
						title
					}
				/>
			) : (
				<div className="buildGalleryEmpty" aria-label="No screenshots yet" />
			)}
			{current && currentStatus !== 'loaded' && (
				<div className="buildGalleryLoading" role="status">
					{currentStatus === 'error' ? (
						'Screenshot unavailable'
					) : (
						<>
							<span className="buildGallerySpinner" aria-hidden="true" />
							Loading screenshot…
						</>
					)}
				</div>
			)}
			{images.length > 1 && (
				<>
					<button
						className="buildGalleryArrow buildGalleryPrevious"
						type="button"
						aria-label="Previous screenshot"
						onClick={() => {
							setIndex(
								(currentIndex) =>
									(currentIndex + images.length - 1) % images.length,
							);
						}}
					>
						<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
							<path d="m15 5-7 7 7 7" />
						</svg>
					</button>
					<button
						className="buildGalleryArrow buildGalleryNext"
						type="button"
						aria-label="Next screenshot"
						onClick={() => {
							setIndex((currentIndex) => (currentIndex + 1) % images.length);
						}}
					>
						<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
							<path d="m9 5 7 7-7 7" />
						</svg>
					</button>
					<div
						className="buildGalleryDots"
						aria-label={
							'Screenshot ' +
							String(visibleIndex + 1) +
							' of ' +
							String(images.length)
						}
					>
						{images.map((_, dot) => (
							<button
								key={dot}
								type="button"
								className={dot === visibleIndex ? 'active' : ''}
								aria-label={'Show screenshot ' + String(dot + 1)}
								aria-current={dot === visibleIndex ? 'true' : undefined}
								onClick={() => {
									setIndex(dot);
								}}
							/>
						))}
					</div>
				</>
			)}
		</div>
	);
}

interface Eligibility {
	inside: boolean;
	own: boolean;
	voted: boolean;
	canVote: boolean;
	canWarp: boolean;
	canReturn: boolean;
}

export function BuildSubmissionDialog({
	eventId,
	stage,
	submission,
	categories,
	onClose,
	onUpdated,
}: {
	eventId: number;
	stage: BuildStage;
	submission: BuildSubmission;
	categories: BuildCategory[];
	onClose: () => void;
	onUpdated: () => Promise<void>;
}) {
	const [eligibility, setEligibility] = useState<Eligibility | null>(null);
	const [scores, setScores] = useState<Record<string, number>>(() =>
		Object.fromEntries(
			categories.map((category) => [
				category.id,
				Math.floor((category.min + category.max) / 2),
			]),
		),
	);
	const { confirm } = useSiteAlert();
	const [error, setError] = useState('');
	const [busy, setBusy] = useState(false);
	const [paused, setPaused] = useState(false);
	const [thanks, setThanks] = useState(false);
	const refreshEligibility = useCallback(async () => {
		try {
			const response = await fetch(
				'/api/build-events/' +
					String(eventId) +
					'/submissions/' +
					String(submission.id) +
					'/eligibility',
				{ cache: 'no-store' },
			);
			if (response.ok) setEligibility((await response.json()) as Eligibility);
			else setEligibility(null);
		} catch {
			setEligibility(null);
		}
	}, [eventId, submission.id]);
	useEffect(() => {
		const initial = window.setTimeout(() => void refreshEligibility(), 0);
		const timer = window.setInterval(() => void refreshEligibility(), 5000);
		return () => {
			window.clearTimeout(initial);
			window.clearInterval(timer);
		};
	}, [refreshEligibility]);
	useEffect(() => {
		const closeOnEscape = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onClose();
		};
		window.addEventListener('keydown', closeOnEscape);
		return () => {
			window.removeEventListener('keydown', closeOnEscape);
		};
	}, [onClose]);

	async function action(path: string, fallback: string) {
		setBusy(true);
		setError('');
		try {
			const response = await fetch(path, { method: 'POST' });
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(responseMessage(body, fallback));
			await refreshEligibility();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : fallback);
		} finally {
			setBusy(false);
		}
	}

	async function vote(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		if (
			!(await confirm({
				title: 'Submit your final vote?',
				message: (
					<div className="buildVoteConfirmation">
						<p>Your scores for {submission.title}:</p>
						<ul>
							{categories.map((category) => (
								<li key={category.id}>
									{category.name}:{' '}
									<strong>
										{scores[category.id]} / {category.max}
									</strong>
								</li>
							))}
						</ul>
						<p>You cannot change this vote afterwards.</p>
					</div>
				),
				confirmLabel: 'Submit final vote',
			}))
		)
			return;
		setBusy(true);
		setError('');
		try {
			const response = await fetch(
				'/api/build-events/' +
					String(eventId) +
					'/submissions/' +
					String(submission.id) +
					'/vote',
				{
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ scores }),
				},
			);
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(responseMessage(body, 'Could not save your vote'));
			setThanks(true);
			await refreshEligibility();
			await onUpdated();
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Could not save your vote');
		} finally {
			setBusy(false);
		}
	}

	const title = submission.title ?? 'Build submission';
	const canVisit = stage === 'voting' || stage === 'visiting';
	return createPortal(
		<div
			className="buildDialogBackdrop"
			onMouseDown={(event) => {
				if (event.target === event.currentTarget) onClose();
			}}
		>
			<section
				className="buildDialog"
				role="dialog"
				aria-modal="true"
				aria-label={title}
				onMouseEnter={() => {
					setPaused(true);
				}}
				onMouseLeave={() => {
					setPaused(false);
				}}
				onFocusCapture={() => {
					setPaused(true);
				}}
				onBlurCapture={(event) => {
					if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false);
				}}
			>
				<button
					className="buildDialogClose"
					type="button"
					onClick={onClose}
					aria-label="Close build details"
				>
					<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
						<path d="m6 6 12 12M18 6 6 18" />
					</svg>
				</button>
				<BuildGallery images={submission.images} title={title} paused={paused} />
				<div className="buildDialogContent">
					<div className="buildDialogHeading">
						<div>
							<h3>
								{submission.rank != null && (
									<span className="buildRank">#{submission.rank} — </span>
								)}
								{title}
							</h3>
						</div>
						{submission.score && (
							<strong>{submission.score.total.toFixed(2)} pts</strong>
						)}
					</div>
					<BuildTeam members={submission.team} />
					{submission.score && (
						<div className="buildPublicScores">
							{submission.score.averages.map((average) => (
								<span key={average.id}>
									{categories.find((category) => category.id === average.id)
										?.name ?? average.id}
									: {average.average.toFixed(3)}
								</span>
							))}
						</div>
					)}
					<p className="buildPlainDescription">{submission.description}</p>
					{stage === 'ended' && submission.position && (
						<p className="buildCoordinates">
							Warping has closed. Visit {buildCoordinates(submission.position)}, or
							ask a builder to show you around.
						</p>
					)}
					{stage !== 'ended' && submission.position && (
						<p className="buildCoordinates">
							Coordinates: {buildCoordinates(submission.position)}
						</p>
					)}
					{canVisit && (
						<div className="buildVisitActions">
							{eligibility?.canReturn && (
								<p className="buildVisitWarning" role="alert">
									Please don&apos;t go away from the build, or you won&apos;t be
									able to warp back again.
								</p>
							)}
							{eligibility?.canReturn ? (
								<button
									type="button"
									disabled={busy}
									onClick={() =>
										void action(
											'/api/build-events/me/return',
											'Could not return to your previous position',
										)
									}
								>
									Warp back
								</button>
							) : eligibility?.canWarp ? (
								<button
									type="button"
									disabled={busy}
									onClick={() =>
										void action(
											'/api/build-events/' +
												String(eventId) +
												'/submissions/' +
												String(submission.id) +
												'/warp',
											'Could not warp to this build',
										)
									}
								>
									Warp to build
								</button>
							) : stage === 'voting' && submission.own ? (
								<p>You cannot warp to your own build during voting.</p>
							) : stage === 'voting' && submission.voted ? (
								<p>You have already voted for this build.</p>
							) : stage === 'visiting' && !submission.canWarp ? (
								<p>Visits are not enabled for this build.</p>
							) : null}
						</div>
					)}
					{stage === 'voting' && eligibility?.own && (
						<p className="buildVoteNotice">You cannot vote for your own build, lol.</p>
					)}
					{stage === 'voting' && (thanks || eligibility?.voted) && !eligibility?.own && (
						<p className="buildVoteThanks" role="status">
							Thank you for voting.
						</p>
					)}
					{stage === 'voting' && eligibility?.canVote && !thanks && (
						<form className="buildVoteForm" onSubmit={(event) => void vote(event)}>
							<h4>Rate this build</h4>
							<p>
								Please try to be as impartial as possible when voting for the
								builds.
							</p>
							{categories.map((category) => (
								<label key={category.id}>
									<span>
										<strong>{category.name}</strong>
										<em>
											{String(scores[category.id]) +
												' / ' +
												String(category.max)}
										</em>
									</span>
									{category.description && <small>{category.description}</small>}
									<input
										type="range"
										min={category.min}
										max={category.max}
										step={1}
										value={
											scores[category.id] ??
											Math.floor((category.min + category.max) / 2)
										}
										onChange={(event) => {
											setScores((current) => ({
												...current,
												[category.id]: Number(event.target.value),
											}));
										}}
									/>
									<small>
										{category.min} to {category.max}
									</small>
								</label>
							))}
							<button type="submit" disabled={busy}>
								Submit final vote
							</button>
						</form>
					)}
					{error && (
						<p className="authError" role="alert">
							{error}
						</p>
					)}
				</div>
			</section>
		</div>,
		document.body,
	);
}

function BuildCard({
	eventId,
	stage,
	submission,
	categories,
	onUpdated,
}: {
	eventId: number;
	stage: BuildStage;
	submission: BuildSubmission;
	categories: BuildCategory[];
	onUpdated: () => Promise<void>;
}) {
	const [open, setOpen] = useState(false);
	const [paused, setPaused] = useState(false);
	const hidden = submission.title === null;
	return (
		<>
			<article
				className={'buildCard' + (hidden ? ' buildCardHidden' : '')}
				onMouseEnter={() => {
					setPaused(true);
				}}
				onMouseLeave={() => {
					setPaused(false);
				}}
				onFocusCapture={() => {
					setPaused(true);
				}}
				onBlurCapture={(event) => {
					if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false);
				}}
			>
				{!hidden && (
					<BuildGallery
						images={submission.images}
						title={submission.title ?? 'Build'}
						paused={paused || open}
					/>
				)}
				<div className="buildCardText">
					<div>
						{hidden ? (
							<h5>
								<ObfuscatedEventName
									wordLengths={submission.titleWordLengths}
									label="Build title hidden until voting opens"
								/>
							</h5>
						) : (
							<h5>
								{submission.rank != null && (
									<span className="buildRank">#{submission.rank} — </span>
								)}
								{submission.title}
							</h5>
						)}
						<BuildTeam members={submission.team} />
						{!hidden && (
							<p className="buildCardDescription">{submission.description}</p>
						)}
					</div>
					{!hidden && (
						<div className="buildCardFooter">
							{submission.voted && <span className="buildVoted">✓ Voted</span>}
							{submission.score && (
								<span>{submission.score.total.toFixed(2)} pts</span>
							)}
							{submission.position && (
								<span className="buildCoordinates">
									Coords: {buildCoordinates(submission.position)}
								</span>
							)}
							<button
								type="button"
								onClick={() => {
									setOpen(true);
								}}
							>
								Details
							</button>
						</div>
					)}
				</div>
			</article>
			{open && (
				<BuildSubmissionDialog
					eventId={eventId}
					stage={stage}
					submission={submission}
					categories={categories}
					onClose={() => {
						setOpen(false);
					}}
					onUpdated={onUpdated}
				/>
			)}
		</>
	);
}

export function BuildEventsTab({
	events,
	selectedId,
	detailPage = false,
}: {
	events: BuildEventSummary[];
	selectedId?: string;
	detailPage?: boolean;
}) {
	const event = selectedId
		? events.find((item) => String(item.id) === selectedId)
		: (events.find((item) =>
				['building', 'preparing', 'voting', 'visiting'].includes(item.status),
			) ??
			events.find((item) => item.status === 'upcoming') ??
			events.at(-1));
	const [detail, setDetail] = useState<BuildEventDetail | null>(null);
	const [error, setError] = useState('');
	const [now, setNow] = useState<number | null>(null);
	const eventId = event?.id;
	const refresh = async () => {
		if (eventId === undefined) return;
		setDetail(await loadBuildEvent(eventId));
	};
	useEffect(() => {
		if (eventId === undefined) return;
		const timer = window.setTimeout(() => {
			void loadBuildEvent(eventId)
				.then(setDetail)
				.catch((caught: unknown) => {
					setError(String(caught));
				});
		}, 0);
		return () => {
			window.clearTimeout(timer);
		};
	}, [eventId]);
	useEffect(() => {
		const initial = window.setTimeout(() => {
			setNow(Date.now());
		}, 0);
		const timer = window.setInterval(() => {
			setNow(Date.now());
		}, 1000);
		return () => {
			window.clearTimeout(initial);
			window.clearInterval(timer);
		};
	}, []);
	if (selectedId && !event)
		return (
			<div className="eventPage">
				<p>This build event could not be found.</p>
			</div>
		);
	if (!event)
		return (
			<div className="eventPage">
				<p>No build events have been scheduled yet.</p>
			</div>
		);
	const current = detail?.id === event.id ? detail : null;
	const status = current?.status ?? event.status;
	const countdownTarget =
		status === 'building'
			? event.buildEndsAtUnixMs
			: status === 'voting'
				? event.votingEndsAtUnixMs
				: null;
	const countdownLabel = status === 'building' ? 'Until building closes' : 'Until voting closes';
	return (
		<div className="eventPage buildEventPage">
			{detailPage && (
				<Link className="eventBack" href="/play/event">
					← All events
				</Link>
			)}
			<header className="eventTop">
				<h3>Events</h3>
				{countdownTarget && now !== null && (
					<div
						className="eventEndCountdown"
						role="timer"
						aria-label={countdownLabel + ': ' + remainingTime(countdownTarget, now)}
					>
						<strong>{remainingTime(countdownTarget, now)}</strong>
						<span>{countdownLabel}</span>
					</div>
				)}
			</header>
			<section className="adminSection eventFeature">
				<h4 className="eventTitle">{event.title}</h4>
				<EventDescription description={event.description} />
			</section>
			{current && status !== 'upcoming' && (
				<section className="adminSection eventScores">
					<div className="eventSectionHeading">
						<h4>Submissions</h4>
					</div>
					{status === 'building' || status === 'preparing' ? (
						<p>
							You can see other people&apos;s submissions and vote on them when voting
							opens.
						</p>
					) : status === 'voting' ? (
						<>
							<p>
								Please try to vote from top to bottom. Builds at the top have
								received fewer votes, so please check them out to give everyone a
								chance.
							</p>
							<p>
								Open a build&apos;s details, then scroll to the bottom to visit it
								and vote.
							</p>
							<div className="buildVoteProgress">
								<progress
									value={current.progress.voted}
									max={current.progress.total || 1}
									aria-label="Builds you have voted for"
								/>
								<span>
									{current.progress.voted} of {current.progress.total} eligible
									builds voted for
								</span>
							</div>
						</>
					) : null}
					<div className="buildCards">
						{current.submissions.map((submission) => (
							<BuildCard
								key={submission.id}
								eventId={event.id}
								stage={status}
								submission={submission}
								categories={current.categories}
								onUpdated={refresh}
							/>
						))}
					</div>
					{current.submissions.length === 0 && <p>No submissions yet.</p>}
				</section>
			)}
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
		</div>
	);
}
