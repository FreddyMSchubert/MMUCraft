import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import Link from 'next/link'
import 'dotenv/config'

import './globals.css'

export const metadata: Metadata = {
	title: {
		default: 'MMU Minecraft Society',
		template: '%s | MMU Minecraft Society',
	},
	description: 'Account portal for MMU Minecraft Society players',
}

interface RootLayoutProps {
	children: ReactNode
}

export default function RootLayout({ children }: RootLayoutProps) {
	return (
		<html lang="en">
			<body>
				<div className="shell">
					<header className="topbar">
						<div className="branding">
							<div className="eyebrow">mmu minecraft society</div>
							<h1>Player portal</h1>
						</div>
						<Link className="ghostLink" href="/">
							Home
						</Link>
					</header>
					<main>{children}</main>
				</div>
			</body>
		</html>
	)
}
