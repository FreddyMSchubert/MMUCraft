import { Injectable } from '@nestjs/common'
import { DatabaseService } from '../database/database.service'

export interface Joke {
    id: number
    joke: string
}

@Injectable()
export class JokesService {
    constructor(private readonly database: DatabaseService) {}

    findById(id: number): Joke | null {
        const row = this.database.connection
            .prepare('SELECT id, joke FROM jokes WHERE id = ?')
            .get(id) as Joke | undefined

        return row ?? null
    }
}