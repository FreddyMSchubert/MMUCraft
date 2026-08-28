'use client';

import { AdminSectionNavigation } from './admin/admin-section-navigation';
import { ClaimAdministrationSection } from './admin/claim-administration-section';
import { CountdownAdminSection } from './admin/countdown-admin-section';
import { DailyRefreshAdminSection } from './admin/daily-refresh-admin-section';
import { DiscordCommandHistoryAdminSection } from './admin/discord-command-history-admin-section';
import { EmailWhitelistAdminSection } from './admin/email-whitelist-admin-section';
import { GiftCodeAdminSection } from './admin/gift-code-admin-section';
import { MemberAccessAdminSection } from './admin/member-access-admin-section';
import { PlayerBanAdminSection } from './admin/player-ban-admin-section';
import { ServerClaimAdministrationSection } from './admin/server-claim-administration-section';
import { useAdminTabController } from './admin/use-admin-tab-controller';
import { VelocityAdminSection } from './admin/velocity-admin-section';
import { FeatureToggleAdminSection } from './admin/feature-toggle-admin-section';

export function AdminTab({ isSuperAdmin, section }: { isSuperAdmin: boolean; section?: string }) {
	const controller = useAdminTabController({ isSuperAdmin, section });

	return (
		<div className="adminPanel">
			<AdminSectionNavigation activeSection={controller.activeSection} />
			<DailyRefreshAdminSection controller={controller} />
			<CountdownAdminSection controller={controller} />
			<DiscordCommandHistoryAdminSection controller={controller} />
			<MemberAccessAdminSection controller={controller} />
			<ClaimAdministrationSection controller={controller} />
			<ServerClaimAdministrationSection controller={controller} />
			<EmailWhitelistAdminSection controller={controller} />
			<PlayerBanAdminSection controller={controller} />
			<GiftCodeAdminSection controller={controller} />
			{controller.activeSection === 'toggles' && <FeatureToggleAdminSection />}
			{(controller.activeSection === 'servers' ||
				controller.activeSection === 'maintenance') && (
				<VelocityAdminSection section={controller.activeSection} />
			)}
			{controller.error && <p className="authError">{controller.error}</p>}
		</div>
	);
}
