import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { randomInt } from 'node:crypto'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { and, eq } from 'drizzle-orm'
import { AuthenticatedUser } from '../../auth/auth.service'
import { DatabaseService, shopUnlocks, UserRow, users } from '../../database/database.service'
import { GrpcServerService } from '../../grpc/grpc-server.service'
import { KnowledgeService } from '../knowledge/knowledge.service'

const DEFAULT_ITEM_ROOTS = [
	join(process.cwd(), 'content', 'items'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
	join(process.cwd(), 'minecraft', 'main', 'data', 'data', 'items'),
]

const VANILLA_ITEM_TEXTURE_PREFIX = '/assets/mc_respack/assets/minecraft/textures/item'
const SHOP_ASSET_REVISION = `${Date.now().toString(36)}-${randomInt(0x100000000).toString(36)}`
const DAILY_DEAL_MESSAGES = [
	"What a steal!",
	"Limited time offer",
	"While stocks last",
	"Best seller",
	"New arrival",
	"Must-have",
	"Amazing deal",
	"Nothing beats a {item}",
	"Today's treasure",
	"A proper bargain",
	"Blink and you'll miss it",
	"Worth every dabloon",
	"A deal this good is rare",
	"The price is right",
	"Too good to leave behind",
	"Limited-time deal",
	"Treat yo' self",
	"Shut up and take my money!",
	"Anything from the trolley, dears?",
	"We'll take the lot.",
	"Buy somethin', will ya!",
	"I am never going to financially recover from this.",
	"Let's go shopping!",
	"Capitalism, baby!",
	"It's our best-seller.",
	"A fool and her money soon part."
] as const

type ShopItemType = 'charm' | 'cosmetic' | 'generic'
type ShopDeliveryKind = 'fake_item' | 'vanilla_item'
type BookUnlockType = 'knowledge' | 'charm' | 'cosmetic'

interface ShopPurchasableDefinition {
	priceDabloons: number
	description: string
	unlockMessage: string | null
	unlockWeight: number
}

interface FakeItemDefinition {
	title?: unknown
	id?: unknown
	baseItemOverride?: unknown
	modelType?: unknown
	rarity?: unknown
	tooltips?: unknown
	shopPurchasable?: unknown
	dyeable?: unknown
	decoBlock?: unknown
	charm?: unknown
	equippableCharm?: unknown
	equippableCosmetic?: unknown
}

interface CharmLevelDefinition {
	level: number
	abilityStatusCurrent: string
	upgradeIngredients: string[]
}

interface CharmDetailsDefinition {
	minLevel: number
	maxLevel: number
	levels: CharmLevelDefinition[]
}

interface TextureAnimationDefinition {
	frameDelayMs: number
	frames: number[] | null
}

interface CatalogItem {
	id: string
	title: string
	type: ShopItemType
	modelType: string
	rarity: string
	priceDabloons: number
	description: string
	tooltips: string[]
	unlockMessage: string | null
	unlockWeight: number
	iconUrl: string | null
	renderMode: 'texture' | 'model'
	modelUrl: string | null
	textureUrl: string | null
	animated: boolean
	dyeable: boolean
	animation: TextureAnimationDefinition | null
	textureFilePath: string | null
	modelFilePath: string | null
	deliveryKind: ShopDeliveryKind
	deliveryItemId: string
	bookUnlockType: BookUnlockType | null
	charmDetails: CharmDetailsDefinition | null
}

interface CachedShopCatalog {
	root: string
	mtimeMs: number
	items: CatalogItem[]
}

interface UnlockAvailability {
	knowledge: boolean
	charms: boolean
	cosmetics: boolean
}

interface ShopUnlockResponse {
	unlocked: boolean
	all_unlocked: boolean
	knowledge_id: string
	unlocked_id: string
	priority: number
	topic: string
	message: string
}

interface PurchaseShopItemResponse {
	purchased: boolean
	online: boolean
	balance_dabloons: number
	message: string
}

interface GameplayProtoRoot {
	mcstack: {
		gameplay: {
			v1: {
				GameplayControl: grpc.ServiceClientConstructor
			}
		}
	}
}

@Injectable()
export class ShopService {
	private cached: CachedShopCatalog | null = null
	private gameplayControlClient: grpc.Client | null = null

	constructor(
		private readonly database: DatabaseService,
		private readonly grpcServer: GrpcServerService,
		private readonly knowledge: KnowledgeService,
	) { }

	getShopForUser(user: AuthenticatedUser) {
		const items = this.loadCatalog().items
		const unlockedIds = this.getUnlockedItemIds(user.id)
		const availability = this.getUnlockAvailabilityForUser(user.id)
		const dailyDealIds = this.getDailyDealIds(items)
		const visibleItems = items.filter((item) => this.isVisibleInShop(item, unlockedIds))

		return {
			availability,
			dealDate: this.getDailyDealDate(),
			shoppingSunday: this.isShoppingSunday(),
			items: visibleItems.map((item) => {
				const isDailyDeal = dailyDealIds.has(item.id)
				const discountPercent = isDailyDeal ? this.getDailyDiscountPercent(item.id) : 0
				return {
					id: item.id,
					title: item.title,
					type: item.type,
					modelType: item.modelType,
					rarity: item.rarity,
					priceDabloons: item.priceDabloons,
					originalPriceDabloons: item.priceDabloons,
					isDailyDeal,
					discountPercent,
					dealMessage: isDailyDeal ? this.getDailyDealMessage(item) : null,
					discountedPriceDabloons: this.discountedPrice(item.priceDabloons, discountPercent),
					description: item.description,
					tooltips: item.tooltips,
					unlockMessage: item.unlockMessage,
					unlockWeight: item.unlockWeight,
					iconUrl: item.iconUrl,
					renderMode: item.renderMode,
					modelUrl: item.modelUrl,
					textureUrl: item.textureUrl,
					animated: item.animated,
					dyeable: item.dyeable,
					animation: item.animation,
					charmDetails: item.charmDetails,
					unlocked: this.isUnlockedShopItem(item, unlockedIds),
					available: this.isAvailableForPurchase(user.id, item, availability, unlockedIds),
				}
			}),
		}
	}

	async purchaseItem(user: AuthenticatedUser, itemIdInput: unknown) {
		const itemId = typeof itemIdInput === 'string' ? itemIdInput.trim() : ''
		if (!itemId) {
			throw new BadRequestException('No shop item was selected.')
		}

		const item = this.loadCatalog().items.find((candidate) => candidate.id === itemId)
		if (!item) {
			throw new NotFoundException('Shop item not found.')
		}

		const availability = this.getUnlockAvailabilityForUser(user.id)
		const unlockedIds = this.getUnlockedItemIds(user.id)
		if (!this.isAvailableForPurchase(user.id, item, availability, unlockedIds)) {
			throw new BadRequestException(this.unavailablePurchaseMessage(item))
		}

		const catalogItems = this.loadCatalog().items
		const isDailyDeal = this.getDailyDealIds(catalogItems).has(item.id)
		const discountPercent = isDailyDeal ? this.getDailyDiscountPercent(item.id) : 0
		const purchasePrice = this.discountedPrice(item.priceDabloons, discountPercent)

		let purchase: PurchaseShopItemResponse
		try {
			purchase = await this.purchaseFromMod(user.minecraftUsername, item, purchasePrice)
		} catch {
			throw new BadRequestException('You have to be online on the server to buy from the shop.')
		}

		if (!purchase.purchased) {
			throw new BadRequestException(purchase.message || this.purchaseFailureMessage(purchase))
		}

		return {
			purchased: true,
			itemId: item.id,
			online: purchase.online,
			balanceDabloons: purchase.balance_dabloons,
			message: purchase.message || `${item.title} purchased.`,
		}
	}

	unlockNextForMinecraftUsername(
		minecraftUsernameInput: string,
		unlockTypeInput: string,
		sourceInput: string,
	): ShopUnlockResponse {
		const unlockType = this.normalizeUnlockType(unlockTypeInput)
		if (!unlockType) {
			return this.noShopUnlock('That book cannot unlock this kind of reward.')
		}

		const minecraftUsername = minecraftUsernameInput.trim()
		const source = sourceInput.trim() || `${unlockType}_book`

		if (!minecraftUsername) {
			return this.noShopUnlock('No Minecraft username was provided.')
		}

		const user = this.findUserByMinecraftUsername(minecraftUsername)
		if (!user) {
			return this.noShopUnlock('No website account is linked to this Minecraft username yet.')
		}

		const candidates = this.loadCatalog().items.filter((item) => item.type === unlockType)
		if (candidates.length === 0) {
			return {
				unlocked: false,
				all_unlocked: true,
				knowledge_id: '',
				unlocked_id: '',
				priority: 0,
				topic: '',
				message: `There are no unlockable ${unlockType}s configured yet.`,
			}
		}

		for (let attempt = 0; attempt < 5; attempt++) {
			const picked = this.database.connection.transaction((tx) => {
				const unlockedIds = this.getUnlockedItemIds(user.id, unlockType)
				const remaining = candidates.filter((item) => !unlockedIds.has(item.id))

				if (remaining.length === 0) {
					return 'all-unlocked' as const
				}

				const chosen = this.pickWeightedRandomItem(remaining)
				const result = tx.insert(shopUnlocks).values({
					user_id: user.id,
					item_id: chosen.id,
					unlock_type: chosen.type,
					unlocked_at_unix_ms: Date.now(),
					source,
				}).onConflictDoNothing().run()

				return result.changes === 1 ? chosen : null
			})

			if (picked === 'all-unlocked') {
				return {
					unlocked: false,
					all_unlocked: true,
					knowledge_id: '',
					unlocked_id: '',
					priority: 0,
					topic: '',
					message: `You have already unlocked all available ${unlockType}s.`,
				}
			}

			if (picked) {
				return {
					unlocked: true,
					all_unlocked: false,
					knowledge_id: picked.id,
					unlocked_id: picked.id,
					priority: picked.unlockWeight,
					topic: picked.title,
					message: picked.unlockMessage ?? `You've unlocked ${picked.title}. Visit the website shop to see it.`,
				}
			}
		}

		return this.noShopUnlock('Unlock was busy. Try again.')
	}

	getUnlockAvailabilityForMinecraftUsername(minecraftUsernameInput: string) {
		const minecraftUsername = minecraftUsernameInput.trim()
		if (!minecraftUsername) {
			return {
				accountLinked: false,
				knowledge: false,
				charms: false,
				cosmetics: false,
				message: 'No Minecraft username was provided.',
			}
		}

		const user = this.findUserByMinecraftUsername(minecraftUsername)
		if (!user) {
			return {
				accountLinked: false,
				knowledge: false,
				charms: false,
				cosmetics: false,
				message: 'No website account is linked to this Minecraft username yet.',
			}
		}

		return {
			accountLinked: true,
			...this.getUnlockAvailabilityForUser(user.id),
			message: '',
		}
	}

	getUnlockAvailabilityForUser(userId: number): UnlockAvailability {
		return {
			knowledge: this.knowledge.hasRemainingForUser(userId),
			charms: this.hasRemainingForUser(userId, 'charm'),
			cosmetics: this.hasRemainingForUser(userId, 'cosmetic'),
		}
	}

	getTextureFilePath(itemIdInput: string): string {
		const itemId = itemIdInput.trim()
		const item = this.loadCatalog().items.find((candidate) => candidate.id === itemId)
		if (!item?.textureFilePath) {
			throw new NotFoundException('Shop item texture not found.')
		}

		return item.textureFilePath
	}

	getModelFilePath(itemIdInput: string): string {
		const itemId = itemIdInput.trim()
		const item = this.loadCatalog().items.find((candidate) => candidate.id === itemId)
		if (!item?.modelFilePath) {
			throw new NotFoundException('Shop item model not found.')
		}

		return item.modelFilePath
	}

	private isAvailableForPurchase(
		userId: number,
		item: CatalogItem,
		availability: UnlockAvailability,
		unlockedIds: Set<string>,
	): boolean {
		if (item.type === 'charm' || item.type === 'cosmetic') {
			return unlockedIds.has(item.id)
		}

		if (item.bookUnlockType === 'knowledge') {
			return availability.knowledge
		}
		if (item.bookUnlockType === 'charm') {
			return availability.charms
		}
		if (item.bookUnlockType === 'cosmetic') {
			return availability.cosmetics
		}

		return userId > 0
	}

	private unavailablePurchaseMessage(item: CatalogItem): string {
		if (item.type === 'charm') {
			return 'Unlock this charm with a magic book before buying it.'
		}
		if (item.type === 'cosmetic') {
			return 'Unlock this cosmetic with a fashion book before buying it.'
		}

		if (item.bookUnlockType === 'knowledge') {
			return 'You have already unlocked all available knowledge.'
		}
		if (item.bookUnlockType === 'charm') {
			return 'You have already unlocked all available charms.'
		}
		if (item.bookUnlockType === 'cosmetic') {
			return 'You have already unlocked all available cosmetics.'
		}

		return 'This item is not available right now.'
	}

	private hasRemainingForUser(userId: number, type: 'charm' | 'cosmetic'): boolean {
		const items = this.loadCatalog().items.filter((item) => item.type === type)
		if (items.length === 0) {
			return false
		}

		const unlockedIds = this.getUnlockedItemIds(userId, type)
		return items.some((item) => !unlockedIds.has(item.id))
	}

	private isVisibleInShop(item: CatalogItem, unlockedIds: Set<string>): boolean {
		if (item.type === 'charm' || item.type === 'cosmetic') {
			return unlockedIds.has(item.id)
		}

		return true
	}

	private isUnlockedShopItem(item: CatalogItem, unlockedIds: Set<string>): boolean {
		if (item.type === 'charm' || item.type === 'cosmetic') {
			return unlockedIds.has(item.id)
		}

		return true
	}

	private getUnlockedItemIds(userId: number, type?: ShopItemType): Set<string> {
		const rows = this.database.connection.select({ item_id: shopUnlocks.item_id })
			.from(shopUnlocks)
			.where(type
				? and(eq(shopUnlocks.user_id, userId), eq(shopUnlocks.unlock_type, type))
				: eq(shopUnlocks.user_id, userId))
			.all()

		return new Set(rows.map((row) => row.item_id))
	}

	private findUserByMinecraftUsername(minecraftUsername: string): UserRow | null {
		return this.database.connection.select().from(users).all()
			.find((user) => user.minecraft_username.localeCompare(minecraftUsername, 'en', { sensitivity: 'base' }) === 0) ?? null
	}

	private pickWeightedRandomItem(items: CatalogItem[]): CatalogItem {
		const totalWeight = items.reduce((total, item) => total + item.unlockWeight, 0)
		let remainingWeight = randomInt(totalWeight)

		for (const item of items) {
			remainingWeight -= item.unlockWeight
			if (remainingWeight < 0) {
				return item
			}
		}

		return items[items.length - 1]!
	}

	private async purchaseFromMod(minecraftUsername: string, item: CatalogItem, priceDabloons: number): Promise<PurchaseShopItemResponse> {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).PurchaseShopItem

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: PurchaseShopItem')
		}

		return await new Promise<PurchaseShopItemResponse>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				item_id: item.deliveryItemId,
				price_dabloons: priceDabloons,
				delivery_kind: item.deliveryKind,
				unix_ms: Date.now(),
			}, (error: grpc.ServiceError | null, response: PurchaseShopItemResponse) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}

	private getGameplayControlClient() {
		if (this.gameplayControlClient) {
			return this.gameplayControlClient
		}

		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)

		return this.gameplayControlClient
	}

	private loadCatalog(): CachedShopCatalog {
		const root = process.env.SHOP_ITEM_ROOT ?? DEFAULT_ITEM_ROOTS.find((candidate) => existsSync(candidate)) ?? DEFAULT_ITEM_ROOTS[0]!

		if (!existsSync(root)) {
			return {
				root,
				mtimeMs: 0,
				items: [],
			}
		}

		const mtimeMs = this.getTreeMtimeMs(root)
		if (this.cached && this.cached.root === root && this.cached.mtimeMs === mtimeMs) {
			return this.cached
		}

		const items = this.readCatalogItems(root).sort((left, right) => {
			const typeCompare = left.type.localeCompare(right.type, 'en')
			if (typeCompare !== 0) return typeCompare
			return left.title.localeCompare(right.title, 'en')
		})

		this.cached = {
			root,
			mtimeMs,
			items,
		}

		return this.cached
	}

	private readCatalogItems(root: string): CatalogItem[] {
		const items: CatalogItem[] = []
		for (const filePath of this.findItemJsonFiles(root)) {
			const parsed = JSON.parse(readFileSync(filePath, 'utf8')) as FakeItemDefinition
			const item = this.parseCatalogItem(parsed, dirname(filePath), root)
			if (item) {
				items.push(item)
			}
		}

		return items
	}

	private parseCatalogItem(json: FakeItemDefinition, directory: string, root: string): CatalogItem | null {
		const id = typeof json.id === 'string' ? json.id : ''
		const title = typeof json.title === 'string' ? json.title : ''
		const modelType = typeof json.modelType === 'string' ? json.modelType : 'basic'
		const rarity = typeof json.rarity === 'string' ? json.rarity : 'common'
		const shop = this.parseShopPurchasable(json.shopPurchasable)

		if (!id || !title || !shop) {
			return null
		}

		const type: ShopItemType = json.equippableCharm && typeof json.equippableCharm === 'object'
			? 'charm'
			: json.equippableCosmetic && typeof json.equippableCosmetic === 'object'
				? 'cosmetic'
				: 'generic'

		const modelFilePath = this.findModelFilePath(directory)
		const modelTextureFilePath = this.findModelTextureFilePath(directory)
		const flatTextureFilePath = this.findFlatTextureFilePath(id, directory, root)
		const canRenderModel = Boolean(
			modelFilePath
			&& modelTextureFilePath
			&& (modelType === 'basic-3d' || type === 'cosmetic'),
		)
		const renderMode = canRenderModel ? 'model' : 'texture'
		const textureFilePath = canRenderModel
			? modelTextureFilePath
			: flatTextureFilePath ?? modelTextureFilePath

		const baseItemOverride = typeof json.baseItemOverride === 'string' ? json.baseItemOverride : null
		const textureUrl = textureFilePath ? this.shopAssetUrl('texture', id) : null
		const iconUrl = renderMode === 'model'
			? textureUrl
			: textureUrl ?? this.vanillaItemIconUrl(baseItemOverride) ?? this.fallbackIconUrl(type)
		const animation = textureFilePath ? this.readAnimationDefinition(textureFilePath) : null

		const tooltips = Array.isArray(json.tooltips)
			? json.tooltips.filter((tooltip): tooltip is string => typeof tooltip === 'string' && tooltip.trim().length > 0)
			: []

		return {
			id,
			title,
			type,
			modelType,
			rarity,
			priceDabloons: shop.priceDabloons,
			description: shop.description,
			tooltips,
			unlockMessage: shop.unlockMessage,
			unlockWeight: shop.unlockWeight,
			iconUrl,
			renderMode,
			modelUrl: renderMode === 'model' ? this.shopAssetUrl('model', id) : null,
			textureUrl: textureUrl ?? this.vanillaItemIconUrl(baseItemOverride) ?? this.fallbackIconUrl(type),
			animated: Boolean(animation),
			dyeable: Boolean(json.dyeable && typeof json.dyeable === 'object'),
			animation,
			textureFilePath,
			modelFilePath: renderMode === 'model' ? modelFilePath : null,
			deliveryKind: 'fake_item',
			deliveryItemId: id,
			bookUnlockType: this.bookUnlockType(id),
			charmDetails: type === 'charm' ? this.parseCharmDetails(json.charm) : null,
		}
	}

	private parseCharmDetails(value: unknown): CharmDetailsDefinition | null {
		if (!value || typeof value !== 'object') return null
		const candidate = value as { minLevel?: unknown; maxLevel?: unknown; levels?: unknown }
		const minLevel = Number.isInteger(candidate.minLevel) ? Number(candidate.minLevel) : 0
		const rawLevels = Array.isArray(candidate.levels) ? candidate.levels : []
		const levels = rawLevels.flatMap((raw): CharmLevelDefinition[] => {
			if (!raw || typeof raw !== 'object') return []
			const level = raw as {
				level?: unknown
				abilityStatusCurrent?: unknown
				upgradeIngredients?: unknown
			}
			if (!Number.isInteger(level.level)) return []
			return [{
				level: Number(level.level),
				abilityStatusCurrent: typeof level.abilityStatusCurrent === 'string' ? level.abilityStatusCurrent : '',
				upgradeIngredients: Array.isArray(level.upgradeIngredients)
					? level.upgradeIngredients.filter((ingredient): ingredient is string => typeof ingredient === 'string')
					: [],
			}]
		})
		const maxLevel = Number.isInteger(candidate.maxLevel)
			? Number(candidate.maxLevel)
			: Math.max(minLevel, ...levels.map((level) => level.level))

		return { minLevel, maxLevel, levels }
	}

	private getDailyDealDate(): string {
		return new Date().toISOString().slice(0, 10)
	}

	private getDailyDealIds(items: CatalogItem[]): Set<string> {
		const date = this.getDailyDealDate()
		return new Set([...items]
			.sort((left, right) => this.seededRank(`${date}:${left.id}`) - this.seededRank(`${date}:${right.id}`))
			.slice(0, Math.min(this.isShoppingSunday() ? 10 : 5, items.length))
			.map((item) => item.id))
	}

	private getDailyDiscountPercent(itemId: string): number {
		const roll =
			this.seededRank(`${this.getDailyDealDate()}:discount:${itemId}`) % 100

		const divisor = this.isShoppingSunday() ? 2 : 4

		return Math.max(5, Math.ceil(roll / divisor))
	}

	private isShoppingSunday(): boolean {
		return new Date(`${this.getDailyDealDate()}T00:00:00Z`).getUTCDay() === 0
	}

	private getDailyDealMessage(item: CatalogItem): string {
		const index = this.seededRank(`${this.getDailyDealDate()}:message:${item.id}`) % DAILY_DEAL_MESSAGES.length
		return DAILY_DEAL_MESSAGES[index]!.replaceAll('{item}', item.title)
	}

	private discountedPrice(price: number, discountPercent: number): number {
		return Math.max(1, Math.ceil(price * (1 - discountPercent / 100)))
	}

	private seededRank(value: string): number {
		let hash = 2166136261
		for (let index = 0; index < value.length; index++) {
			hash ^= value.charCodeAt(index)
			hash = Math.imul(hash, 16777619)
		}
		return hash >>> 0
	}

	private purchaseFailureMessage(purchase: PurchaseShopItemResponse): string {
		if (!purchase.online) {
			return 'You have to be online on the server to buy from the shop.'
		}

		return 'Purchase failed.'
	}

	private parseShopPurchasable(value: unknown): ShopPurchasableDefinition | null {
		if (!value || typeof value !== 'object') {
			return null
		}

		const candidate = value as Partial<Record<keyof ShopPurchasableDefinition, unknown>>
		const priceDabloons = candidate.priceDabloons
		const description = candidate.description
		const unlockMessage = candidate.unlockMessage
		const unlockWeight = candidate.unlockWeight
		if (
			typeof priceDabloons !== 'number'
			|| !Number.isInteger(priceDabloons)
			|| typeof description !== 'string'
			|| (unlockMessage !== undefined && typeof unlockMessage !== 'string')
			|| typeof unlockWeight !== 'number'
			|| !Number.isInteger(unlockWeight)
			|| unlockWeight < 1
		) {
			return null
		}

		return {
			priceDabloons,
			description,
			unlockMessage: unlockMessage ?? null,
			unlockWeight,
		}
	}

	private findItemJsonFiles(directory: string): string[] {
		const files: string[] = []
		for (const child of readdirSync(directory, { withFileTypes: true })) {
			const childPath = join(directory, child.name)
			if (child.isDirectory()) {
				files.push(...this.findItemJsonFiles(childPath))
			} else if (child.isFile() && child.name === 'item.json') {
				files.push(childPath)
			}
		}

		return files
	}

	private findFlatTextureFilePath(itemId: string, itemDirectory: string, root: string): string | null {
		for (const fileName of ['texture.png', 'model.png']) {
			const filePath = join(itemDirectory, fileName)
			if (existsSync(filePath)) {
				return filePath
			}
		}

		if (itemId === 'charm-wallet') {
			const emptyWalletTexture = join(root, 'wallets', 'wallet-0', 'texture.png')
			if (existsSync(emptyWalletTexture)) {
				return emptyWalletTexture
			}
		}

		return null
	}

	private findModelTextureFilePath(itemDirectory: string): string | null {
		for (const fileName of ['model.png', 'texture.png']) {
			const filePath = join(itemDirectory, fileName)
			if (existsSync(filePath)) {
				return filePath
			}
		}

		return null
	}

	private findModelFilePath(itemDirectory: string): string | null {
		const filePath = join(itemDirectory, 'model.json')
		return existsSync(filePath) ? filePath : null
	}

	private readAnimationDefinition(textureFilePath: string): TextureAnimationDefinition | null {
		const metadataPath = this.findAnimationMetadataPath(textureFilePath)
		if (!metadataPath) {
			return null
		}

		try {
			const parsed = JSON.parse(readFileSync(metadataPath, 'utf8')) as {
				animation?: {
					frametime?: unknown
					frames?: unknown
				}
			}
			const frameTimeTicks = typeof parsed.animation?.frametime === 'number'
				? Math.max(1, parsed.animation.frametime)
				: 1
			const frames = Array.isArray(parsed.animation?.frames)
				? parsed.animation.frames
					.map((frame) => typeof frame === 'number'
						? frame
						: frame && typeof frame === 'object' && typeof (frame as { index?: unknown }).index === 'number'
							? (frame as { index: number }).index
							: null)
					.filter((frame): frame is number => frame !== null && Number.isInteger(frame) && frame >= 0)
				: null

			return {
				frameDelayMs: frameTimeTicks * 50,
				frames: frames && frames.length > 0 ? frames : null,
			}
		} catch {
			return null
		}
	}

	private findAnimationMetadataPath(textureFilePath: string): string | null {
		const directPath = `${textureFilePath}.mcmeta`
		if (existsSync(directPath)) {
			return directPath
		}

		const directory = dirname(textureFilePath)
		for (const child of readdirSync(directory, { withFileTypes: true })) {
			if (child.isFile() && child.name.endsWith('.mcmeta')) {
				return join(directory, child.name)
			}
		}

		return null
	}

	private shopAssetUrl(kind: 'model' | 'texture', itemId: string): string {
		return `/api/shop/${kind}/${encodeURIComponent(itemId)}?v=${SHOP_ASSET_REVISION}`
	}

	private vanillaItemIconUrl(itemId: string | null): string | null {
		if (!itemId?.startsWith('minecraft:')) {
			return null
		}

		return `${VANILLA_ITEM_TEXTURE_PREFIX}/${itemId.slice('minecraft:'.length)}.png`
	}

	private fallbackIconUrl(type: ShopItemType): string | null {
		if (type === 'charm') {
			return `${VANILLA_ITEM_TEXTURE_PREFIX}/amethyst_shard.png`
		}

		return null
	}

	private bookUnlockType(itemId: string): BookUnlockType | null {
		if (itemId === 'charm-knowledge-book') return 'knowledge'
		if (itemId === 'charm-magic-book') return 'charm'
		if (itemId === 'charm-fashion-book') return 'cosmetic'
		return null
	}

	private normalizeUnlockType(value: string): 'charm' | 'cosmetic' | null {
		const normalized = value.trim().toLowerCase()
		if (normalized === 'charm' || normalized === 'charms') return 'charm'
		if (normalized === 'cosmetic' || normalized === 'cosmetics') return 'cosmetic'
		return null
	}

	private getTreeMtimeMs(path: string): number {
		const stats = statSync(path)
		let mtimeMs = stats.mtimeMs

		if (!stats.isDirectory()) {
			return mtimeMs
		}

		for (const child of readdirSync(path, { withFileTypes: true })) {
			if (child.name.startsWith('.')) continue
			mtimeMs = Math.max(mtimeMs, this.getTreeMtimeMs(join(path, child.name)))
		}

		return mtimeMs
	}

	private noShopUnlock(message: string): ShopUnlockResponse {
		return {
			unlocked: false,
			all_unlocked: false,
			knowledge_id: '',
			unlocked_id: '',
			priority: 0,
			topic: '',
			message,
		}
	}
}
