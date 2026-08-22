import { Injectable, Logger } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, playerProfiles } from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { effectivePlayerColor } from './player-color';

const ONLINE_PLAYERS_RECONCILE_MS = 5 * 60 * 1000;

@Injectable()
export class OnlinePlayerPresenceService {
	private readonly logger = new Logger(OnlinePlayerPresenceService.name);
	private readonly onlinePlayers = new Map<
		string,
		{ minecraft_username: string; minecraft_uuid: string }
	>();
	private reconciledAt = 0;

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	async listOnlinePlayers() {
		if (Date.now() - this.reconciledAt >= ONLINE_PLAYERS_RECONCILE_MS) await this.reconcile();
		return {
			players: [...this.onlinePlayers.values()]
				.sort((left, right) =>
					left.minecraft_username.localeCompare(right.minecraft_username, 'en', {
						sensitivity: 'base',
					}),
				)
				.map((player) => {
					const presentation = this.discordPresentation(player.minecraft_uuid);
					return {
						minecraftUsername:
							presentation.minecraftUsername || player.minecraft_username,
						color: presentation.colorHex,
						role: presentation.role,
					};
				}),
		};
	}

	discordPresentation(minecraftUuid: string) {
		const user = this.identities.findByUuid(minecraftUuid);
		if (!user)
			return {
				minecraftUsername: '',
				role: 'Player',
				nickname: '',
				pronouns: '',
				colorHex: effectivePlayerColor(minecraftUuid),
			};
		const profile = this.database.connection
			.select()
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, user.id))
			.get();
		return {
			minecraftUsername: user.minecraft_username,
			role:
				user.is_super_admin === 1 || user.is_committee === 1
					? 'Committee'
					: user.is_member === 1
						? 'Member'
						: user.responsible_user_id !== null
							? 'External'
							: 'Player',
			nickname: profile?.preferred_name ?? '',
			pronouns: profile?.pronouns ?? '',
			colorHex: effectivePlayerColor(minecraftUuid, profile?.color_hex),
		};
	}

	recordPresenceEvent(event: {
		type: string;
		minecraft_username: string;
		minecraft_uuid: string;
	}) {
		if (event.type !== 'join' && event.type !== 'first_join' && event.type !== 'leave') return;
		const key = onlinePlayerKey(event.minecraft_uuid, event.minecraft_username);
		if (event.type === 'leave') this.onlinePlayers.delete(key);
		else
			this.onlinePlayers.set(key, {
				minecraft_username: event.minecraft_username,
				minecraft_uuid: event.minecraft_uuid,
			});
	}

	private async reconcile() {
		this.reconciledAt = Date.now();
		try {
			const response = await this.minecraft.gameplay<{
				players: { minecraft_username: string; minecraft_uuid: string }[];
			}>('GetOnlinePlayers', {}, { deadline: Date.now() + 5_000 });
			this.onlinePlayers.clear();
			for (const player of response.players)
				this.onlinePlayers.set(
					onlinePlayerKey(player.minecraft_uuid, player.minecraft_username),
					player,
				);
		} catch (error) {
			this.logger.warn(`Could not reconcile online players with Minecraft: ${String(error)}`);
		}
	}
}

function onlinePlayerKey(minecraftUuid: string, minecraftUsername: string) {
	return minecraftUuid.toLowerCase().replaceAll('-', '') || minecraftUsername.toLowerCase();
}
