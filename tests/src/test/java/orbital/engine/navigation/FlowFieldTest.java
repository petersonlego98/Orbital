package orbital.engine.navigation;

import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Correctness and parallel-agreement tests for the navigation flow field. */
class FlowFieldTest{

    @Test
    void distanceIsFourConnectedFromTheGoal(){
        FlowField field = new FlowField(5, 5);
        assertEquals(25, field.computeDistance(2, 2));

        assertEquals(0, field.distanceAt(2, 2));
        assertEquals(1, field.distanceAt(2, 3));
        assertEquals(2, field.distanceAt(0, 2));
        assertEquals(4, field.distanceAt(0, 0));
    }

    @Test
    void obstaclesAreSkippedAndCanDisconnectCells(){
        FlowField field = new FlowField(3, 3);
        for(int y = 0; y < 3; y++){
            field.setBlocked(1, y, true);
        }

        //the left column is cut off from the goal on the right
        assertEquals(3, field.computeDistance(2, 1));
        assertEquals(FlowField.unreachable, field.distanceAt(0, 0));
        assertEquals(FlowField.unreachable, field.distanceAt(1, 1));
    }

    @Test
    void flowPointsTowardDecreasingDistance(){
        FlowField field = new FlowField(7, 7);
        field.computeDistance(6, 6);
        field.computeFlowSequential();

        for(int y = 0; y < 7; y++){
            for(int x = 0; x < 7; x++){
                if(x == 6 && y == 6) continue;
                int here = field.distanceAt(x, y);
                int nx = x + (int)field.flowXAt(x, y);
                int ny = y + (int)field.flowYAt(x, y);

                assertTrue(field.inside(nx, ny), "flow must stay inside the grid");
                assertTrue(field.distanceAt(nx, ny) < here,
                    "flow at (" + x + "," + y + ") must reduce the distance");
            }
        }

        assertEquals(0f, field.flowXAt(6, 6), 0f, "the goal has no flow");
        assertEquals(0f, field.flowYAt(6, 6), 0f);
    }

    @Test
    void parallelFlowEqualsTheSequentialField(){
        FlowField expected = new FlowField(64, 64);
        for(int y = 10; y < 50; y++){
            for(int x = 20; x < 30; x++){
                expected.setBlocked(x, y, true);
            }
        }
        expected.computeDistance(63, 63);
        expected.computeFlowSequential();

        FlowField actual = new FlowField(64, 64);
        for(int y = 10; y < 50; y++){
            for(int x = 20; x < 30; x++){
                actual.setBlocked(x, y, true);
            }
        }
        actual.computeDistance(63, 63);
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 256)){
            actual.computeFlow(jobs);
        }

        for(int y = 0; y < 64; y++){
            for(int x = 0; x < 64; x++){
                assertEquals(expected.distanceAt(x, y), actual.distanceAt(x, y));
                assertEquals(expected.flowXAt(x, y), actual.flowXAt(x, y), 0f);
                assertEquals(expected.flowYAt(x, y), actual.flowYAt(x, y), 0f);
            }
        }
    }
}