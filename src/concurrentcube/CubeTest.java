package concurrentcube;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class CubeTest {

    private static Random random = new Random();

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

    private void checkNumberOfColors(Cube cube, int size) {
        String cubeState = "";
        try {
            cubeState = cube.show();
        } catch (InterruptedException e) {
            System.err.println("test interrupted");
        }

        int[] colorCount = new int[6];
        for (int i = 0; i < cubeState.length(); i++) {
            colorCount[cubeState.charAt(i)- '0']++;
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(size * size, colorCount[i]);
        }
    }

    public void randomScramblingAndSolving(int size, int rotations) {
        int[] side = new int[rotations];
        int[] layer = new int[rotations];
        int[] inverseSide = new int[rotations];
        int[] inverseLayer = new int[rotations];

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
                checkNumberOfColors(cube, size);
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

        randomScramblingAndSolving(50, 200);
        randomScramblingAndSolving(51, 200);
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
            checkNumberOfColors(sequentialCube, size);
        }
        catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    // Funkcja zwraca bezpieczną kostkę. Podczas wykonania operacji na bezpiecznej kostce zostanie zwrócony błąd,
    // jeśli bezpieczeństwo nie jest zachowane, tzn. pracują jednocześnie dwa wykluczające się wątki. Kostka
    // sprawdza bezpieczeństwo bezpośrednio z definicji bezpieczeństwa dla tego problemu.
    private Cube getNewSecureCube(int size) {
        // blockCounter[i][j][k] - ile wątków aktualnie obraca blokiem (i, j, k)
        // i - wysokość (rośnie w dół), j - szerokość (rośnie w prawo), k - głębokość (rośnie w głąb)
        // patrząc od strony przedniej ściany, gdy górna ściana jest na górze
        int[][][] blockCounter = new int[size][size][size];
        // liczba procesów aktualnie pokazujących stan kostki
        AtomicInteger showCounter = new AtomicInteger();
        // semafor zapewniający atomowość operacji before i after
        Semaphore cubeMutex = new Semaphore(1);

        Cube cube = new Cube(size,
                (side, layer) -> {
                    cubeMutex.acquireUninterruptibly(); // Uninterruptibly, bo funckje before/after nie mogą się przerwać

                    assertEquals(0, showCounter.get());

                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            if (side == 0) assertEquals(1, ++blockCounter[layer][i][j]);
                            else if (side == 1) assertEquals(1, ++blockCounter[i][layer][j]);
                            else if (side == 2) assertEquals(1, ++blockCounter[i][j][layer]);
                            else if (side == 3) assertEquals(1, ++blockCounter[i][size - layer - 1][j]);
                            else if (side == 4) assertEquals(1, ++blockCounter[i][j][size - layer - 1]);
                            else if (side == 5) assertEquals(1, ++blockCounter[size - layer - 1][i][j]);
                        }
                    }

                    cubeMutex.release();
                },
                (side, layer) -> {
                    cubeMutex.acquireUninterruptibly();

                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            if (side == 0) blockCounter[layer][i][j]--;
                            else if (side == 1) blockCounter[i][layer][j]--;
                            else if (side == 2) blockCounter[i][j][layer]--;
                            else if (side == 3) blockCounter[i][size - layer - 1][j]--;
                            else if (side == 4) blockCounter[i][j][size - layer - 1]--;
                            else if (side == 5) blockCounter[size - layer - 1][i][j]--;
                        }
                    }

                    cubeMutex.release();
                },
                () -> {
                    cubeMutex.acquireUninterruptibly();

                    showCounter.getAndIncrement();

                    for (int i = 0; i < size; i++) {
                        for (int j = 0; j < size; j++) {
                            for (int k = 0; k < size; k++) {
                                assertEquals(0, blockCounter[i][j][j]);
                            }
                        }
                    }

                    cubeMutex.release();
                },
                () -> {
                    cubeMutex.acquireUninterruptibly();

                    showCounter.getAndDecrement();

                    cubeMutex.release();
                }
        );

        return cube;
    }

    private void randomOperation(Cube cube, int size) throws InterruptedException {
        int r = random.nextInt(7);
        if (r == 6) {
            cube.show();
        }
        else {
            cube.rotate(r, random.nextInt(size));
        }
    }

    // Test bezpieczeństwa dla losowych operacji. Jest wykonywany z różnymi parametrami przez funkcję securityTest.
    public void parameterizedSecurityTest(int size, int rotations, int threadsNum) {
        Cube cube = getNewSecureCube(size); // bezpieczeństwo sprawdza kostka
        Thread[] threads = new Thread[threadsNum];
        for (int i = 0; i < threadsNum; i++) {
            threads[i] = new Thread(
                    () -> {
                        for (int j = 0; j < rotations; j++) {
                            try {
                                randomOperation(cube, size);
                            }
                            catch (InterruptedException e) {
                                System.err.println("test interrupted");
                            }
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

        checkNumberOfColors(cube, size);
    }

    // Test bezpieczeństwa dla losowych operacji.
    @Test
    public void securityTest() {
        // mała kostka, dużo wątków
        parameterizedSecurityTest(1, 1000, 10);
        parameterizedSecurityTest(2, 1000, 10);
        parameterizedSecurityTest(3, 1000, 10);
        parameterizedSecurityTest(5, 200, 100);

        // duża kostka, mało wątków
        parameterizedSecurityTest(10, 20000, 1);
        parameterizedSecurityTest(10, 10000, 2);
        parameterizedSecurityTest(10, 5000, 3);

        // rozmiar kostki podobny do ilości wątków
        parameterizedSecurityTest(2, 20000, 2);
        parameterizedSecurityTest(3, 10000, 3);
        parameterizedSecurityTest(10, 3000, 10);
        parameterizedSecurityTest(20, 2000, 20);
        parameterizedSecurityTest(20, 3000, 10);
        parameterizedSecurityTest(10, 3000, 20);
    }

    // Test sprawdzający obsługę przerwań w losowym momencie. Sprawdzane jest potencjalne pojawianie się zaklaszczeń
    // (wtedy test nie skończy się wykonywać). Ponadto test sprawdza, czy jeśli została wykonana metoda beforeRotaion(),
    // to mimo przerwania wątku, funkcja rotate() wykonała się poprawnie do końca. Podobnie z beforeShowing() dla show().
    @Test
    public void interruptionHandlingTest() {
        int size = 1;
        int trials = 100;
        int threadsNum = 10;

        for (int trial = 0; trial < trials; trial++) {
            AtomicInteger beforeRotationCounter = new AtomicInteger();
            AtomicInteger afterRotationCounter = new AtomicInteger();
            AtomicInteger beforeShowingCounter = new AtomicInteger();
            AtomicInteger afterShowingCounter = new AtomicInteger();
            Cube cube = new Cube(size,
                    (x, y) -> { beforeRotationCounter.incrementAndGet(); },
                    (x, y) -> { afterRotationCounter.incrementAndGet(); },
                    beforeShowingCounter::incrementAndGet,
                    afterShowingCounter::incrementAndGet
            );

            Thread[] threads = new Thread[threadsNum]; // // Wątki wykonujące rotate(0, 0) i show() w nieskończonej pętli.
            for (int i = 0; i < threadsNum; i++) {
                threads[i] = new Thread(
                        () -> {
                            while (true) {
                                try {
                                    if (random.nextInt(4) < 3) { // 3 na 4 operacje to rotate(0, 0)
                                        cube.rotate(0, 0);
                                    }
                                    else {
                                        cube.show();
                                    }
                                }
                                catch (InterruptedException e) {
                                    break;
                                }
                            }
                        }
                );
            }

            for (int i = 0; i < threadsNum; i++) {
                threads[i].start();
            }

            for (int i = 0; i < threadsNum; i++) {
                // Dajemy wątkom chwilę popracować.
                int time = random.nextInt(1000);
                while (time > 0) {
                    time--;
                }

                threads[i].interrupt(); //  Przerywamy kolejny wątek w losowym (prawie) momencie.
            }

            for (int i = 0; i < threadsNum; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                    System.err.println("test interrupted");
                }
            }

            // Sprawdzenie, czy metody before i after wykonały się tyle samo razy mimo przerwań.
            assertEquals(beforeRotationCounter.get(), afterRotationCounter.get());
            assertEquals(beforeShowingCounter.get(), afterShowingCounter.get());

            // Sprawdzenie, czy rotacje wykonały się poprawnie mimo przerwań.
            try {
                if (beforeRotationCounter.get() % 4 == 0) assertEquals("012345", cube.show());
                if (beforeRotationCounter.get() % 4 == 1) assertEquals("023415", cube.show());
                if (beforeRotationCounter.get() % 4 == 2) assertEquals("034125", cube.show());
                if (beforeRotationCounter.get() % 4 == 3) assertEquals("041235", cube.show());
            }
            catch (InterruptedException e) {
                System.err.println("test interrupted");
            }

            checkNumberOfColors(cube, size);
        }
    }

    // Test sprawdzający obslugę przerwań i bezpieczeństwo wywoływany przez metodę interruptionHandlingAndSecurityTest().
    public void parameterizedInterruptionHandlingAndSecurityTest(int size, int threadsNum) {
        Cube cube = getNewSecureCube(size);

        Thread[] threads = new Thread[threadsNum]; // Wątki wykonujące losowe operacje w nieskończonej pętli.
        for (int i = 0; i < threadsNum; i++) {
            threads[i] = new Thread(
                    () -> {
                        while (true) {
                            try {
                                randomOperation(cube, size);
                            }
                            catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
            );
        }

        for (int i = 0; i < threadsNum; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threadsNum; i++) {
            // Dajemy wątkom chwilę popracować.
            int time = random.nextInt(1000);
            while (time > 0) {
                time--;
            }

            threads[i].interrupt(); //  Przerywamy kolejny wątek w losowym (prawie) momencie.
        }

        for (int i = 0; i < threadsNum; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.err.println("test interrupted");
            }
        }

        checkNumberOfColors(cube, size);
    }

    // Ostateczny test sprawdzający obsługę przerwań i bezpieczeństwo jednocześnie. Wątki wykonują losowe operacje
    // w nieskończonej pętli i zostają przerywane w losowym momencie. Test sprawdza, czy przerwania nie psują
    // bezpieczeństwa kostki. Test nie zakończy się, jeśli dojdzie do zakleszczenia.
    @Test
    public void interruptionHandlingAndSecurityTest() {
        // mała kostka, dużo wątków
        parameterizedInterruptionHandlingAndSecurityTest(1, 20);
        parameterizedInterruptionHandlingAndSecurityTest(2, 20);
        parameterizedInterruptionHandlingAndSecurityTest(3, 20);
        parameterizedInterruptionHandlingAndSecurityTest(5, 200);
        parameterizedInterruptionHandlingAndSecurityTest(3, 1000);

        // duża kostka, mało wątków
        parameterizedInterruptionHandlingAndSecurityTest(10, 1);
        parameterizedInterruptionHandlingAndSecurityTest(10, 5);
        parameterizedInterruptionHandlingAndSecurityTest(50, 20);
        parameterizedInterruptionHandlingAndSecurityTest(100, 10);
        parameterizedInterruptionHandlingAndSecurityTest(500, 100);

        // duża kostka, jeszcze więcej wątków
        parameterizedInterruptionHandlingAndSecurityTest(30, 50);
        parameterizedInterruptionHandlingAndSecurityTest(30, 200);
        parameterizedInterruptionHandlingAndSecurityTest(50, 100);
        parameterizedInterruptionHandlingAndSecurityTest(100, 2000);
        parameterizedInterruptionHandlingAndSecurityTest(200, 5000);
    }

}
