package concurrentcube;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Side {
    int[][] color; // The type is AtomicInteger so it can be easily passed by reference.
    int size;

    public Side(int size, int initialColor) {
        this.size = size;
        color = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                color[i][j] = initialColor;
            }
        }
    }

    public int[] getRow(int row) {
        return Arrays.copyOf(color[row], size);
    }

    public int[] getColumn(int column) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = color[i][column];
        }
        return result;
    }

    private int[] reverseTable(int[] t) {
        for (int i = 0; i < size / 2; i++) {
            int temp = t[i];
            t[i] = t[size - i - 1];
            t[size - i - 1] = temp;
        }
        return  t;
    }

    public int[] getReversedRow(int layer) {
        return reverseTable(getRow(layer));
    }

    public int[] getReversedColumn(int layer) {
        return reverseTable(getColumn(layer));
    }

    public void setRow(int row, int[] newRow) {
        System.arraycopy(newRow, 0, color[row], 0, size);
    }

    public void setColumn(int column, int[] newColumn) {
        for (int i = 0; i < size; i++) {
             color[i][column] = newColumn[i];
        }
    }

    public void rotateClockwise() {
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[j][size - i - 1] = color[i][j];
            }
        }
        color = result;
    }

    public void rotateCounterClockwise() {
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[size - j - 1][i] = color[i][j];
            }
        }
        color = result;
    }

    @Override
    public String toString() {
        String result = "";
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result += color[i][j];
            }
        }
        return result;
    }

}
