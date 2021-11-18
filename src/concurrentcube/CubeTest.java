package concurrentcube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
            System.out.println("test interrupted");
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
            System.out.println("test interrupted");
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
                (x, y) -> { ; },
                (x, y) -> { pairs.add(new Pair<>(x, y)); },
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
                            System.out.println("test interrupted");
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
            System.out.println("test interrupted");
        }

        try {
            assertEquals(sequentialCube.show(), concurrentCube.show());
        }
        catch (InterruptedException e) {
            System.out.println("test interrupted");
        }
    }

}
