import { BadRequestException, Injectable } from '@nestjs/common';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { MinecraftGrpcClientService } from '../../grpc/minecraft-grpc-client.service';
import { ShopItemCatalogService } from './shop-item-catalog.service';
import { charmsUpgraded } from '../../monitoring/monitoring.service';

interface CharmUpgradeIngredientResponse {
	raw: string;
	display_name: string;
	icon_item_id: string;
	required_count: number;
	inventory_count: number;
}

interface InventoryCharmResponse {
	item_id: string;
	title: string;
	current_level: number;
	max_level: number;
	target_level: number;
	price_dabloons: number;
	current_ability: string;
	next_ability: string;
	ingredients: CharmUpgradeIngredientResponse[];
}

interface GetCharmInventoryResponse {
	online: boolean;
	balance_dabloons: number;
	charms: InventoryCharmResponse[];
	message: string;
}

interface UpgradeCharmResponse {
	upgraded: boolean;
	online: boolean;
	balance_dabloons: number;
	new_level: number;
	message: string;
}

export interface CharmUpgradeInput {
	itemId?: string;
	expectedLevel?: number;
}

@Injectable()
export class ShopCharmInventoryService {
	constructor(
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly itemCatalog: ShopItemCatalogService,
	) {}

	async getInventory(user: AuthenticatedUser) {
		let inventory: GetCharmInventoryResponse;
		try {
			inventory = await this.minecraft.gameplay<GetCharmInventoryResponse>(
				'GetCharmInventory',
				{
					minecraft_username: user.minecraftUsername,
				},
			);
		} catch {
			throw new BadRequestException(
				'Join the Minecraft server with this account, hold a charm in your main hand, and refresh the forge.',
			);
		}

		return {
			online: inventory.online,
			balanceDabloons: inventory.balance_dabloons,
			message: inventory.message,
			charms: inventory.charms.map((charm) => {
				const itemAsset = this.itemCatalog.itemAsset(charm.item_id);
				return {
					itemId: charm.item_id,
					title: charm.title,
					currentLevel: charm.current_level,
					maxLevel: charm.max_level,
					targetLevel: charm.target_level,
					priceDabloons: charm.price_dabloons,
					currentAbility: charm.current_ability,
					nextAbility: charm.next_ability,
					modelUrl: itemAsset?.modelUrl ?? null,
					textureUrl: itemAsset?.textureUrl ?? null,
					animation: itemAsset?.animation ?? null,
					ingredients: charm.ingredients.map((ingredient) => {
						const ingredientAsset = this.itemCatalog.gameItemAsset(
							ingredient.icon_item_id,
						);
						return {
							raw: ingredient.raw,
							displayName: ingredient.display_name,
							requiredCount: ingredient.required_count,
							inventoryCount: ingredient.inventory_count,
							itemId: ingredient.icon_item_id,
							iconUrl: ingredient.icon_item_id.startsWith('minecraft:')
								? null
								: (ingredientAsset?.textureUrl ?? null),
							modelUrl: ingredientAsset?.modelUrl ?? null,
						};
					}),
				};
			}),
		};
	}

	async upgrade(user: AuthenticatedUser, input: CharmUpgradeInput) {
		const itemId = typeof input.itemId === 'string' ? input.itemId.trim() : '';
		if (!itemId || !Number.isInteger(input.expectedLevel)) {
			throw new BadRequestException('The selected charm is invalid.');
		}

		let result: UpgradeCharmResponse;
		try {
			result = await this.minecraft.gameplay<UpgradeCharmResponse>('UpgradeCharm', {
				minecraft_username: user.minecraftUsername,
				item_id: itemId,
				expected_level: input.expectedLevel,
			});
		} catch {
			throw new BadRequestException(
				'Join the Minecraft server, keep the charm in your main hand, and try the upgrade again.',
			);
		}

		if (!result.upgraded) {
			throw new BadRequestException(result.message || 'The charm could not be upgraded.');
		}
		charmsUpgraded.inc();
		return {
			upgraded: true,
			newLevel: result.new_level,
			balanceDabloons: result.balance_dabloons,
			message: result.message,
		};
	}
}
