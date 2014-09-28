/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout.intrinsifiable;

/**
 * This class contains the intrinsifiable portions of PrimitiveIntArray behavior. JDK implementations
 * that choose to intrinsify PrimitiveIntArray are expected to replace the implementation of this
 * base class.
 */

public abstract class AbstractPrimitiveIntArray extends PrimitiveArray {

    private final int[][] longAddressableElements; // Used to store elements at indexes above Integer.MAX_VALUE
    private final int[] intAddressableElements;

    protected final int[] _asArray() {
        if (getLength() > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Cannot make int[] from array with more than Integer.MAX_VALUE elements (" +
                            getLength() + ")");
        }
        return intAddressableElements;
    }

    protected int _get(final int index) {
        return intAddressableElements[index];
    }

    protected int _get(final long index) {
        if (index < Integer.MAX_VALUE) {
            return _get((int) index);
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex = (index - Integer.MAX_VALUE);
        final int partitionIndex = (int)(longIndex >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int)longIndex & PARTITION_MASK;

        return longAddressableElements[partitionIndex][partitionOffset];
    }

    protected void _set(final int index, final int value) {
        intAddressableElements[index] = value;
    }

    protected void _set(final long index, final int value) {
        if (index < Integer.MAX_VALUE) {
            _set((int) index, value);
        }

        // Calculate index into long-addressable-only partitions:
        final long longIndex = (index - Integer.MAX_VALUE);
        final int partitionIndex = (int)(longIndex >>> MAX_EXTRA_PARTITION_SIZE_POW2_EXPONENT);
        final int partitionOffset = (int)longIndex & PARTITION_MASK;

        longAddressableElements[partitionIndex][partitionOffset] = value;
    }

    protected AbstractPrimitiveIntArray() {
        intAddressableElements = (int[]) createIntAddressableElements(int.class);
        longAddressableElements = (int[][]) createLongAddressableElements(int.class);
    }

    protected AbstractPrimitiveIntArray(AbstractPrimitiveIntArray sourceArray) {
        intAddressableElements = sourceArray.intAddressableElements.clone();
        int numLongAddressablePartitions = sourceArray.longAddressableElements.length;
        longAddressableElements = new int[numLongAddressablePartitions][];
        for (int i = 0; i < numLongAddressablePartitions; i++) {
            longAddressableElements[i] = sourceArray.longAddressableElements[i].clone();
        }
    }
}