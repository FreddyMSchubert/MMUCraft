import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { SiteAlertProvider } from '@/components/site-alert';
import './globals.css';

export const metadata: Metadata = {
	title: 'MMU Minecraft Society',
	description: 'MMU Minecraft Society',
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="en">
			<body>
				<SiteAlertProvider>{children}</SiteAlertProvider>
			</body>
		</html>
	);
}
