import { Module } from '@nestjs/common'
import { DatabaseModule } from '../database/database.module'
import { JokesController } from './jokes.controller'
import { JokesService } from './jokes.service'

@Module({
    imports: [DatabaseModule],
    controllers: [JokesController],
    providers: [JokesService],
})
export class JokesModule {}