import { Module } from '@nestjs/common'
import { DatabaseModule } from './database/database.module'
import { JokesModule } from './jokes/jokes.module'

@Module({
    imports: [DatabaseModule, JokesModule],
})
export class AppModule {}