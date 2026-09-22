'use client';

import { useEffect, useState } from 'react';
import { errorMessage, fetchAdmin } from './admin-api';

interface Drop {
	id: string;
	name: string;
	date: string;
	description: string;
}
interface Item {
	id: string;
	name: string;
	type: 'cosmetic' | 'decoblock' | 'charm';
	drop: string | null;
	shopPurchasable: boolean;
	membersOnly: boolean;
}
interface Knowledge {
	id: string;
	name: string;
	drop: string | null;
	public: boolean;
}
interface Catalog {
	drops: Drop[];
	items: Item[];
	knowledge: Knowledge[];
}
type Sort = 'drop' | 'name' | 'availability';
type View = 'summary' | 'chart' | 'items' | 'weekly';
type MembershipFilter = 'all' | 'members' | 'everyone';
type TypeFilter = 'all' | Item['type'];
type AvailabilityFilter = 'all' | 'shop' | 'outside';

const niceDate = (date: string) =>
	new Intl.DateTimeFormat('en-GB', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
		timeZone: 'UTC',
	}).format(new Date(`${date}T12:00:00Z`));

function countFor(catalog: Catalog, drop: string | null, type: Item['type'] | 'knowledge') {
	return type === 'knowledge'
		? catalog.knowledge.filter((entry) => entry.drop === drop).length
		: catalog.items.filter((entry) => entry.drop === drop && entry.type === type).length;
}

function ContentList({ entries }: { entries: (Item | Knowledge)[] }) {
	return entries.length ? (
		<ul className="dropContentList">
			{entries
				.toSorted((a, b) => a.name.localeCompare(b.name))
				.map((entry) => (
					<li key={entry.id}>
						{entry.name}
						{'membersOnly' in entry && entry.membersOnly && (
							<span title="Membership required" aria-label="Membership required">
								{' '}
								⭐
							</span>
						)}
						{'shopPurchasable' in entry && !entry.shopPurchasable && (
							<span className="dropItemNote">Included outside the shop</span>
						)}
						{'public' in entry && entry.public && (
							<span className="dropItemNote">Public</span>
						)}
					</li>
				))}
		</ul>
	) : (
		<p className="dropEmpty">None assigned</p>
	);
}

export function DropAnalyticsAdminSection() {
	const [catalog, setCatalog] = useState<Catalog | null>(null);
	const [error, setError] = useState('');

	useEffect(() => {
		let active = true;
		void fetchAdmin<Catalog>('/api/admin/drops', 'Failed to load drop analytics')
			.then((result) => {
				if (active) setCatalog(result);
			})
			.catch((caught: unknown) => {
				if (active) setError(errorMessage(caught, 'Failed to load drop analytics'));
			});
		return () => {
			active = false;
		};
	}, []);

	return (
		<section className="adminSection dropAnalytics">
			{catalog && <DropAnalyticsContent catalog={catalog} />}
			{!catalog && !error && <p>Loading drop analytics…</p>}
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
		</section>
	);
}

function DropAnalyticsContent({ catalog }: { catalog: Catalog }) {
	const [view, setView] = useState<View>('summary');
	const [membershipFilter, setMembershipFilter] = useState<MembershipFilter>('all');
	const [typeFilter, setTypeFilter] = useState<TypeFilter>('all');
	const [dropFilter, setDropFilter] = useState('all');
	const [availabilityFilter, setAvailabilityFilter] = useState<AvailabilityFilter>('all');
	const [sort, setSort] = useState<Sort>('drop');
	const [descending, setDescending] = useState(false);
	const allDrops = [{ id: null, name: 'Unassigned', date: '' }, ...catalog.drops];
	const releaseOrder = new Map(allDrops.map((drop, index) => [drop.id, index]));
	const rows = allDrops.map((drop) => ({
		...drop,
		cosmetics: countFor(catalog, drop.id, 'cosmetic'),
		memberCosmetics: catalog.items.filter(
			(item) => item.drop === drop.id && item.type === 'cosmetic' && item.membersOnly,
		).length,
		decoblocks: countFor(catalog, drop.id, 'decoblock'),
		charms: countFor(catalog, drop.id, 'charm'),
		knowledge: countFor(catalog, drop.id, 'knowledge'),
	}));
	const maximum = Math.max(
		1,
		...rows.map((row) => row.cosmetics + row.decoblocks + row.charms + row.knowledge),
	);
	const scale = Math.ceil(maximum / 5) * 5;
	const ticks = Array.from({ length: 6 }, (_, index) => scale - (index * scale) / 5);
	const items = catalog.items
		.filter(
			(item) =>
				(membershipFilter === 'all' ||
					item.membersOnly === (membershipFilter === 'members')) &&
				(typeFilter === 'all' || item.type === typeFilter) &&
				(dropFilter === 'all' ||
					item.drop === (dropFilter === 'unassigned' ? null : dropFilter)) &&
				(availabilityFilter === 'all' ||
					item.shopPurchasable === (availabilityFilter === 'shop')),
		)
		.toSorted((a, b) => {
			let comparison = 0;
			if (sort === 'drop')
				comparison = (releaseOrder.get(a.drop) ?? 0) - (releaseOrder.get(b.drop) ?? 0);
			if (sort === 'name') comparison = a.name.localeCompare(b.name);
			if (sort === 'availability')
				comparison = Number(a.shopPurchasable) - Number(b.shopPurchasable);
			return (descending ? -comparison : comparison) || a.name.localeCompare(b.name);
		});
	const total = (key: 'cosmetics' | 'decoblocks' | 'charms' | 'knowledge') =>
		rows.reduce((sum, row) => sum + row[key], 0);

	function changeSort(next: Sort) {
		if (sort === next) setDescending(!descending);
		else {
			setSort(next);
			setDescending(false);
		}
	}

	function showDrop(id: string | null) {
		setView('weekly');
		requestAnimationFrame(() => {
			const details = document.getElementById(
				`drop-${id ?? 'unassigned'}`,
			) as HTMLDetailsElement | null;
			if (!details) return;
			details.open = true;
			details.scrollIntoView({ behavior: 'smooth', block: 'start' });
		});
	}

	return (
		<>
			<header className="adminSectionHeader">
				<h3>Drop analytics</h3>
				<p>
					Content counts come from item definitions and knowledge pages. Unassigned
					content appears first.
				</p>
			</header>
			<nav className="dropViewTabs" aria-label="Drop analytics views">
				{(
					[
						['summary', 'Summary'],
						['chart', 'Chart'],
						['items', 'Items'],
						['weekly', 'Weekly drops'],
					] as const
				).map(([key, label]) => (
					<button
						key={key}
						type="button"
						className={view === key ? 'active' : ''}
						aria-pressed={view === key}
						onClick={() => {
							setView(key);
						}}
					>
						{label}
					</button>
				))}
			</nav>
			{view === 'summary' && (
				<div className="dropTableScroll">
					<table className="dropTable">
						<thead>
							<tr>
								<th scope="col">Drop</th>
								<th scope="col">Release</th>
								<th scope="col">Cosmetics</th>
								<th scope="col">Decoblocks</th>
								<th scope="col">Charms</th>
								<th scope="col">Knowledge</th>
								<th scope="col">Total</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((row) => (
								<tr key={row.id ?? 'unassigned'}>
									<th scope="row">
										<button
											className="dropTextButton"
											type="button"
											onClick={() => {
												showDrop(row.id);
											}}
										>
											{row.name}
										</button>
									</th>
									<td>{row.date ? niceDate(row.date) : '—'}</td>
									<td>{row.cosmetics}</td>
									<td>{row.decoblocks}</td>
									<td>{row.charms}</td>
									<td>{row.knowledge}</td>
									<td>
										{row.cosmetics +
											row.decoblocks +
											row.charms +
											row.knowledge}
									</td>
								</tr>
							))}
						</tbody>
						<tfoot>
							<tr>
								<th scope="row">Total</th>
								<td>—</td>
								<td>{total('cosmetics')}</td>
								<td>{total('decoblocks')}</td>
								<td>{total('charms')}</td>
								<td>{total('knowledge')}</td>
								<td>{catalog.items.length + catalog.knowledge.length}</td>
							</tr>
						</tfoot>
					</table>
				</div>
			)}
			{view === 'chart' && (
				<>
					<div className="dropChartHeading">
						<h4>Content by drop</h4>
						<div className="dropLegend">
							<span>
								<i className="dropLegendCosmetic" /> Other cosmetics
							</span>
							<span>
								<i className="dropLegendMemberCosmetic" /> Members only cosmetics
							</span>
							<span>
								<i className="dropLegendDeco" /> Decoblocks
							</span>
							<span>
								<i className="dropLegendCharm" /> Charms
							</span>
							<span>
								<i className="dropLegendKnowledge" /> Knowledge
							</span>
						</div>
					</div>
					<div
						className="dropChartScroll"
						role="img"
						aria-label="Stacked bar chart of cosmetics, members only cosmetics, decoblocks, charms and knowledge by drop, in release order"
					>
						<div className="dropChart">
							<div className="dropYAxis">
								{ticks.map((tick) => (
									<span key={tick}>{tick}</span>
								))}
							</div>
							<div
								className="dropPlot"
								style={{
									backgroundSize: `100% ${100 / 5}%`,
									gridTemplateColumns: `repeat(${rows.length}, minmax(45px, 1fr))`,
								}}
							>
								{rows.map((row) => (
									<div className="dropBarColumn" key={row.id ?? 'unassigned'}>
										<div
											className="dropBar"
											title={`${row.name}${row.date ? ` (${niceDate(row.date)})` : ''}: ${row.cosmetics - row.memberCosmetics} other cosmetics, ${row.memberCosmetics} members only cosmetics, ${row.decoblocks} decoblocks, ${row.charms} charms, ${row.knowledge} knowledge`}
										>
											<span
												className="dropBarDeco"
												style={{
													height: `${(row.decoblocks / scale) * 100}%`,
												}}
											/>
											<span
												className="dropBarCharm"
												style={{
													height: `${(row.charms / scale) * 100}%`,
												}}
											/>
											<span
												className="dropBarKnowledge"
												style={{
													height: `${(row.knowledge / scale) * 100}%`,
												}}
											/>
											<span
												className="dropBarCosmetic"
												style={{
													height: `${((row.cosmetics - row.memberCosmetics) / scale) * 100}%`,
												}}
											/>
											<span
												className="dropBarMemberCosmetic"
												style={{
													height: `${(row.memberCosmetics / scale) * 100}%`,
												}}
											/>
										</div>
										<small>
											{row.name}
											{row.date && (
												<>
													<br />
													{niceDate(row.date)}
												</>
											)}
										</small>
									</div>
								))}
							</div>
						</div>
					</div>
				</>
			)}
			{view === 'items' && (
				<>
					<div className="dropItemsHeading">
						<h4>Items ({items.length})</h4>
					</div>
					<div className="dropTableScroll">
						<table className="dropTable dropItemTable">
							<thead>
								<tr>
									<th scope="col">
										<button
											type="button"
											onClick={() => {
												changeSort('name');
											}}
										>
											Name {sort === 'name' ? (descending ? '↓' : '↑') : ''}
										</button>
									</th>
									<th scope="col">Type</th>
									<th scope="col">
										<button
											type="button"
											onClick={() => {
												changeSort('drop');
											}}
										>
											Drop {sort === 'drop' ? (descending ? '↓' : '↑') : ''}
										</button>
									</th>
									<th scope="col">
										<button
											type="button"
											onClick={() => {
												changeSort('availability');
											}}
										>
											Availability{' '}
											{sort === 'availability'
												? descending
													? '↓'
													: '↑'
												: ''}
										</button>
									</th>
									<th scope="col">Membership</th>
								</tr>
								<tr className="dropFilterRow">
									<th scope="col" aria-label="Name" />
									<th scope="col">
										<select
											aria-label="Filter type"
											value={typeFilter}
											onChange={(event) => {
												setTypeFilter(event.target.value as TypeFilter);
											}}
										>
											<option value="all">All types</option>
											<option value="cosmetic">Cosmetic</option>
											<option value="decoblock">Decoblock</option>
											<option value="charm">Charm</option>
										</select>
									</th>
									<th scope="col">
										<select
											aria-label="Filter drop"
											value={dropFilter}
											onChange={(event) => {
												setDropFilter(event.target.value);
											}}
										>
											<option value="all">All drops</option>
											{allDrops.map((drop) => (
												<option
													key={drop.id ?? 'unassigned'}
													value={drop.id ?? 'unassigned'}
												>
													{drop.name}
												</option>
											))}
										</select>
									</th>
									<th scope="col">
										<select
											aria-label="Filter availability"
											value={availabilityFilter}
											onChange={(event) => {
												setAvailabilityFilter(
													event.target.value as AvailabilityFilter,
												);
											}}
										>
											<option value="all">All availability</option>
											<option value="shop">Shop</option>
											<option value="outside">Outside the shop</option>
										</select>
									</th>
									<th scope="col">
										<select
											aria-label="Filter membership"
											value={membershipFilter}
											onChange={(event) => {
												setMembershipFilter(
													event.target.value as MembershipFilter,
												);
											}}
										>
											<option value="all">All items</option>
											<option value="members">Members only ⭐</option>
											<option value="everyone">Everyone</option>
										</select>
									</th>
								</tr>
							</thead>
							<tbody>
								{items.map((item) => (
									<tr key={item.id}>
										<th scope="row">{item.name}</th>
										<td>
											{item.type === 'cosmetic'
												? 'Cosmetic'
												: item.type === 'decoblock'
													? 'Decoblock'
													: 'Charm'}
										</td>
										<td>
											<button
												className="dropTextButton"
												type="button"
												onClick={() => {
													showDrop(item.drop);
												}}
											>
												{
													allDrops.find((drop) => drop.id === item.drop)
														?.name
												}
											</button>
										</td>
										<td>
											{item.shopPurchasable
												? 'Shop'
												: 'Included outside the shop'}
										</td>
										<td>{item.membersOnly ? 'Members only ⭐' : 'Everyone'}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}
			{view === 'weekly' && (
				<>
					<h4>Weekly drops</h4>
					<div className="dropWeekList">
						{[
							{ id: 'unassigned', name: 'Unassigned', date: '', description: '' },
							...catalog.drops,
						].map((drop, index) => {
							const dropId = drop.id === 'unassigned' ? null : drop.id;
							const itemEntries = catalog.items.filter(
								(item) => item.drop === dropId,
							);
							const knowledgeEntries = catalog.knowledge.filter(
								(entry) => entry.drop === dropId,
							);
							return (
								<details className="dropWeek" id={`drop-${drop.id}`} key={drop.id}>
									<summary>
										<span>
											{dropId && (
												<small>
													Week {index} · {niceDate(drop.date)}
												</small>
											)}
											<strong>
												{drop.name}
												{dropId && ' Drop'}
											</strong>
										</span>
										<span>
											{itemEntries.length + knowledgeEntries.length} entries
										</span>
									</summary>
									{drop.description && (
										<p className="dropWeekDescription">{drop.description}</p>
									)}
									<div className="dropWeekGroups">
										<div>
											<h6>
												Cosmetics (
												{
													itemEntries.filter(
														(item) => item.type === 'cosmetic',
													).length
												}
												)
											</h6>
											<ContentList
												entries={itemEntries.filter(
													(item) => item.type === 'cosmetic',
												)}
											/>
										</div>
										<div>
											<h6>
												Decoblocks (
												{
													itemEntries.filter(
														(item) => item.type === 'decoblock',
													).length
												}
												)
											</h6>
											<ContentList
												entries={itemEntries.filter(
													(item) => item.type === 'decoblock',
												)}
											/>
										</div>
										<div>
											<h6>
												Charms (
												{
													itemEntries.filter(
														(item) => item.type === 'charm',
													).length
												}
												)
											</h6>
											<ContentList
												entries={itemEntries.filter(
													(item) => item.type === 'charm',
												)}
											/>
										</div>
										<div>
											<h6>Knowledge ({knowledgeEntries.length})</h6>
											<ContentList entries={knowledgeEntries} />
										</div>
									</div>
								</details>
							);
						})}
					</div>
				</>
			)}
		</>
	);
}
