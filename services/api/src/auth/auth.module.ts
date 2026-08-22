import { Module } from '@nestjs/common';
import { DatabaseModule } from '../database/database.module';
import { AuthController } from './auth.controller';
import { AuthAccountAdministrationService } from './auth-account-administration.service';
import { AuthGrpcService } from './auth-grpc.service';
import { AuthSessionService } from './auth-session.service';
import { AuthSigninService } from './auth-signin.service';
import { AuthVerificationEmailService } from './auth-verification-email.service';
import { AuthUserLookupService } from './auth-user-lookup.service';
import { AuthSignupService } from './auth-signup.service';
import { AuthSignupAccountRegistrationService } from './auth-signup-account-registration.service';
import { PlayerBansService } from './player-bans.service';

@Module({
	imports: [DatabaseModule],
	controllers: [AuthController],
	providers: [
		AuthSignupService,
		AuthSignupAccountRegistrationService,
		AuthAccountAdministrationService,
		AuthGrpcService,
		AuthSessionService,
		AuthSigninService,
		AuthUserLookupService,
		AuthVerificationEmailService,
		PlayerBansService,
	],
	exports: [
		AuthAccountAdministrationService,
		AuthSessionService,
		AuthSigninService,
		AuthSignupService,
	],
})
export class AuthModule {}
