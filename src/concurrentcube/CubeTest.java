// Patryk Jędrzejczak

package concurrentcube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

// Testy zrobiłem tak, żeby działały na students w rozsądnym czasie około 12 sekund. Na students mogłem stworzyć
// maksymalnie około 100 wątków, inaczej dochodziło do przekroczenia limitu stosu. Ponadto nie mogłem tworzyć bardzo
// dużych kostek i wykonywać zbyt wielu operacji, bo testy działały zbyt wolno. W przypadku testowania na swoim
// komputerze można te testy "podkręcić", zwiększając rozmiary kostek, liczbę operacji i liczbę wątków, w szczególności
// w sparametryzowanych testach.

public class CubeTest {

    /***************************************** FUNKCJE I KLASY POMOCNICZE *********************************************/

    private static final Random random = new Random();

    public static class Pair<T1, T2> {

        public final T1 st;
        public final T2 nd;

        public Pair(T1 st, T2 nd) {
            this.st = st;
            this.nd = nd;
        }

    }

    // Sprawdzenie, czy liczba kolorów na kostce jest prawidłowa.
    private void checkNumberOfColors(Cube cube, int size) {
        String cubeState = "";
        try {
            cubeState = cube.show();
        } catch (InterruptedException e) {
            System.err.println("test interrupted");
        }

        int[] colorCount = new int[6];
        for (int i = 0; i < cubeState.length(); i++) {
            colorCount[cubeState.charAt(i) - '0']++;
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(size * size, colorCount[i]);
        }
    }

    private Cube getBasicCube(int size) {
        return new Cube(size,
            (x, y) -> {},
            (x, y) -> {},
            () -> {},
            () -> {}
        );
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

    // Funkcja zwraca bezpieczną kostkę. Podczas wykonania operacji na bezpiecznej kostce zostanie zwrócony błąd,
    // jeśli bezpieczeństwo nie jest zachowane, tzn. pracują jednocześnie dwa wykluczające się wątki. Kostka
    // sprawdza bezpieczeństwo bezpośrednio z definicji bezpieczeństwa dla tego problemu.
    private Cube getSecureCube(int size) {
        // blockCounter[i][j][k] - ile wątków aktualnie obraca blokiem (i, j, k)
        // i - wysokość (rośnie w dół), j - szerokość (rośnie w prawo), k - głębokość (rośnie w głąb)
        // patrząc od strony przedniej ściany, gdy górna ściana jest na górze
        AtomicInteger[][][] blockCounter = new AtomicInteger[size][size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    blockCounter[i][j][k] = new AtomicInteger();
                }
            }
        }
        // liczba procesów aktualnie pokazujących stan kostki
        AtomicInteger showCounter = new AtomicInteger();
        // semafor zapewniający atomowość operacji before i after
        Semaphore cubeMutex = new Semaphore(1);

        Cube cube = new Cube(size,
            (side, layer) -> {
                cubeMutex.acquireUninterruptibly(); // Uninterruptibly, bo funckje before/after nie mogą się przerwać
                assertEquals(0, showCounter.get()); // Ktoś pokazuje podczas rotowania - brak bezpieczeństwa.

                // Dla pewnych i, j, k otrzymaliśmy blockCounter[i][j][k] > 1 - brak bezpieczeństwa.
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        if (side == 0) assertEquals(1, blockCounter[layer][i][j].incrementAndGet());
                        else if (side == 1) assertEquals(1, blockCounter[i][layer][j].incrementAndGet());
                        else if (side == 2) assertEquals(1, blockCounter[i][j][layer].incrementAndGet());
                        else if (side == 3) assertEquals(1, blockCounter[i][size - layer - 1][j].incrementAndGet());
                        else if (side == 4) assertEquals(1, blockCounter[i][j][size - layer - 1].incrementAndGet());
                        else if (side == 5) assertEquals(1, blockCounter[size - layer - 1][i][j].incrementAndGet());
                    }
                }

                cubeMutex.release();
            },
            (side, layer) -> {
                cubeMutex.acquireUninterruptibly();

                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        if (side == 0) blockCounter[layer][i][j].decrementAndGet();
                        else if (side == 1) blockCounter[i][layer][j].decrementAndGet();
                        else if (side == 2) blockCounter[i][j][layer].decrementAndGet();
                        else if (side == 3) blockCounter[i][size - layer - 1][j].decrementAndGet();
                        else if (side == 4) blockCounter[i][j][size - layer - 1].decrementAndGet();
                        else if (side == 5) blockCounter[size - layer - 1][i][j].decrementAndGet();
                    }
                }

                cubeMutex.release();
            },
            () -> {
                cubeMutex.acquireUninterruptibly();

                showCounter.getAndIncrement();

                // Ktoś rotuje podczas pokazywania - brak bezpieczeństwa.
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        for (int k = 0; k < size; k++) {
                            assertEquals(0, blockCounter[i][j][j].get());
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

    // Funkcja uruchamiająca wątki i czekająca aż skończą pracować.
    private void executeThreads(Thread[] threads, int threadsNum) {
        for (int i = 0; i < threadsNum; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threadsNum; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Funkcja uruchamiająca wątki i przerywająca kolejne wątki po losowym czasie.
    private void executeAndInterruptThreads(Thread[] threads, int threadsNum) {
        for (int i = 0; i < threadsNum; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threadsNum; i++) {
            // Dajemy wątkom chwilę popracować.
            try {
                Thread.sleep(random.nextInt(3));
            } catch (InterruptedException e) {
                System.err.println("test interrupted");
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
    }

    /************************************************** TESTY *********************************************************/

    // Prosty, sekwencyjny test poprawnościowy, dający się sprawdzić z kostką w ręku.
    @Test
    public void example() {
        int size = 3;
        Cube cube = getBasicCube(size);

        try {
            cube.rotate(0, 0);
            assertEquals("000000000222111111333222222444333333111444444555555555", cube.show());
            cube.rotate(1, 1);
            assertEquals("040040010222111111303202202444333333151454454535525525", cube.show());
            cube.rotate(2, 2);
            assertEquals("112040010522211511303202202440334330144555144535525334", cube.show());
            cube.rotate(3, 0);
            assertEquals("113042012522211511305205204334334040044055244531525331", cube.show());
            cube.rotate(4, 1);
            assertEquals("113334012522241501305205204354324050044055244531211331", cube.show());
            cube.rotate(5, 2);
            assertEquals("342131130044241501522205204305324050354055244531211331", cube.show());
        } catch (InterruptedException e) {
            System.err.println("test interrupted");
        }
    }

    // Sekwencyjny test poprawnościowy. Najpierw wykonujemy losowe operacje, a potem układamy kostkę używając
    // przeciwnych operacji w odwrotnej kolejności. Po każdej odwrotnej operacji powinniśmy wrócić do stanu kostki
    // o jeden wcześniejszego.
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

        Cube cube = getBasicCube(size);

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

    // Uruchomienie powyższego testu dla różnych parametrów.
    @Test
    public void sequentialCorrectness() {
        for (int size = 1; size < 10; size++) {
            randomScramblingAndSolving(size, 1000);
        }

        randomScramblingAndSolving(50, 200);
        randomScramblingAndSolving(51, 200);
    }

    // Sekwencyjny test poprawnościowy, sprawdzający, czy liczba kolorów zgadza się po wykonaniu wielu losowych operacji.
    // Sprawdza poprawność implementacji rotacji.
    @Test
    public void sequentialNumberOfColors() {
        int size = 3;
        int operations = 1000000;
        Cube cube = getBasicCube(size);

        for (int i = 0; i < operations; i++) {
            try {
                randomOperation(cube, size);
            }
            catch (InterruptedException e) {
                System.err.println("test interrupted");
            }
        }

        checkNumberOfColors(cube, size);
    }

    // Współbieżny test poprawnościowy, sprawdzający, czy liczba kolorów zgadza się po wykonaniu wielu losowych operacji.
    // Sprawdza poprawność współdzielenia kostki przez wątki.
    @Test
    public void concurrentNumberOfColors() {
        int size = 3;
        int operations = 100000;
        int threadsNum = 10;
        Cube cube = getBasicCube(size);

        Thread[] threads = new Thread[threadsNum];
        for (int i = 0; i < threadsNum; i++) {
            threads[i] = new Thread(
                () -> {
                    try {
                        for (int j = 0; j < operations; j ++) {
                            randomOperation(cube, size);
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
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.err.println("test interrupted");
            }
        }

        checkNumberOfColors(cube, size);
    }

    // Współbieżny test poprawnościowy. Wychwytuje sekwencyjną kolejność wykonania współbieżnych rotacji na kostce
    // (dokładnie chodzi o kolejność wywołania beforeRotation) i sprawdza, czy wykonanie tych operacji sekwencyjnie
    // da ten sam wynik. Jeżeli w kostce nie ma bezpieczeństwa lub występują inne błędy związane ze współbieżnością,
    // powinniśmy otrzymać różne wyniki.
    @Test
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
            (x, y) -> pairs.add(new Pair<>(x, y)),
            (x, y) -> {},
            () -> {},
            () -> {}
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

        Cube sequentialCube = getBasicCube(size);

        try {
            for (int i = 0; i < 10; i++) {
                threads[i].join();
            }
            for (int i = 0; i < rotations; i++) { // Tu przy okazji sprawdzamy, czy wszystkie operacje się wykonały.
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

    // Prosty test sprawdzający, czy operacje mogące wykonywać się współbieżnie, wykonują się współbieżnie.
    @Test
    public void concurrentPerformance1() {
        int size = 100;

        CyclicBarrier barrier = new CyclicBarrier(size);

        Cube cube = new Cube(size,
            (x, y) -> {
                try {
                    barrier.await(); // Wszystkie wątki muszą dojść do bariery razem, aby mogły pójść dalej.
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.err.println("test interrupted");
                }
            },
            (x, y) -> {},
            () -> {},
            () -> {}
        );

        Thread[] rotatingThreads = new Thread[size]; // Wątki mogące rotować współbieżnie.
        Thread[] showingThreads = new Thread[size]; // Wątki mogące pokazywać współbieżnie.
        for (int i = 0; i < size; i++) {
            int layer = i;
            rotatingThreads[i] = new Thread(
                () -> {
                    try {
                        cube.rotate(layer % 2 == 0 ? 0 : 5, layer % 2 == 0 ? layer : size - layer - 1);
                    }
                    catch (InterruptedException e) {
                        System.err.println("test interrupted");
                    }
                }
            );

            showingThreads[i] = new Thread(
                () -> {
                    try {
                        cube.show();
                    }
                    catch (InterruptedException e) {
                        System.err.println("test interrupted");
                    }
                }
            );
        }

        // To się zapętli, jeśli implementacja nie jest współbieżna.
        executeThreads(rotatingThreads, size);
        executeThreads(showingThreads, size);
    }

    // Bardziej złożony test sprawdzający, czy wątki działają współbieżnie. Mamy po 10 wątków z każdej z 4 grup
    // (rotujące względem ścian 0 i 5, rotujące względem ścian 1 i 3, rotujące względem ścian 2 i 4, pokazujące).
    // Każdy wątek wykonuje jedną operację. Wszystkie wątki z jednej grupy mogą wykonywać operację współbieżnie.
    // Każdy wątek zasypia w metodzie beforeRotation/beforeShowing na pół sekudny. Widać, że jeśli współbieżność
    // jest zaimplementowana sensownie, to taki test powinien zokończyń się w niewiele ponad 2 sekundy. Każda grupa
    // wątków powinna cała pracować jednocześnie, ewentualnie w dwóch turach.
    @Test
    public void concurrentPerformance2() {
        // Limit to 4 sekundy, żeby test zadziałał nawet dla wyjątkowo dziwnych przeplotów. Chodzi tylko o to,
        // żeby czas był zdecydowanie mniejszy od 20 sekund, które przekroczyłaby implementacja sekwencyjna.
        assertTimeout(Duration.ofSeconds(4), () -> {
            int size = 10;

            // Kostka usypiająca wątki na pół sekudny w metodach before.
            Cube cube = new Cube(size,
                (x, y) -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        System.err.println("test interrupted");
                    }
                },
                (x, y) -> {},
                () -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        System.err.println("test interrupted");
                    }
                },
                () -> {}
            );

            // 4 grupy wątków rozmiaru size
            // Każde kolejne size wątków może pracować wspóbieżnie (tworzą jedną grupę).
            Thread[] threads = new Thread[4 * size];
            for (int i = 0; i < 4 * size; i++) {
                int finalI = i;
                int layer = i % size;
                int dualLayer = layer % 2 == 0 ? layer : size - layer - 1;
                threads[i] = new Thread(
                    () -> {
                        try {
                            if (finalI < size) {
                                cube.rotate(layer % 2 == 0 ? 0 : 5, dualLayer);
                            }
                            else if (finalI < 2 * size){
                                cube.rotate(layer % 2 == 0 ? 1 : 3, dualLayer);
                            }
                            else if (finalI < 3 * size){
                                cube.rotate(layer % 2 == 0 ? 2 : 4, dualLayer);
                            }
                            else {
                                cube.show();
                            }
                        } catch (InterruptedException e) {
                            System.err.println("test interrupted");
                        }
                    }
                );
            }

            executeThreads(threads, 4 * size);
        });
    }

    // Test bezpieczeństwa dla losowych operacji. Jest wykonywany z różnymi parametrami przez funkcję securityTest.
    public void parameterizedSecurityTest(int size, int rotations, int threadsNum) {
        Cube cube = getSecureCube(size); // Bezpieczeństwo sprawdza kostka.

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

        executeThreads(threads, threadsNum);
    }

    // Test bezpieczeństwa dla losowych operacji.
    @Test
    public void securityTest() {
        // mała kostka, dużo wątków
        parameterizedSecurityTest(1, 200, 10);
        parameterizedSecurityTest(2, 200, 10);
        parameterizedSecurityTest(3, 200, 10);
        parameterizedSecurityTest(3, 200, 30);

        // duża kostka, mało wątków
        parameterizedSecurityTest(10, 20000, 1);
        parameterizedSecurityTest(10, 10000, 2);
        parameterizedSecurityTest(10, 5000, 4);

        // rozmiar kostki podobny do ilości wątków
        parameterizedSecurityTest(2, 10000, 2);
        parameterizedSecurityTest(3, 5000, 3);
        parameterizedSecurityTest(5, 200, 5);
        parameterizedSecurityTest(5, 200, 10);
        parameterizedSecurityTest(10, 200, 5);
        parameterizedSecurityTest(10, 200, 10);
    }

    // Test sprawdzający obsługę przerwań w losowym momencie. Sprawdzane jest potencjalne pojawianie się zaklaszczeń
    // (wtedy test nie skończy się wykonywać). Ponadto test sprawdza, czy jeśli została wykonana metoda beforeRotaion(),
    // to mimo przerwania wątku, funkcja rotate() wykonała się poprawnie do końca. Podobnie z beforeShowing() dla show().
    // Innymi słowy, test sprawdza, czy przerwania nie psują poprawnego stanu kostki.
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
                (x, y) -> beforeRotationCounter.incrementAndGet(),
                (x, y) -> afterRotationCounter.incrementAndGet(),
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

            executeAndInterruptThreads(threads, threadsNum);

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
        }
    }

    // Test sprawdzający obslugę przerwań i bezpieczeństwo wywoływany przez metodę interruptionHandlingAndSecurityTest().
    public void parameterizedInterruptionHandlingAndSecurityTest(int size, int threadsNum) {
        Cube cube = getSecureCube(size);

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

        executeAndInterruptThreads(threads, threadsNum);

        checkNumberOfColors(cube, size); // dodatkowe sprawdzenie stanu kostki
    }

    // Ostateczny test sprawdzający obsługę przerwań i bezpieczeństwo jednocześnie. Wątki wykonują losowe operacje
    // w nieskończonej pętli i zostają przerywane w losowym momencie. Test sprawdza, czy przerwania nie psują
    // bezpieczeństwa kostki. Test nie zakończy się, jeśli dojdzie do zakleszczenia.
    @Test
    public void interruptionHandlingAndSecurityTest() {
        // mała kostka, dużo wątków
        parameterizedInterruptionHandlingAndSecurityTest(1, 10);
        parameterizedInterruptionHandlingAndSecurityTest(2, 10);
        parameterizedInterruptionHandlingAndSecurityTest(3, 10);
        parameterizedInterruptionHandlingAndSecurityTest(5, 20);
        parameterizedInterruptionHandlingAndSecurityTest(3, 50);

        // duża kostka, mało wątków
        parameterizedInterruptionHandlingAndSecurityTest(10, 1);
        parameterizedInterruptionHandlingAndSecurityTest(10, 5);
        parameterizedInterruptionHandlingAndSecurityTest(50, 20);
        parameterizedInterruptionHandlingAndSecurityTest(100, 10);
        parameterizedInterruptionHandlingAndSecurityTest(200, 50);

        // rozmiar kostki podobny do ilości wątków
        parameterizedInterruptionHandlingAndSecurityTest(20, 20);
        parameterizedInterruptionHandlingAndSecurityTest(30, 30);
        parameterizedInterruptionHandlingAndSecurityTest(30, 50);
        parameterizedInterruptionHandlingAndSecurityTest(50, 50);
        parameterizedInterruptionHandlingAndSecurityTest(100, 50);
    }

}