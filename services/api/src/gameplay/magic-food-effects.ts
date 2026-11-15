import { eq } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import { type DatabaseService, users } from '../database/database.service';
import { type MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';

export interface ActiveFoodEffects {
	shop_discount_percent: number;
	sunday_money: boolean;
}

export async function activeFoodEffects(
	database: DatabaseService,
	minecraft: MinecraftGrpcClientService,
	user: AuthenticatedUser,
): Promise<ActiveFoodEffects> {
	const uuid = database.connection
		.select({ value: users.minecraft_uuid })
		.from(users)
		.where(eq(users.id, user.id))
		.get()?.value;
	if (!uuid) return { shop_discount_percent: 0, sunday_money: false };
	return minecraft.gameplay<ActiveFoodEffects>('GetActiveFoodEffects', { minecraft_uuid: uuid });
}
