'use client';

import { useMemo, useState } from 'react';
import catalog from '@/data/drop-analytics.json';

type Item = (typeof catalog.items)[number];
type Knowledge = (typeof catalog.knowledge)[number];
type Sort = 'drop' | 'name' | 'availability';

const allDrops = [{ id: null, name: 'Unassigned', date: null, description: '' }, ...catalog.drops];
const releaseOrder = new Map(allDrops.map((drop, index) => [drop.id, index]));
const niceDate = (date: string) =>
	new Intl.DateTimeFormat('en-GB', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
		timeZone: 'UTC',
	}).format(new Date(`${date}T12:00:00Z`));

function countFor(drop: string | null, type: 'cosmetic' | 'decoblock' | 'knowledge') {
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
	const [sort, setSort] = useState<Sort>('drop');
	const [descending, setDescending] = useState(false);
	const rows = useMemo(
		() =>
			allDrops.map((drop) => ({
				...drop,
				cosmetics: countFor(drop.id, 'cosmetic'),
				decoblocks: countFor(drop.id, 'decoblock'),
				knowledge: countFor(drop.id, 'knowledge'),
			})),
		[],
	);
	const maximum = Math.max(
		1,
		...rows.map((row) => row.cosmetics + row.decoblocks + row.knowledge),
	);
	const scale = Math.ceil(maximum / 5) * 5;
	const ticks = Array.from({ length: 6 }, (_, index) => scale - (index * scale) / 5);
	const cosmetics = useMemo(
		() =>
			catalog.items
				.filter((item) => item.type === 'cosmetic')
				.toSorted((a, b) => {
					let comparison = 0;
					if (sort === 'drop')
						comparison =
							(releaseOrder.get(a.drop) ?? 0) - (releaseOrder.get(b.drop) ?? 0);
					if (sort === 'name') comparison = a.name.localeCompare(b.name);
					if (sort === 'availability')
						comparison = Number(a.shopPurchasable) - Number(b.shopPurchasable);
					return (descending ? -comparison : comparison) || a.name.localeCompare(b.name);
				}),
		[sort, descending],
	);
	const total = (key: 'cosmetics' | 'decoblocks' | 'knowledge') =>
		rows.reduce((sum, row) => sum + row[key], 0);

	function changeSort(next: Sort) {
		if (sort === next) setDescending(!descending);
		else {
			setSort(next);
			setDescending(false);
		}
	}

	return (
		<section className="adminSection dropAnalytics">
			<header className="adminSectionHeader">
				<h3>Drop analytics</h3>
				<p>
					Content counts come from item definitions and knowledge pages. Unassigned
					content appears first.
				</p>
			</header>
			<div className="dropTableScroll">
				<table className="dropTable">
					<thead>
						<tr>
							<th scope="col">Drop</th>
							<th scope="col">Cosmetics</th>
							<th scope="col">Decoblocks</th>
							<th scope="col">Knowledge</th>
							<th scope="col">Total</th>
						</tr>
					</thead>
					<tbody>
						{rows.map((row) => (
							<tr key={row.id ?? 'unassigned'}>
								<th scope="row">{row.name}</th>
								<td>{row.cosmetics}</td>
								<td>{row.decoblocks}</td>
								<td>{row.knowledge}</td>
								<td>{row.cosmetics + row.decoblocks + row.knowledge}</td>
							</tr>
						))}
					</tbody>
					<tfoot>
						<tr>
							<th scope="row">Total</th>
							<td>{total('cosmetics')}</td>
							<td>{total('decoblocks')}</td>
							<td>{total('knowledge')}</td>
							<td>{catalog.items.length + catalog.knowledge.length}</td>
						</tr>
					</tfoot>
				</table>
			</div>
			<div className="dropChartHeading">
				<h4>Content by drop</h4>
				<div className="dropLegend">
					<span>
						<i className="dropLegendCosmetic" /> Cosmetics
					</span>
					<span>
						<i className="dropLegendDeco" /> Decoblocks
					</span>
					<span>
						<i className="dropLegendKnowledge" /> Knowledge
					</span>
				</div>
			</div>
			<div
				className="dropChartScroll"
				role="img"
				aria-label="Stacked bar chart of cosmetics, decoblocks and knowledge by drop, in release order"
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
									title={`${row.name}: ${row.cosmetics} cosmetics, ${row.decoblocks} decoblocks, ${row.knowledge} knowledge`}
								>
									<span
										className="dropBarDeco"
										style={{ height: `${(row.decoblocks / scale) * 100}%` }}
									/>
									<span
										className="dropBarKnowledge"
										style={{ height: `${(row.knowledge / scale) * 100}%` }}
									/>
									<span
										className="dropBarCosmetic"
										style={{ height: `${(row.cosmetics / scale) * 100}%` }}
									/>
								</div>
								<small>{row.name}</small>
							</div>
						))}
					</div>
				</div>
			</div>
			<h4>Cosmetics ({cosmetics.length})</h4>
			<div className="dropTableScroll">
				<table className="dropTable dropCosmeticTable">
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
									{sort === 'availability' ? (descending ? '↓' : '↑') : ''}
								</button>
							</th>
						</tr>
					</thead>
					<tbody>
						{cosmetics.map((item) => (
							<tr key={item.id}>
								<th scope="row">{item.name}</th>
								<td>{allDrops.find((drop) => drop.id === item.drop)?.name}</td>
								<td>
									{item.shopPurchasable ? 'Shop' : 'Included outside the shop'}
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
			<h4>Weekly drops</h4>
			<div className="dropWeekList">
				{catalog.drops.map((drop, index) => {
					const itemEntries = catalog.items.filter((item) => item.drop === drop.id);
					const knowledgeEntries = catalog.knowledge.filter(
						(entry) => entry.drop === drop.id,
					);
					return (
						<article className="dropWeek" key={drop.id}>
							<header>
								<div>
									<small>
										Week {index + 1} · {niceDate(drop.date)}
									</small>
									<h5>{drop.name} Drop</h5>
									<p>{drop.description}</p>
								</div>
								<strong>
									{itemEntries.length + knowledgeEntries.length} entries
								</strong>
							</header>
							<div className="dropWeekGroups">
								<div>
									<h6>
										Cosmetics (
										{
											itemEntries.filter((item) => item.type === 'cosmetic')
												.length
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
											itemEntries.filter((item) => item.type === 'decoblock')
												.length
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
									<h6>Knowledge ({knowledgeEntries.length})</h6>
									<ContentList entries={knowledgeEntries} />
								</div>
							</div>
						</article>
					);
				})}
			</div>
		</section>
	);
}
