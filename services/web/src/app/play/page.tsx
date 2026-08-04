import { LaunchGate } from '@/components/launch/launch-gate'
import { SiteShell } from '@/components/site-shell'
import { LAUNCH_TIME } from '@/lib/launch'
import { getSiteVisuals } from '@/lib/site-assets'

export const dynamic = 'force-dynamic'

export default function PlayPage() {
	const visuals = getSiteVisuals()
	// Server-request time is intentionally the switch between the launch gate and live app.
	// eslint-disable-next-line react-hooks/purity
	if (Date.now() >= LAUNCH_TIME) return <SiteShell {...visuals} />
	return <LaunchGate {...visuals} discordUrl={process.env.DISCORD_URL ?? ''} instagramUrl={process.env.INSTAGRAM_URL ?? ''} />
}
