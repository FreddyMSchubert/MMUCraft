import { Module } from '@nestjs/common'
import { DatabaseService } from './database.service'
import { MinecraftIdentityService } from './minecraft-identity.service'

@Module({
    providers: [DatabaseService, MinecraftIdentityService],
    exports: [DatabaseService, MinecraftIdentityService],
})
export class DatabaseModule {}
