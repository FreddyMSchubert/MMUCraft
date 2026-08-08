import { redirect } from 'next/navigation'
import { LaunchGate } from '@/components/launch/launch-gate'
import { LAUNCH_TIME } from '@/lib/launch'
import { getSiteVisuals } from '@/lib/site-assets'

export const dynamic = 'force-dynamic'

export default function CountdownPage() {
	// Server-request time keeps the expired countdown out of browser history.
	// eslint-disable-next-line react-hooks/purity
	if (Date.now() >= LAUNCH_TIME) redirect('/play')

	return <LaunchGate
		{...getSiteVisuals()}
		discordUrl={process.env.DISCORD_URL ?? ''}
		instagramUrl={process.env.INSTAGRAM_URL ?? ''}
	/>
}
