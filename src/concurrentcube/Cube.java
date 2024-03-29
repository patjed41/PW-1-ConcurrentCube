// author - Patryk Jędrzejczak

package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class Cube {

    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private final CubeSide top, left, front, right, back, bottom;

    // Używam tylko zmiennych Atomic, żeby zapewnić prawidłową widoczność zmiennych.

    // liczba wątków, które aktualnie rotują kostkę lub pokazują jej stan
    private final AtomicInteger workingNum;
    // numer aktualnie pracującej grupy
    private final AtomicInteger workingGroup;
    // workingLayer[s][i] == true, gdy i-ta warstwa potrząc od ściany s < 3 jest rotowana
    private final AtomicBoolean[][] workingLayer;
    // liczba wątków czekających na rotację lub pokazanie stanu
    private final AtomicInteger waitingNum;
    // liczba wątków z danej grupy czekających na wykonanie operacji
    private final AtomicInteger[] waitingFromGroup;
    // waitingFromLayer[s][i] - liczba wątków czekających na obrót i-tej warstwy patrząc od ściany s < 3
    private final AtomicInteger[][] waitingFromLayer;

    private final Semaphore mutex;
    // semafor, na którym czekają wątki oczekujące na pokazenie stanu kostki
    private final Semaphore showSem;
    // layerSem[s][i] - semafor, na którym czekają wątki oczekujące na obrót i-tej warstwy patrząc od ściany s < 3
    private final Semaphore[][] layerSem;
    // Mamy cztery grupy wątków:
    //  0 - rotujące warstwy względem ścian 0 i 5
    //  1 - rotujące warstwy względem ścian 1 i 3
    //  2 - rotujące warstwy względem ścian 2 i 4
    //  3 - pokazujące stan kostki
    // Bezpieczeństwo kostki jest zachowane, gdy w danym momencie pracują wątki tylko z jednej grupy. Ponadto
    // dla grup 0, 1, 2 aktualnie może pracować tylko 1 proces obracający pewną warstwę.
    private static final int GROUPS = 4;
    private static final int SHOW_GROUP = 3;

    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        top = new CubeSide(size, 0);
        left = new CubeSide(size, 1);
        front = new CubeSide(size, 2);
        right = new CubeSide(size, 3);
        back = new CubeSide(size, 4);
        bottom = new CubeSide(size, 5);

        workingNum = new AtomicInteger();
        workingGroup = new AtomicInteger();
        workingLayer = new AtomicBoolean[GROUPS][size];
        waitingNum = new AtomicInteger();
        waitingFromGroup = new AtomicInteger[GROUPS];
        waitingFromLayer = new AtomicInteger[GROUPS][size];
        for (int group = 0; group < GROUPS; group++) {
            waitingFromGroup[group] = new AtomicInteger();
            for (int layer = 0; layer < size; layer++) {
                workingLayer[group][layer] = new AtomicBoolean();
                waitingFromLayer[group][layer] = new AtomicInteger();
            }
        }
        mutex = new Semaphore(1, true);
        showSem = new Semaphore(0, true);
        layerSem = new Semaphore[GROUPS - 1][size];
        for (int group = 0; group < GROUPS - 1; group++) {
            for (int layer = 0; layer < size; layer++) {
                layerSem[group][layer] = new Semaphore(0, true);
            }
        }
    }

    // Operacja obrócenia "brzegów" warstwy.
    private void rotateLayer(int side, int layer) {
        AtomicInteger[] copyOfFirstFragment;

        switch (side) {
            case 0:
                copyOfFirstFragment = left.getRow(layer);
                left.setRow(layer, front.getRow(layer));
                front.setRow(layer, right.getRow(layer));
                right.setRow(layer, back.getRow(layer));
                back.setRow(layer, copyOfFirstFragment);
                break;
            case 1:
                copyOfFirstFragment = top.getColumn(layer);
                top.setColumn(layer, back.getReversedColumn(size - layer - 1));
                back.setColumn(size - layer - 1, bottom.getReversedColumn(layer));
                bottom.setColumn(layer, front.getColumn(layer));
                front.setColumn(layer, copyOfFirstFragment);
                break;
            case 2:
                copyOfFirstFragment = top.getRow(size - layer - 1);
                top.setRow(size - layer - 1, left.getReversedColumn(size - layer - 1));
                left.setColumn(size - layer - 1, bottom.getRow(layer));
                bottom.setRow(layer, right.getReversedColumn(layer));
                right.setColumn(layer, copyOfFirstFragment);
                break;
            case 3:
                copyOfFirstFragment = top.getReversedColumn(size - layer - 1);
                top.setColumn(size - layer - 1, front.getColumn(size - layer - 1));
                front.setColumn(size - layer - 1, bottom.getColumn(size - layer - 1));
                bottom.setColumn(size - layer - 1, back.getReversedColumn(layer));
                back.setColumn(layer, copyOfFirstFragment);
                break;
            case 4:
                copyOfFirstFragment = top.getReversedRow(layer);
                top.setRow(layer, right.getColumn(size - layer - 1));
                right.setColumn(size - layer - 1, bottom.getReversedRow(size - layer - 1));
                bottom.setRow(size - layer - 1, left.getColumn(layer));
                left.setColumn(layer, copyOfFirstFragment);
                break;
            case 5:
                copyOfFirstFragment = left.getRow(size - layer - 1);
                left.setRow(size - layer - 1, back.getRow(size - layer - 1));
                back.setRow(size - layer - 1, right.getRow(size - layer - 1));
                right.setRow(size - layer - 1, front.getRow(size - layer - 1));
                front.setRow(size - layer - 1, copyOfFirstFragment);
                break;
        }
    }

    // Operacja obrócenia całej ściany, gdy layer == 0 lub layer == size - 1.
    private void rotateSide(int side, int layer) {
        if (layer == 0) {
            if (side == 0) top.rotateClockwise();
            else if (side == 1) left.rotateClockwise();
            else if (side == 2) front.rotateClockwise();
            else if (side == 3) right.rotateClockwise();
            else if (side == 4) back.rotateClockwise();
            else if (side == 5) bottom.rotateClockwise();
        }

        if (layer == size - 1) {
            if (side == 0) bottom.rotateCounterClockwise();
            else if (side == 1) right.rotateCounterClockwise();
            else if (side == 2) back.rotateCounterClockwise();
            else if (side == 3) left.rotateCounterClockwise();
            else if (side == 4) front.rotateCounterClockwise();
            else if (side == 5) top.rotateCounterClockwise();
        }
    }

    private int getGroupOfRotation(int side) {
        if (side == 0 || side == 5) return 0;
        else if (side == 1 || side == 3) return 1;
        else return 2;
    }

    // Fragment kodu dopuszczający kolejną grupę wątków do pracy. Faktycznie wpuszczany jest jeden wątek, a reszta
    // grupy jest wpuszczana później kaskadowo. Jest to wydzielony fragment kodu z funkcji rotate() i show().
    // Lepiej go teraz nie analizować.
    private void releaseNextGroup(int group) {
        boolean threadReleased = false;

        if (workingNum.get() == 0) { // Możemy kogoś wpuścić.
            int nextGroup = (group + 1) % GROUPS; // Zaczynamy od kolejnej grupy.
            for (int i = 0; i <= GROUPS; i++) { // A kończymy na naszej.
                if (nextGroup == SHOW_GROUP && waitingFromGroup[SHOW_GROUP].get() > 0) { // Wpuszczamy grupę pokazującą.
                    showSem.release();
                    threadReleased = true;
                }
                else { // Wpuszczamy grupę rotującą (być może tę samą).
                    for (int firstLayer = 0; firstLayer < size; firstLayer++) {
                        if (waitingFromLayer[nextGroup][firstLayer].get() > 0) {
                            layerSem[nextGroup][firstLayer].release();
                            threadReleased = true;
                            break;
                        }
                    }
                }
                if (threadReleased) {
                    break;
                }
                nextGroup = (nextGroup + 1) % GROUPS;
            }
        }

        if (!threadReleased) { // Nikogo nie wpuściliśmy.
            mutex.release();
        }
    }

    // Funkcja dopuszczająca wątek rotujący kolejną wartwę z grupy. Zwraca false, jeśli nie ma kogo wpuścić.
    private boolean releaseNextLayer(int group, int dualLayer) {
        boolean releasedNext = false;
        for (int otherLayer = dualLayer + 1; otherLayer < size; otherLayer++) {
            if (waitingFromLayer[group][otherLayer].get() > 0) {
                releasedNext = true;
                layerSem[group][otherLayer].release();
                break;
            }
        }
        return releasedNext;
    }

    public void rotate(int side, int layer) throws InterruptedException {
        int group = getGroupOfRotation(side);
        int dualLayer = side < 3 ? layer : size - layer - 1; // jednoznaczna warstwa dla przeciwnych ścian
        Thread thread = Thread.currentThread();
        boolean shouldReleaseNext = true; // true, jeśli wątek powinien wpuścić nastęnego

        mutex.acquire();

        // Poniżej true, jeśli wątek musi poczekać.
        if (workingNum.get() > 0 && (workingGroup.get() != group || waitingNum.get() - waitingFromGroup[group].get() > 0
                                                                 || workingLayer[group][dualLayer].get())) {
            waitingNum.incrementAndGet();
            waitingFromGroup[group].incrementAndGet();
            waitingFromLayer[group][dualLayer].incrementAndGet();

            mutex.release();
            layerSem[group][dualLayer].acquireUninterruptibly();

            waitingNum.decrementAndGet();
            waitingFromGroup[group].decrementAndGet();
            waitingFromLayer[group][dualLayer].decrementAndGet();

            if (thread.isInterrupted()) { // Obsługa wątków przerwanych w protokole wstępnym.
                if (!releaseNextLayer(group, dualLayer - 1)) { // Kontynuujemy kaskadowe wpuszczanie.
                    releaseNextGroup(group); // Jeśli nie mamy kogo wpuścić, być może trzeba wpuścić nową grupę.
                }

                thread.interrupt();
                throw new InterruptedException();
            }
        }
        else if (workingNum.get() > 0) { // Tylko wątki, które weszły bez czekania i nie jako pierwsze, nie wpuszczają.
            shouldReleaseNext = false;
        }

        // Wątek przeszedł protokół wstępny. Od tego momemntu, jeśli zostanie przerwany, wykonujemu funkcję do końca.
        workingGroup.set(group);
        workingLayer[group][dualLayer].set(true);
        workingNum.incrementAndGet();

        // Kaskodowe wpuszczanie kolejnych wątków z pracującej grupy z dziedziczeniem mutex'a. Ostatecznie dla każdej
        // jednoznacznej warstwy zostanie wpuszczony jeden nieprzerwany wątek, o ile choć jeden czeka na wpuszczenie.
        if (!shouldReleaseNext || !releaseNextLayer(group, dualLayer)) {
            mutex.release();
        }

        beforeRotation.accept(side, layer);
        rotateLayer(side, layer);
        rotateSide(side, layer);
        afterRotation.accept(side, layer);

        mutex.acquireUninterruptibly();
        workingNum.decrementAndGet();
        workingLayer[group][dualLayer].set(false);
        releaseNextGroup(group); // Wpuszczenie kolejnej grupy wątków. Z sukcesem zrobi to tylko ostatni kończący pracę.

        if (thread.isInterrupted()) { // Wątek został przerwany po protokole wstępnym.
            thread.interrupt();
            throw new InterruptedException();
        }
    }

    public String show() throws InterruptedException {
        Thread thread = Thread.currentThread();

        mutex.acquire();

        // Poniżej true, jeśli wątek musi poczekać.
        if (workingNum.get() > 0 && (workingGroup.get() != SHOW_GROUP ||
                                     waitingNum.get() - waitingFromGroup[SHOW_GROUP].get() > 0)) {
            waitingNum.incrementAndGet();
            waitingFromGroup[SHOW_GROUP].incrementAndGet();

            mutex.release();
            showSem.acquireUninterruptibly(); // Wątek może zostać przerwany. Później to obsłużymy.

            waitingNum.decrementAndGet();
            waitingFromGroup[SHOW_GROUP].decrementAndGet();

            if (thread.isInterrupted()) { // Obsługa wątków przerwanych w protokole wstępnym.
                if (waitingFromGroup[SHOW_GROUP].get() > 0) { // Kontynuujemy kaskadowe wpuszczanie.
                    showSem.release();
                }
                else { // Jeśli nie mamy więcej wątków czekających na show(), to być może trzeba wpuścić nową grupę.
                    releaseNextGroup(SHOW_GROUP);
                }

                thread.interrupt();
                throw new InterruptedException();
            }
        }

        // Wątek przeszedł protokół wstępny. Od tego momemntu, jeśli zostanie przerwany, wykonujemu funkcję do końca.
        workingGroup.set(SHOW_GROUP);
        workingNum.incrementAndGet();

        // Kaskodowe wpuszczanie kolejnych wątków pokazujących stan kostki z dziedziczeniem mutex'a.
        if (waitingFromGroup[SHOW_GROUP].get() > 0) {
            showSem.release();
        }
        else {
            mutex.release();
        }

        beforeShowing.run();
        String description = top.toString() + left.toString() + front.toString() +
                right.toString() + back.toString() + bottom.toString();
        afterShowing.run();

        mutex.acquireUninterruptibly();
        workingNum.decrementAndGet();
        releaseNextGroup(SHOW_GROUP);

        if (thread.isInterrupted()) { // Wątek został przerwany po protokole wstępnym.
            thread.interrupt();
            throw new InterruptedException();
        }

        return description;
    }

}
