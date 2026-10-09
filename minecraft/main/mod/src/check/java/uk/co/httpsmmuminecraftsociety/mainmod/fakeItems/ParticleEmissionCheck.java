package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.ParticleEmissionItemFeature;

public final class ParticleEmissionCheck {
    public static void main(String[] args) {
        var feature = ParticleEmissionItemFeature.of(JsonParser.parseString("""
                {
                  "particles": [{"particle":"minecraft:flame","from":[8,12,8],"to":[8,12,8],"minTicks":1,"maxTicks":1}],
                  "display": {
                    "fixed": {"rotation":[-90,0,0],"translation":[0,0,-14.25],"scale":[2,2,2]},
                    "head": {"translation":[0,17.5,0],"scale":[1.6,1.6,1.6]}
                  }
                }
                """).getAsJsonObject());

        var fixed = feature.transform("fixed");
        double baseHeight = -ParticleEmission.displayPoint(new Vec3(8, 1, 8), fixed).z;
        double wickHeight = -ParticleEmission.displayPoint(new Vec3(8, 12, 8), fixed).z;
        assert close(baseHeight, 0.015625);
        assert close(wickHeight, 1.390625);

        var head = feature.transform("head");
        Vec3 wick = new Vec3(8, 12, 8);
        assert close(ParticleEmission.headPoint(wick, head, 0).y
                - ParticleEmission.headPoint(new Vec3(8, 1, 8), head, 0).y, 0.6875);
        assert ParticleEmission.headPoint(wick, head, 45).z > 0;
        assert ParticleEmission.headPoint(wick, head, -45).z < 0;
        System.out.println("Particle emission transforms passed.");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.00001;
    }
}
