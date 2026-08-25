import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

const typedFiles = ['**/*.{ts,tsx}'];

export default tseslint.config(
	{
		ignores: [
			'**/.next/**',
			'**/coverage/**',
			'**/dist/**',
			'**/node_modules/**',
			'**/output/**',
			'services/web/next-env.d.ts',
		],
	},
	js.configs.recommended,
	...tseslint.configs.strictTypeChecked.map((config) => ({ ...config, files: typedFiles })),
	...tseslint.configs.stylisticTypeChecked.map((config) => ({ ...config, files: typedFiles })),
	{
		files: typedFiles,
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'module',
			globals: { ...globals.browser, ...globals.node },
			parserOptions: {
				projectService: {
					allowDefaultProject: [
						'services/api/drizzle.config.ts',
						'tests/playwright.config.ts',
						'tests/playwright/*.ts',
					],
					defaultProject: 'services/api/tsconfig.json',
				},
				tsconfigRootDir: import.meta.dirname,
			},
		},
		rules: {
			'@typescript-eslint/consistent-type-imports': [
				'error',
				{ prefer: 'type-imports', fixStyle: 'inline-type-imports' },
			],
			'@typescript-eslint/no-explicit-any': 'error',
			'@typescript-eslint/no-extraneous-class': ['error', { allowWithDecorator: true }],
			'@typescript-eslint/no-floating-promises': 'error',
			'@typescript-eslint/no-misused-promises': ['error', { checksVoidReturn: false }],
			'@typescript-eslint/no-unnecessary-type-parameters': 'off',
			'@typescript-eslint/no-unnecessary-condition': 'error',
			'@typescript-eslint/no-unused-vars': [
				'error',
				{
					argsIgnorePattern: '^_',
					caughtErrorsIgnorePattern: '^_',
					varsIgnorePattern: '^_',
				},
			],
			'@typescript-eslint/prefer-nullish-coalescing': 'error',
			'@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
			'@typescript-eslint/switch-exhaustiveness-check': 'error',
			'@typescript-eslint/unbound-method': 'off',
		},
	},
	{
		files: ['**/*.js'],
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'commonjs',
			globals: globals.node,
		},
	},
	{
		...tseslint.configs.disableTypeChecked,
		files: [
			'services/api/drizzle.config.ts',
			'tests/playwright.config.ts',
			'tests/playwright/*.ts',
		],
	},
	{
		files: ['**/*.mjs'],
		languageOptions: {
			ecmaVersion: 2022,
			sourceType: 'module',
			globals: globals.node,
		},
	},
);
