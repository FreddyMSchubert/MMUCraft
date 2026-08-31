import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
	testDir: './playwright',
	outputDir: './test-results',
	workers: 1,
	reporter: 'list',
	use: {
		baseURL: 'http://127.0.0.1:3100',
		launchOptions: {
			executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
		},
		trace: 'retain-on-failure',
	},
	projects: [
		{
			name: 'chrome',
			use: { ...devices['Desktop Chrome'] },
		},
	],
});
