import Ajv from 'ajv'
import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const root = path.resolve('public', 'knowledge')
const schema = JSON.parse(await readFile(new URL('../knowledge-recipe.schema.json', import.meta.url), 'utf8'))
const validate = new Ajv({ allErrors: true, strict: true }).compile(schema)
const files = await markdownFiles(root)
let recipeCount = 0
let failed = false

for (const file of files) {
	const markdown = await readFile(file, 'utf8')
	const blocks = [...markdown.matchAll(/^```recipe[ \t]*\r?\n([\s\S]*?)\r?\n```[ \t]*$/gm)]
	const starts = markdown.match(/^```recipe[ \t]*$/gm)?.length ?? 0
	if (starts !== blocks.length) report(file, 0, 'A recipe block is not closed with ``` on its own line.')

	for (const [index, block] of blocks.entries()) {
		recipeCount += 1
		let recipe
		try {
			recipe = JSON.parse(block[1])
		} catch (error) {
			report(file, index + 1, `Invalid JSON: ${error instanceof Error ? error.message : String(error)}`)
			continue
		}

		if (!validate(recipe)) {
			for (const error of validate.errors ?? []) {
				report(file, index + 1, `${error.instancePath || '/'} ${error.message}`)
			}
			continue
		}

		const positions = recipe.inputs.map((item) => item.pos)
		if (new Set(positions).size !== positions.length) report(file, index + 1, 'Each input position must be unique.')
	}
}

if (failed) process.exit(1)
console.log(`Validated ${recipeCount} knowledge recipe${recipeCount === 1 ? '' : 's'} in ${files.length} Markdown files.`)

async function markdownFiles(directory) {
	const entries = await readdir(directory, { withFileTypes: true })
	const nested = await Promise.all(entries.map((entry) => {
		const entryPath = path.join(directory, entry.name)
		return entry.isDirectory() ? markdownFiles(entryPath) : entry.name.endsWith('.md') ? [entryPath] : []
	}))
	return nested.flat().sort()
}

function report(file, recipeIndex, message) {
	failed = true
	const location = recipeIndex ? `${path.relative(process.cwd(), file)} (recipe ${recipeIndex})` : path.relative(process.cwd(), file)
	console.error(`${location}: ${message}`)
}
