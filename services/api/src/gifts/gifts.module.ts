import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ClaimsModule } from '../claims/claims.module';
import { DatabaseModule } from '../database/database.module';
import { PlayersModule } from '../players/players.module';
import { AdminController } from './admin.controller';
import { GiftsController } from './gifts.controller';
import { CountdownsController } from './countdowns.controller';
import { CountdownsService } from './countdowns.service';
import { GiftCodeAdministrationService } from './gift-code-administration.service';
import { GiftCodeRedemptionService } from './gift-code-redemption.service';
import { PlayerRoleAdministrationService } from './player-role-administration.service';

@Module({
	imports: [AuthModule, ClaimsModule, DatabaseModule, PlayersModule],
	controllers: [AdminController, CountdownsController, GiftsController],
	providers: [
		CountdownsService,
		GiftCodeAdministrationService,
		GiftCodeRedemptionService,
		PlayerRoleAdministrationService,
	],
})
export class GiftsModule {}
