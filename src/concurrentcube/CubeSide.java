// Patryk Jędrzejczak

// Klasa reprezentująca jedną ściankę kostki.

package concurrentcube;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class CubeSide {
    AtomicInteger[][] color;
    int size;

    public CubeSide(int size, int initialColor) {
        this.size = size;
        color = new AtomicInteger[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                color[i][j] = new AtomicInteger(initialColor);
            }
        }
    }

    public AtomicInteger[] getRow(int row) {
        return Arrays.copyOf(color[row], size);
    }

    public AtomicInteger[] getColumn(int column) {
        AtomicInteger[] result = new AtomicInteger[size];
        for (int i = 0; i < size; i++) {
            result[i] = new AtomicInteger(color[i][column].get());
        }
        return result;
    }

    private AtomicInteger[] reverseTable(AtomicInteger[] t) {
        for (int i = 0; i < size / 2; i++) {
            int temp = t[i].get();
            t[i].set(t[size - i - 1].get());
            t[size - i - 1].set(temp);
        }
        return t;
    }

    public AtomicInteger[] getReversedRow(int layer) {
        return reverseTable(getRow(layer));
    }

    public AtomicInteger[] getReversedColumn(int layer) {
        return reverseTable(getColumn(layer));
    }

    public void setRow(int row, AtomicInteger[] newRow) {
        System.arraycopy(newRow, 0, color[row], 0, size);
    }

    public void setColumn(int column, AtomicInteger[] newColumn) {
        for (int i = 0; i < size; i++) {
             color[i][column].set(newColumn[i].get());
        }
    }

    public void rotateClockwise() {
        AtomicInteger[][] result = new AtomicInteger[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[j][size - i - 1] = new AtomicInteger(color[i][j].get());
            }
        }
        color = result;
    }

    public void rotateCounterClockwise() {
        AtomicInteger[][] result = new AtomicInteger[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[size - j - 1][i] = new AtomicInteger(color[i][j].get());
            }
        }
        color = result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result.append(color[i][j]);
            }
        }
        return result.toString();
    }

}
