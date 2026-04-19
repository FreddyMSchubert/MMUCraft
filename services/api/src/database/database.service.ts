import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { Injectable, OnModuleDestroy } from '@nestjs/common'
import Database from 'better-sqlite3'

interface JokeRow {
    id: number
    joke: string
}

@Injectable()
export class DatabaseService implements OnModuleDestroy {
    private readonly db: Database.Database

    constructor() {
        const dbPath = join(process.cwd(), 'data', 'app.sqlite')

        mkdirSync(dirname(dbPath), { recursive: true })

        this.db = new Database(dbPath)
        this.db.pragma('journal_mode = WAL')

        this.db.exec(`
          CREATE TABLE IF NOT EXISTS jokes (
            id INTEGER PRIMARY KEY,
            joke TEXT NOT NULL
          );
        `)

        const row = this.db
            .prepare('SELECT COUNT(*) AS count FROM jokes')
            .get() as { count: number }

        if (row.count === 0) {
            const jokes: JokeRow[] = [
                { id: 1, joke: 'Why do Java developers wear glasses? Because they do not C#.' },
                { id: 2, joke: 'I told SQLite a joke. It said: sorry, I only support single-user humor.' },
                { id: 3, joke: 'Why did developer go broke? Because he used up all his cache.' },
                { id: 4, joke: 'I would tell you UDP joke, but you might not get it.' },
                { id: 5, joke: 'There are 10 kinds of people: those who understand binary and those who do not.' },
            ]

            const insert = this.db.prepare(
                'INSERT INTO jokes (id, joke) VALUES (?, ?)',
            )

            const seed = this.db.transaction((rows: JokeRow[]) => {
                for (const joke of rows) {
                    insert.run(joke.id, joke.joke)
                }
            })

            seed(jokes)
        }
    }

    get connection(): Database.Database {
        return this.db
    }

    onModuleDestroy() {
        this.db.close()
    }
}