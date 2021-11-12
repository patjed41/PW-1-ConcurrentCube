package concurrentcube;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class Cube {

    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private Side top, left, front, right, back, bottom;

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
    }

    public void rotate(int side, int layer) throws InterruptedException {
        beforeRotation.accept(side, layer);

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
                copyOfFirstFragment = top.getRow(side - layer - 1);
                top.setRow(size - layer - 1, left.getReversedColumn(size - layer - 1));
                left.setColumn(size - layer - 1, bottom.getRow(layer));
                bottom.setRow(layer, right.getReversedColumn(layer));
                right.setColumn(layer, copyOfFirstFragment);
                break;
            case 3:
                copyOfFirstFragment = top.getReversedColumn(side - layer - 1);
                top.setColumn(size - layer - 1, front.getColumn(size - layer - 1));
                front.setColumn(size - layer - 1, bottom.getColumn(size - layer - 1));
                bottom.setColumn(size - layer - 1, back.getReversedColumn(layer));
                back.setColumn(layer, copyOfFirstFragment);
                break;
            case 4:
                copyOfFirstFragment = top.getReversedRow(layer);
                top.setRow(layer, right.getReversedColumn(size - layer - 1));
                right.setColumn(size - layer - 1, bottom.getReversedRow(size - layer - 1));
                bottom.setRow(size - layer - 1, left.getColumn(layer));
                left.setRow(layer, copyOfFirstFragment);
                break;
            case 5:
                copyOfFirstFragment = left.getRow(size - layer - 1);
                left.setRow(size - layer - 1, back.getRow(size - layer - 1));
                back.setRow(size - layer - 1, right.getRow(size - layer - 1));
                right.setRow(size - layer - 1, front.getRow(size - layer - 1));
                front.setRow(size - layer - 1, copyOfFirstFragment);
                break;
        }

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

        afterRotation.accept(side, layer);
    }

    public String show() throws InterruptedException {
        beforeShowing.run();

        String description = top.toString() + left.toString() + front.toString() +
                             right.toString() + back.toString() + bottom.toString();

        afterShowing.run();

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