import { SiteShell } from '@/components/site-shell'
import { getSiteVisuals } from '@/lib/site-assets'

export const dynamic = 'force-dynamic'

export default function PlayPage() {
	return <SiteShell {...getSiteVisuals()} />
}
