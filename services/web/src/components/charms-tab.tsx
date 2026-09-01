'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { CharmForgeRenderer } from '@/lib/charm-forge-renderer';
import { ASSETS } from '@/lib/assets';
import { MinecraftItemIcon } from '@/components/minecraft-item-icon';
import { DabloonAmount, DabloonText } from '@/components/dabloon-amount';
import { useSiteAlert } from '@/components/site-alert';
import { formatDabloonWord } from '@/lib/dabloons';

interface CharmIngredient {
	raw: string;
	displayName: string;
	requiredCount: number;
	inventoryCount: number;
	itemId: string;
	iconUrl: string | null;
	modelUrl: string | null;
}

interface HeldCharm {
	itemId: string;
	title: string;
	currentLevel: number;
	maxLevel: number;
	targetLevel: number;
	priceDabloons: number;
	currentAbility: string;
	nextAbility: string;
	modelUrl: string | null;
	textureUrl: string | null;
	ingredients: CharmIngredient[];
}

interface CharmInventory {
	online: boolean;
	balanceDabloons: number;
	message: string;
	charms: HeldCharm[];
}

async function fetchCharmInventory() {
	const response = await fetch('/api/shop/charms', { cache: 'no-store' });
	const data = (await response.json().catch(() => null)) as
		CharmInventory | { message?: string } | null;
	if (!response.ok) throw new Error(data?.message ?? 'Could not open the charm forge.');
	return data as CharmInventory;
}

export function CharmsTab() {
	const { showAlert } = useSiteAlert();
	const [inventory, setInventory] = useState<CharmInventory | null>(null);
	const [loading, setLoading] = useState(true);
	const [upgrading, setUpgrading] = useState(false);
	const [animating, setAnimating] = useState(false);
	const [message, setMessage] = useState('');
	const [messageIsError, setMessageIsError] = useState(false);
	const forgeHost = useRef<HTMLDivElement>(null);
	const forge = useRef<CharmForgeRenderer | null>(null);
	const charm = inventory?.charms[0] ?? null;
	const forgeCharm = useRef<HeldCharm | null>(charm);
	const forgeKey = charm
		? JSON.stringify({
				itemId: charm.itemId,
				currentLevel: charm.currentLevel,
				modelUrl: charm.modelUrl,
				textureUrl: charm.textureUrl,
				ingredients: charm.ingredients.map(({ itemId, modelUrl, iconUrl }) => ({
					itemId,
					modelUrl,
					iconUrl,
				})),
			})
		: '';
	const hasUpgradeMaterials = hasRequiredMaterials(charm);

	const refresh = useCallback(async () => {
		try {
			const nextInventory = await fetchCharmInventory();
			setInventory(nextInventory);
			return nextInventory;
		} catch (error) {
			setInventory(null);
			setMessage(error instanceof Error ? error.message : 'Could not open the charm forge.');
			setMessageIsError(true);
			return null;
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		let cancelled = false;
		void fetchCharmInventory()
			.then((data) => {
				if (!cancelled) setInventory(data);
			})
			.catch((error: unknown) => {
				if (!cancelled) {
					setMessage(
						error instanceof Error ? error.message : 'Could not open the charm forge.',
					);
					setMessageIsError(true);
				}
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		forgeCharm.current = charm;
	}, [charm]);

	useEffect(() => {
		const renderedCharm = forgeCharm.current;
		forge.current?.destroy();
		forge.current = null;
		if (!forgeHost.current || !renderedCharm?.textureUrl) return;

		const renderer = new CharmForgeRenderer(forgeHost.current, {
			charm: {
				assetRoot: ASSETS.minecraft.root,
				itemId: `mainmod:${renderedCharm.itemId}`,
				modelUrl: renderedCharm.modelUrl,
				textureUrl: renderedCharm.textureUrl,
			},
			ingredients: renderedCharm.ingredients.map((ingredient) =>
				ingredient.itemId.startsWith('minecraft:')
					? { assetRoot: ASSETS.minecraft.root, itemId: ingredient.itemId }
					: {
							assetRoot: ASSETS.minecraft.root,
							itemId: ingredient.itemId,
							modelUrl: ingredient.modelUrl,
							textureUrl: ingredient.iconUrl,
						},
			),
		});
		forge.current = renderer;
		return () => {
			renderer.destroy();
			if (forge.current === renderer) forge.current = null;
		};
	}, [forgeKey]);

	async function upgrade() {
		if (!charm || upgrading || charm.currentLevel >= charm.maxLevel) return;
		setUpgrading(true);
		setMessage('Checking your inventory...');
		setMessageIsError(false);

		try {
			const latestInventory = await refresh();
			if (!latestInventory) return;
			const latestCharm = latestInventory.charms[0];
			if (
				latestCharm.itemId !== charm.itemId ||
				latestCharm.currentLevel !== charm.currentLevel
			) {
				showUpgradeAlert(
					'Held charm changed',
					'Your held charm changed. Review the refreshed inventory before upgrading.',
				);
				return;
			}
			if (!hasRequiredMaterials(latestCharm)) {
				const missing = latestCharm.ingredients
					.filter((ingredient) => ingredient.inventoryCount < ingredient.requiredCount)
					.map(
						(ingredient) =>
							`${ingredient.displayName}: ${ingredient.inventoryCount} available, ${ingredient.requiredCount} required`,
					)
					.join('\n');
				showUpgradeAlert(
					'Missing upgrade materials',
					`Your inventory does not contain everything this upgrade needs:\n\n${missing}\n\nCollect the missing items, keep them in your inventory, then refresh the forge.`,
				);
				return;
			}
			if (latestInventory.balanceDabloons < latestCharm.priceDabloons) {
				showUpgradeAlert(
					'Not enough Dabloons',
					`This upgrade costs ${formatDabloonWord(latestCharm.priceDabloons)}, but your balance is ${formatDabloonWord(latestInventory.balanceDabloons)}. Earn ${formatDabloonWord(latestCharm.priceDabloons - latestInventory.balanceDabloons)} more and try again.`,
				);
				return;
			}

			setMessage('The forge is reading your main hand...');
			const response = await fetch('/api/shop/charms/upgrade', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					itemId: latestCharm.itemId,
					expectedLevel: latestCharm.currentLevel,
				}),
			});
			const result = (await response.json().catch(() => null)) as { message?: string } | null;
			if (!response.ok) throw new Error(result?.message ?? 'The upgrade failed.');

			setMessage('Upgrade accepted. Stand back!');
			setAnimating(true);
			await forge.current?.playUpgrade();
			await refresh();
			setMessage(result?.message ?? 'The charm grew stronger.');
			setMessageIsError(false);
		} catch (error) {
			const failure = error instanceof Error ? error.message : 'The upgrade failed.';
			await refresh();
			setMessage(failure);
			setMessageIsError(true);
			await showAlert({ title: 'Charm upgrade failed', message: failure, tone: 'danger' });
		} finally {
			setAnimating(false);
			setUpgrading(false);
		}
	}

	function showUpgradeAlert(title: string, warning: string) {
		setMessage(warning);
		setMessageIsError(true);
		void showAlert({ title, message: warning, tone: 'danger' });
	}

	return (
		<div className="charmForge">
			<header className="charmForgeHeader">
				<div>
					<h3>Charm Forge</h3>
					<p className="tabSubtitle">
						Charms give you new abilities, from climbing walls to mining whole veins of
						ore. You can upgrade them here to make those abilities stronger. For all
						infos, check out the{' '}
						<Link href="/play/knowledge/charms">charms knowledge book</Link>.
					</p>
					<p className="tabNote">
						Hold the charm you want to upgrade in your main hand, then refresh your
						inventory.
					</p>
				</div>
				<div className="charmForgeControls">
					<span className="charmBalance" title="Current Dabloon balance">
						{inventory ? (
							<DabloonAmount amount={inventory.balanceDabloons} format="full" />
						) : (
							'—'
						)}
					</span>
					<button
						type="button"
						className="charmRefresh"
						onClick={() => {
							setLoading(true);
							setMessage('');
							setMessageIsError(false);
							void refresh();
						}}
						disabled={loading || upgrading}
					>
						<span aria-hidden="true">↻</span>{' '}
						{loading && inventory ? 'Refreshing inventory...' : 'Refresh inventory'}
					</button>
				</div>
			</header>

			{loading && !inventory && (
				<div className="charmForgeEmpty loading" role="status">
					<div className="charmRune" aria-hidden="true">
						✦
					</div>
					<strong>Reading the forge...</strong>
				</div>
			)}

			{!charm && (!loading || inventory !== null) && (
				<div className="charmForgeEmpty">
					<div className="charmRune" aria-hidden="true">
						✧
					</div>
					<strong>No charm found in your main hand</strong>
					<p role={messageIsError ? 'alert' : undefined}>
						<DabloonText>
							{message.length > 0
								? message
								: (inventory?.message ??
									'Equip a charm in your hotbar, select it, and press Refresh inventory.')}
						</DabloonText>
					</p>
				</div>
			)}

			{charm && (
				<>
					<section
						className={`charmForgeStage ${animating ? 'enchanting' : ''}`}
						aria-label={`${charm.title} upgrade preview`}
					>
						<div className="charmForgeAura" aria-hidden="true" />
						<div ref={forgeHost} className="charmForgeScene" />
						<div className="charmIdentity">
							<h4>
								<DabloonText>{charm.title}</DabloonText>
							</h4>
							<strong>Level {charm.currentLevel}</strong>
							{charm.currentLevel < charm.maxLevel && (
								<span>→ Level {charm.targetLevel}</span>
							)}
						</div>
					</section>

					<section className="charmAbility">
						<div>
							<span>Current level</span>
							<p>{charm.currentAbility}</p>
						</div>
						{charm.currentLevel < charm.maxLevel && (
							<div>
								<span>Next level</span>
								<p>{charm.nextAbility}</p>
							</div>
						)}
					</section>

					{charm.currentLevel < charm.maxLevel ? (
						<section className="charmIngredientSection">
							<div className="charmSectionHeading">
								<div>
									<span>Reagents</span>
									<h4>Required ingredients</h4>
								</div>
							</div>
							<ul className="charmIngredientGrid">
								{charm.ingredients.map((ingredient) => (
									<li
										className={
											ingredient.inventoryCount < ingredient.requiredCount
												? 'missing'
												: undefined
										}
										key={ingredient.raw}
									>
										<div className="charmIngredientIcon">
											<MinecraftItemIcon
												className="charmIngredientModel"
												itemId={ingredient.itemId}
												modelUrl={ingredient.modelUrl}
												textureUrl={ingredient.iconUrl}
											/>
										</div>
										<strong>{ingredient.displayName}</strong>
										<span
											aria-label={`${ingredient.inventoryCount} in inventory, ${ingredient.requiredCount} required`}
										>
											{ingredient.inventoryCount} / {ingredient.requiredCount}
										</span>
									</li>
								))}
							</ul>
						</section>
					) : (
						<div className="charmMastered">
							<span aria-hidden="true">✦</span> Maximum level reached
						</div>
					)}

					<div className="charmEnchantBar">
						<div
							className={`charmForgeMessage ${messageIsError ? 'error' : ''}`}
							role={messageIsError ? 'alert' : 'status'}
						>
							<DabloonText>
								{message ||
									'The server will verify your held charm, reagents, and balance.'}
							</DabloonText>
						</div>
						<button
							type="button"
							className={`charmEnchantButton${!hasUpgradeMaterials && charm.currentLevel < charm.maxLevel ? ' insufficient' : ''}`}
							onClick={() => void upgrade()}
							disabled={upgrading || charm.currentLevel >= charm.maxLevel}
						>
							<span>
								{upgrading
									? 'Upgrading...'
									: charm.currentLevel >= charm.maxLevel
										? 'Charm mastered'
										: 'Upgrade charm'}
							</span>
							{charm.currentLevel < charm.maxLevel && (
								<strong>
									{charm.priceDabloons === 0 ? (
										'Free'
									) : (
										<DabloonAmount
											amount={charm.priceDabloons}
											format="full"
											tone="inherit"
										/>
									)}
								</strong>
							)}
						</button>
					</div>
				</>
			)}

			{!charm && message && (
				<p className="charmForgeMessage" role="alert">
					<DabloonText>{message}</DabloonText>
				</p>
			)}
		</div>
	);
}

function hasRequiredMaterials(charm: HeldCharm | null) {
	return (
		charm?.ingredients.every(
			(ingredient) => ingredient.inventoryCount >= ingredient.requiredCount,
		) ?? false
	);
}
