import Link from 'next/link';
import type { AdminSection } from './admin-data.types';

const ADMIN_SECTIONS: { section: AdminSection; label: string }[] = [
	{ section: 'members', label: 'Member list' },
	{ section: 'claims', label: 'Player claims' },
	{ section: 'server-claims', label: 'Server claims' },
	{ section: 'whitelist', label: 'Email whitelist' },
	{ section: 'gifts', label: 'Gift codes' },
	{ section: 'countdowns', label: 'Countdowns' },
	{ section: 'discord-commands', label: 'Discord commands' },
	{ section: 'dailies', label: 'Dailies' },
	{ section: 'toggles', label: 'Gameplay toggles' },
	{ section: 'bans', label: 'Ban / timeout' },
	{ section: 'servers', label: 'Servers' },
	{ section: 'maintenance', label: 'Maintenance' },
];

export function AdminSectionNavigation({ activeSection }: { activeSection: AdminSection }) {
	return (
		<nav className="adminSubTabs" aria-label="Admin sections">
			{ADMIN_SECTIONS.map(({ section, label }) => (
				<Link
					key={section}
					className={activeSection === section ? 'active' : ''}
					href={`/play/admin/${section}`}
				>
					{label}
				</Link>
			))}
			<a
				href={
					process.env.NODE_ENV === 'production'
						? 'https://grafana.mmuminecraftsociety.co.uk/'
						: '/grafana/'
				}
				target="_blank"
				rel="noopener noreferrer"
			>
				Statistics
			</a>
		</nav>
	);
}
