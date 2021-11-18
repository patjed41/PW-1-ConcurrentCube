package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {

    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private final Side top, left, front, right, back, bottom;

    private int workingNum;
    private int workingGroup;
    private boolean[][] workingLayer;
    private int waitingNum;
    private int[] waitingFromGroup;
    private int[][] waitingWithLayer;
    private final Semaphore mutex;
    private final Semaphore showSem;
    private final Semaphore[][] layerSem;

    private static final int GROUPS = 4;

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

        top = new Side(size, 0);
        left = new Side(size, 1);
        front = new Side(size, 2);
        right = new Side(size, 3);
        back = new Side(size, 4);
        bottom = new Side(size, 5);

        workingNum = 0;
        workingGroup = 0;
        workingLayer = new boolean[GROUPS][size];
        waitingNum = 0;
        waitingFromGroup = new int[GROUPS];
        waitingWithLayer = new int[GROUPS][size];
        mutex = new Semaphore(1, true);
        showSem = new Semaphore(0, true);
        layerSem = new Semaphore[GROUPS - 1][size];
        for (int group = 0; group < GROUPS - 1; group++) {
            for (int layer = 0; layer < size; layer++) {
                layerSem[group][layer] = new Semaphore(0, true);
            }
        }
    }

    private void rotateLayer(int side, int layer) throws InterruptedException {
        int[] copyOfFirstFragment = null;

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

    private void rotateSide(int side, int layer) {
        if (layer == 0) {
            switch (side) {
                case 0:
                    top.rotateClockwise();
                    break;
                case 1:
                    left.rotateClockwise();
                    break;
                case 2:
                    front.rotateClockwise();
                    break;
                case 3:
                    right.rotateClockwise();
                    break;
                case 4:
                    back.rotateClockwise();
                    break;
                case 5:
                    bottom.rotateClockwise();
                    break;
            }
        }

        if (layer == size - 1) {
            switch (side) {
                case 0:
                    bottom.rotateCounterClockwise();
                    break;
                case 1:
                    right.rotateCounterClockwise();
                    break;
                case 2:
                    back.rotateCounterClockwise();
                    break;
                case 3:
                    left.rotateCounterClockwise();
                    break;
                case 4:
                    front.rotateCounterClockwise();
                    break;
                case 5:
                    top.rotateCounterClockwise();
                    break;
            }
        }
    }

    private int getGroupOfRotation(int side) {
        if (side == 0 || side == 5) return 0;
        else if (side == 1 || side == 3) return 1;
        else return 2;
    }

    public void rotate(int side, int layer) throws InterruptedException {
        int group = getGroupOfRotation(side);
        int dualLayer = side > 2 ? layer : size - layer - 1;
        mutex.acquire();
        if (workingNum > 0 && (workingGroup != group || waitingNum > 0 || workingLayer[group][dualLayer])) {
            waitingNum++;
            waitingFromGroup[group]++;
            waitingWithLayer[group][dualLayer]++;
            mutex.release();
            layerSem[group][dualLayer].acquire();
            waitingNum--;
            waitingFromGroup[group]--;
            waitingWithLayer[group][dualLayer]--;
        }
        workingGroup = group;
        workingLayer[group][dualLayer] = true;
        workingNum++;
        boolean releasedNext = false;
        for (int otherLayer = dualLayer + 1; otherLayer < size; otherLayer++) {
            if (waitingWithLayer[group][otherLayer] > 0) {
                releasedNext = true;
                layerSem[group][otherLayer].release();
                break;
            }
        }
        if (!releasedNext) {
            mutex.release();
        }

        beforeRotation.accept(side, layer);

        rotateLayer(side, layer);
        rotateSide(side, layer);

        afterRotation.accept(side, layer);

        mutex.acquire();
        workingNum--;
        workingLayer[group][dualLayer] = false;
        if (workingNum == 0 && waitingNum > 0) {
            for (int nextGroup = group + 1; true; nextGroup = (nextGroup + 1) % 4) {
                if (waitingFromGroup[nextGroup] > 0) {
                    if (nextGroup == 3) {
                        showSem.release();
                    }
                    else {
                        for (int firstLayer = 0; firstLayer < size; firstLayer++) {
                            if (waitingWithLayer[nextGroup][firstLayer] > 0) {
                                layerSem[nextGroup][firstLayer].release();
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        else {
            mutex.release();
        }
    }

    public String show() throws InterruptedException {
        mutex.acquire();
        if (workingNum > 0 && (workingGroup != 3 || waitingNum > 0)) {
            waitingNum++;
            waitingFromGroup[3]++;
            mutex.release();
            showSem.acquire();
            waitingNum--;
            waitingFromGroup[3]--;
        }
        workingGroup = 3;
        workingNum++;
        if (waitingFromGroup[3] > 0) {
            showSem.release();
        }
        else {
            mutex.release();
        }

        beforeShowing.run();

        String description = top.toString() + left.toString() + front.toString() +
                             right.toString() + back.toString() + bottom.toString();

        afterShowing.run();

        mutex.acquire();
        workingNum--;
        if (workingNum == 0 && waitingNum > 0) {
            for (int nextGroup = 0; true; nextGroup++) {
                if (waitingFromGroup[nextGroup] > 0) {
                    if (nextGroup == 3) {
                        showSem.release();
                    }
                    else {
                        for (int firstLayer = 0; firstLayer < size; firstLayer++) {
                            if (waitingWithLayer[nextGroup][firstLayer] > 0) {
                                layerSem[nextGroup][firstLayer].release();
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        else {
            mutex.release();
        }

        return description;
    }

    public static void main(String[] args) {
        var counter = new Object() { int value = 0; };

        Cube cube = new Cube(4,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; }
        );

        try {
            cube.rotate(2, 0);
            System.out.println(cube.show());
            cube.rotate(5, 1);

            String expected =
                      "0000"
                    + "0000"
                    + "0000"
                    + "1111"

                    + "1115"
                    + "1115"
                    + "4444"
                    + "1115"

                    + "2222"
                    + "2222"
                    + "1115"
                    + "2222"

                    + "0333"
                    + "0333"
                    + "2222"
                    + "0333"

                    + "4444"
                    + "4444"
                    + "0333"
                    + "4444"

                    + "3333"
                    + "5555"
                    + "5555"
                    + "5555";

            System.out.println(expected);
            System.out.println(cube.show());
        }
        catch (InterruptedException e) {
            System.out.println("interrupted");
        }
    }

}