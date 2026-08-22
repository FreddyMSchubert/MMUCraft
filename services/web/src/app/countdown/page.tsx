import { LaunchGate } from '@/components/launch/launch-gate';
import { getSiteVisuals } from '@/lib/site-assets';

export const dynamic = 'force-dynamic';

export default function CountdownPage() {
	return (
		<LaunchGate
			{...getSiteVisuals()}
			discordUrl={process.env.DISCORD_URL ?? ''}
			instagramUrl={process.env.INSTAGRAM_URL ?? ''}
		/>
	);
}
