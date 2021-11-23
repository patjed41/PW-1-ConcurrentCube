package concurrentcube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;

import javax.naming.InitialContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CubeTest {

    private String getUnchangedCube(int size) {
        StringBuilder result = new StringBuilder();
        for (int side = 0; side < 6; side++) {
            result.append(String.valueOf(side).repeat(size * size));
        }
        return result.toString();
    }

    private void rotateRepeatedly(Cube cube, int side, int layer, int repeats) throws InterruptedException {
        for (int i = 0; i < repeats; i++) {
            cube.rotate(side, layer);
        }
    }

    @Test // test of correctness for size == 1
    public void edgeCase1() {
        Cube cube = new Cube(1,
                (x, y) -> { ; },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        try {
            String unchangedCube = getUnchangedCube(1);

            rotateRepeatedly(cube, 0, 0, 4);
            assertEquals(unchangedCube, cube.show());

            rotateRepeatedly(cube, 1, 0, 4);
            assertEquals(unchangedCube, cube.show());

            rotateRepeatedly(cube, 2, 0, 4);
            assertEquals(unchangedCube, cube.show());

            rotateRepeatedly(cube, 3, 0, 4);
            assertEquals(unchangedCube, cube.show());

            rotateRepeatedly(cube, 4, 0, 4);
            assertEquals(unchangedCube, cube.show());

            rotateRepeatedly(cube, 5, 0, 4);
            assertEquals(unchangedCube, cube.show());
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }

        assertEquals(1, 1);
    }

    public void randomScramblingAndSolving(int size, int rotations) {
        int[] side = new int[rotations];
        int[] layer = new int[rotations];
        int[] inverseSide = new int[rotations];
        int[] inverseLayer = new int[rotations];

        Random random = new Random();
        for (int i = 0; i < rotations; i++) {
            side[i] = random.nextInt(6);

            if (side[i] == 0) inverseSide[i] = 5;
            else if (side[i] == 1) inverseSide[i] = 3;
            else if (side[i] == 2) inverseSide[i] = 4;
            else if (side[i] == 3) inverseSide[i] = 1;
            else if (side[i] == 4) inverseSide[i] = 2;
            else inverseSide[i] = 0;

            layer[i] = random.nextInt(size);
            inverseLayer[i] = size - layer[i] - 1;
        }

        Cube cube = new Cube(size,
                (x, y) -> { ; },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        try {
            String[] states = new String[rotations];
            for (int i = 0; i < rotations; i++) {
                states[i] = cube.show();
                cube.rotate(side[i], layer[i]);
            }

            for (int i = rotations - 1; i >= 0; i--) {
                cube.rotate(inverseSide[i], inverseLayer[i]);
                assertEquals(states[i], cube.show());
            }
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    @Test // big random test of correctness
    public void random() {
        for (int size = 1; size < 10; size++) {
            randomScramblingAndSolving(size, 1000);
        }

        randomScramblingAndSolving(50, 10);
        randomScramblingAndSolving(51, 10);
    }

    public static class Pair<T1, T2> {

        public final T1 st;
        public final T2 nd;

        public Pair(T1 st, T2 nd) {
            this.st = st;
            this.nd = nd;
        }

    }

    @Test // big random test of concurrent correctness
    public void randomConcurrentRotations() {
        int size = 4;
        int rotations = 10000;

        int[] side = new int[rotations];
        int[] layer = new int[rotations];

        Random random = new Random();
        for (int i = 0; i < rotations; i++) {
            side[i] = random.nextInt(6);
            layer[i] = random.nextInt(size);
        }

        List<Pair<Integer, Integer>> pairs = Collections.synchronizedList(new ArrayList<>());

        Cube concurrentCube = new Cube(size,
                (x, y) -> { pairs.add(new Pair<>(x, y)); },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            int start = i;
            threads[i] = new Thread(
                    () -> {
                        try {
                            for (int j = start; j < rotations; j += 10) {
                                concurrentCube.rotate(side[j], layer[j]);
                            }
                        }
                        catch (InterruptedException e) {
                            System.err.println("test interrupted");
                        }
                    }
            );
        }

        for (int i = 0; i < 10; i++) {
            threads[i].start();
        }

        Cube sequentialCube = new Cube(size,
                (x, y) -> { ; },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        try {
            for (int i = 0; i < 10; i++) {
                threads[i].join();
            }
            for (int i = 0; i < rotations; i++) {
                sequentialCube.rotate(pairs.get(i).st, pairs.get(i).nd);
            }
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }

        try {
            assertEquals(sequentialCube.show(), concurrentCube.show());
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    @Test // test of security
    public void securityTest() {
        int size = 10;

        AtomicInteger[][][] block = new AtomicInteger[size][size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    block[i][j][k] = new AtomicInteger();
                }
            }
        }

        AtomicInteger showCounter = new AtomicInteger();

        Cube cube = new Cube(size,
                (side, layer) -> {
                    assertEquals(0, showCounter.get());

                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            if (side == 0) assertEquals(1, block[layer][i][j].incrementAndGet());
                            else if (side == 1) assertEquals(1, block[i][layer][j].incrementAndGet());
                            else if (side == 2) assertEquals(1, block[i][j][layer].incrementAndGet());
                            else if (side == 3) assertEquals(1, block[i][size - layer - 1][j].incrementAndGet());
                            else if (side == 4) assertEquals(1, block[i][j][size - layer - 1].incrementAndGet());
                            else if (side == 5) assertEquals(1, block[size - layer - 1][i][j].incrementAndGet());
                        }
                    }
                },
                (side, layer) -> {
                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            if (side == 0) block[layer][i][j].decrementAndGet();
                            else if (side == 1) block[i][layer][j].decrementAndGet();
                            else if (side == 2) block[i][j][layer].decrementAndGet();
                            else if (side == 3) block[i][size - layer - 1][j].decrementAndGet();
                            else if (side == 4) block[i][j][size - layer - 1].decrementAndGet();
                            else if (side == 5) block[size - layer - 1][i][j].decrementAndGet();
                        }
                    }
                },
                () -> {
                    showCounter.incrementAndGet();

                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            for (int k = 0; k < size; k++) {
                                assertEquals(0, block[i][j][j].get());
                            }
                        }
                    }
                },
                showCounter::decrementAndGet
        );

        int rotations = 10000;
        int threadsNum = 20;
        Random random = new Random();

        Thread[] threads = new Thread[threadsNum];
        for (int i = 0; i < threadsNum; i++) {
            threads[i] = new Thread(
                    () -> {
                        try {
                            for (int j = 0; j < rotations; j++) {
                                int r = random.nextInt(7);
                                if (r == 6) {
                                    cube.show();
                                }
                                else {
                                    cube.rotate(r, random.nextInt(size));
                                }
                            }
                        }
                        catch (InterruptedException e) {
                            System.err.println("test interrupted");
                        }
                    }
            );
        }

        for (int i = 0; i < threadsNum; i++) {
            threads[i].start();
        }

        try {
            for (int i = 0; i < threadsNum; i++) {
                threads[i].join();
            }
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    @Test // finishing rotation after interruption
    public void interruptionHandlingTest1() {
        int size = 3;

        Cube interruptedCube = new Cube(size,
                (x, y) -> { Thread.currentThread().interrupt(); },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        Cube uninterruptedCube = new Cube(size,
                (x, y) -> { ; },
                (x, y) -> { ; },
                () -> { ; },
                () -> { ; }
        );

        AtomicBoolean exceptionThrown = new AtomicBoolean(false);

        Thread interruptedThread = new Thread(
                () -> {
                    try {
                        interruptedCube.rotate(0, 0);
                    }
                    catch (InterruptedException e) {
                        exceptionThrown.set(true);
                    }
                }
        );

        interruptedThread.start();

        try {
            uninterruptedCube.rotate(0, 0);
            interruptedThread.join();
            assertTrue(exceptionThrown.get());
            assertEquals(uninterruptedCube.show(), interruptedCube.show());
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    @Test // interruption in random moment
    public void interruptionHandlingTest2() {
        int size = 1;
        int trials = 50;
        int threadsNum = 10;
        Random random = new Random();

        for (int trial = 0; trial < trials; trial++) {
            AtomicInteger rotationCounter = new AtomicInteger();
            Cube cube = new Cube(size,
                    (x, y) -> { rotationCounter.incrementAndGet(); },
                    (x, y) -> { ; },
                    () -> { ; },
                    () -> { ; }
            );

            Thread[] threads = new Thread[threadsNum];
            for (int i = 0; i < threadsNum; i++) {
                threads[i] = new Thread(
                        () -> {
                            boolean exceptionThrown = false;
                            while (!exceptionThrown) {
                                try {
                                    cube.rotate(0, 0);
                                }
                                catch (InterruptedException e) {
                                    exceptionThrown = true;
                                }
                            }
                        }
                );
            }

            for (int i = 0; i < threadsNum; i++) {
                threads[i].start();
            }

            for (int i = 0; i < threadsNum; i++) {
                try {
                    Thread.sleep(random.nextInt(3));
                }
                catch (InterruptedException e) {
                    System.err.println("test interrupted");
                }
                while (threads[i].isAlive()) {
                    threads[i].interrupt();
                }
            }

            for (int i = 0; i < threadsNum; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                    System.err.println("test interrupted");
                }
            }

            try {
                if (rotationCounter.get() % 4 == 0) assertEquals("012345", cube.show());
                if (rotationCounter.get() % 4 == 1) assertEquals("023415", cube.show());
                if (rotationCounter.get() % 4 == 2) assertEquals("034125", cube.show());
                if (rotationCounter.get() % 4 == 3) assertEquals("041235", cube.show());
            }
            catch (InterruptedException e) {
                System.err.println("test interrupted");
            }
        }
    }
}
