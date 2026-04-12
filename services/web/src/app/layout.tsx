import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import './globals.css'

export const metadata: Metadata = {
	title: 'MMU Minecraft Society',
	description: 'MMU Minecraft Society',
}

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="en">
		<body>{children}</body>
		</html>
	)
}
