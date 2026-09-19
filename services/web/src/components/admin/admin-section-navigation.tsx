import Link from 'next/link';
import { useSyncExternalStore } from 'react';
import type { AdminSection } from './admin-data.types';

const ADMIN_SECTIONS: { section: AdminSection; label: string }[] = [
	{ section: 'drops', label: 'Drops' },
	{ section: 'members', label: 'Member list' },
	{ section: 'launch', label: 'Launch' },
	{ section: 'announcements', label: 'Announcements' },
	{ section: 'emojis', label: 'Emojis' },
	{ section: 'claims', label: 'Player claims' },
	{ section: 'server-claims', label: 'Server claims' },
	{ section: 'whitelist', label: 'External invites' },
	{ section: 'gifts', label: 'Gift codes' },
	{ section: 'countdowns', label: 'Countdowns' },
	{ section: 'commands', label: 'Command log' },
	{ section: 'signin-attempts', label: 'Sign-in attempts' },
	{ section: 'dailies', label: 'Dailies' },
	{ section: 'toggles', label: 'Gameplay toggles' },
	{ section: 'bans', label: 'Ban / timeout' },
	{ section: 'servers', label: 'Servers' },
	{ section: 'maintenance', label: 'Maintenance' },
];

export function AdminSectionNavigation({ activeSection }: { activeSection: AdminSection }) {
	const hostname = useSyncExternalStore(
		() => () => undefined,
		() => window.location.hostname,
		() => '',
	);
	const isDev = hostname.startsWith('dev.');

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
			<a href="/grafana/" target="_blank" rel="noopener noreferrer">
				Statistics
			</a>
			<a
				href={
					isDev
						? 'https://mmuminecraftsociety.co.uk'
						: 'https://dev.mmuminecraftsociety.co.uk'
				}
				target="_blank"
				rel="noopener noreferrer"
			>
				{isDev ? 'go to prod' : 'go to dev'}
			</a>
		</nav>
	);
}
