import { Controller, Get, NotFoundException, Param, ParseIntPipe } from '@nestjs/common'
import { JokesService } from './jokes.service'

@Controller('jokes')
export class JokesController {
    constructor(private readonly jokesService: JokesService) {}

    @Get(':id')
    getById(@Param('id', ParseIntPipe) id: number) {
        const joke = this.jokesService.findById(id)

        if (!joke) {
            throw new NotFoundException('joke not found')
        }

        return joke
    }
}