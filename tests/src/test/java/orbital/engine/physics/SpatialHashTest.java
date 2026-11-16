package orbital.engine.physics;

import orbital.engine.memory.UnitData;
import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Tests that the parallel spatial hash is complete and deterministic. */
class SpatialHashTest{

    @Test
    void everyUnitLandsInExactlyOneBucket(){
        Random random = new Random(4444L);
        UnitData units = new UnitData(4000);
        for(int i = 0; i < 4000; i++){
            units.add(i, random.nextFloat() * 800f - 100f, random.nextFloat() * 800f - 100f,
                0f, 0f, 0f, 0f, 0);
        }

        SpatialHash sequential = new SpatialHash(600f, 600f, 32f);
        sequential.build(units, null);
        SpatialHash parallel = new SpatialHash(600f, 600f, 32f);
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 128)){
            parallel.build(units, jobs);
        }

        assertEquals(units.count, sequential.itemCount());
        assertEquals(units.count, parallel.itemCount());

        for(int y = 0; y < parallel.rows(); y++){
            for(int x = 0; x < parallel.cols(); x++){
                int count = parallel.countInCell(x, y);
                assertEquals(sequential.countInCell(x, y), count, "bucket (" + x + "," + y + ")");
                for(int slot = 0; slot < count; slot++){
                    assertEquals(sequential.itemAt(x, y, slot), parallel.itemAt(x, y, slot),
                        "bucket order must not depend on the worker schedule");
                }
            }
        }
    }

    @Test
    void outOfBoundsPositionsClampIntoEdgeCells(){
        UnitData units = new UnitData();
        units.add(1, -500f, -500f, 0f, 0f, 0f, 0f, 0);
        units.add(2, 5000f, 5000f, 0f, 0f, 0f, 0f, 0);

        SpatialHash hash = new SpatialHash(100f, 100f, 10f);
        hash.build(units, null);

        assertEquals(2, hash.itemCount());
        assertEquals(0, hash.cellX(-500f));
        assertEquals(0, hash.cellY(-500f));
        assertEquals(hash.cols() - 1, hash.cellX(5000f));
        assertEquals(hash.rows() - 1, hash.cellY(5000f));
    }
}