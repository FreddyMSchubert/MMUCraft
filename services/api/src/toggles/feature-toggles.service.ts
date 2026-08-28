import {
	BadRequestException,
	Injectable,
	Logger,
	NotFoundException,
	OnModuleDestroy,
} from '@nestjs/common';
import { asc, eq } from 'drizzle-orm';
import { DatabaseService, featureToggles } from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';

const SYNC_RETRY_MS = 5_000;

export interface FeatureTogglesSnapshot {
	toggles: { key: string; enabled: boolean }[];
}

@Injectable()
export class FeatureTogglesService implements OnModuleDestroy {
	private readonly logger = new Logger(FeatureTogglesService.name);
	private syncChain = Promise.resolve(true);
	private retryTimer: ReturnType<typeof setTimeout> | null = null;

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	onModuleDestroy() {
		if (this.retryTimer) clearTimeout(this.retryTimer);
	}

	getSnapshot(): FeatureTogglesSnapshot {
		return {
			toggles: this.database.connection
				.select()
				.from(featureToggles)
				.orderBy(asc(featureToggles.key))
				.all()
				.map((toggle) => ({ key: toggle.key, enabled: toggle.enabled === 1 })),
		};
	}

	async set(key: string, enabledInput: unknown) {
		if (typeof enabledInput !== 'boolean')
			throw new BadRequestException('enabled must be a boolean');
		const existing = this.database.connection
			.select({ key: featureToggles.key })
			.from(featureToggles)
			.where(eq(featureToggles.key, key))
			.get();
		if (!existing) throw new NotFoundException('Feature toggle not found');

		this.database.connection
			.update(featureToggles)
			.set({ enabled: enabledInput ? 1 : 0 })
			.where(eq(featureToggles.key, key))
			.run();

		return { ...this.getSnapshot(), minecraftSynced: await this.synchronize() };
	}

	private synchronize() {
		const attempt = this.syncChain.then(() => this.pushSnapshot());
		this.syncChain = attempt.catch(() => false);
		return attempt;
	}

	private async pushSnapshot() {
		try {
			const response = await this.minecraft.gameplay<{ applied: boolean }>(
				'ApplyFeatureToggles',
				this.getSnapshot(),
			);
			if (!response.applied) throw new Error('Minecraft refused the feature toggles');
			if (this.retryTimer) clearTimeout(this.retryTimer);
			this.retryTimer = null;
			return true;
		} catch (error) {
			this.logger.warn(`Could not synchronize feature toggles; retrying: ${String(error)}`);
			this.scheduleRetry();
			return false;
		}
	}

	private scheduleRetry() {
		if (this.retryTimer) return;
		this.retryTimer = setTimeout(() => {
			this.retryTimer = null;
			void this.synchronize();
		}, SYNC_RETRY_MS);
		this.retryTimer.unref();
	}
}
