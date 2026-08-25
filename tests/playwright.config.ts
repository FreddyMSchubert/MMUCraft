import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
	testDir: './playwright',
	outputDir: './test-results',
	fullyParallel: false,
	workers: 1,
	retries: process.env.CI ? 2 : 0,
	reporter: process.env.CI
		? [['line'], ['html', { open: 'never', outputFolder: './playwright-report' }]]
		: 'list',
	use: {
		baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3100',
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure',
		video: 'retain-on-failure',
	},
	projects: [
		{
			name: 'chromium',
			use: { ...devices['Desktop Chrome'] },
		},
	],
});
