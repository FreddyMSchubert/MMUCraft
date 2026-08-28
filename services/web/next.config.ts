import { existsSync } from 'node:fs';
import type { NextConfig } from 'next';

if (existsSync('../../.env')) process.loadEnvFile('../../.env');

const nextConfig: NextConfig = {
	output: 'standalone',
	turbopack: { root: process.cwd() },
	rewrites() {
		return Promise.resolve([
			{
				source: '/grafana/:path*',
				destination: `${process.env.GRAFANA_BASE_URL ?? 'http://grafana:3000'}/grafana/:path*`,
			},
		]);
	},
};

export default nextConfig;
