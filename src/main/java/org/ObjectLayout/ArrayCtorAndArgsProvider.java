/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supports the construction of a new array's individual elements using a copy constructor to copy a source
 * array's corresponding elements.
 *
 * @param <T> type of the element occupying each array slot
 */
public class ArrayCtorAndArgsProvider<T> extends CtorAndArgsProvider<T> {

    private final Constructor<T> constructor;
    private final Object[] originalArgs;
    private final int containingIndexOffsetInArgs;

    private final boolean keepInternalCachingThreadSafe;
    private CtorAndArgs<T> nonThreadSafeCachedCtorAndArgs = null;
    private Object[] nonThreadSafeCachedArgs = null;
    private long[] nonThreadSafeCachedContainingIndex = null;
    private final AtomicReference<CtorAndArgs<T>> cachedConstructorAndArgs = new AtomicReference<CtorAndArgs<T>>();
    private final AtomicReference<Object[]> cachedArgs = new AtomicReference<Object[]>();
    private final AtomicReference<long[]> cachedContainingIndex = new AtomicReference<long[]>();


    /**
     * Used to apply a fixed constructor with a given set of arguments to all elements.
     *
     * @param constructor The element constructor
     * @param args The arguments to be passed to the constructor for all elements
     * @throws NoSuchMethodException if a constructor matching argTypes
     * @throws IllegalArgumentException if argTypes and args conflict
     */
    public ArrayCtorAndArgsProvider(final Constructor<T> constructor,
                                    final Object[] args,
                                    final int containingIndexOffsetInArgs) throws NoSuchMethodException {
        this(constructor, args, containingIndexOffsetInArgs, true);
    }

    /**
     * Used to apply a fixed constructor with a given set of arguments to all elements.
     *
     * @param constructor The element constructor
     * @param args The arguments to be passed to the constructor for all elements
     * @param keepInternalCachingThreadSafe Control whether or not internal caching is kept thread-safe
     * @throws NoSuchMethodException if a constructor matching argTypes
     * @throws IllegalArgumentException if argTypes and args conflict
     */
    public ArrayCtorAndArgsProvider(final Constructor<T> constructor,
                                    final Object[] args,
                                    final int containingIndexOffsetInArgs,
                                    final boolean keepInternalCachingThreadSafe) throws NoSuchMethodException {
        super(constructor.getDeclaringClass());
        this.constructor = constructor;
        this.originalArgs = args;
        this.containingIndexOffsetInArgs = containingIndexOffsetInArgs;
        this.keepInternalCachingThreadSafe = keepInternalCachingThreadSafe;
    }

    /**
     * Get a {@link CtorAndArgs} instance to be used in constructing a given element index in
     * a {@link StructuredArray}
     *
     * @param index The indices of the element to be constructed in the target array (one value per dimension).
     * @return {@link CtorAndArgs} instance to used in element construction
     * @throws NoSuchMethodException
     */
    @Override
    public CtorAndArgs<T> getForIndex(final long... index) throws NoSuchMethodException {
        CtorAndArgs<T> ctorAndArgs;
        Object[] args;
        long[] containingIndex;

        // Try (but not too hard) to use a cached, previously allocated ctorAndArgs object:
        if (keepInternalCachingThreadSafe) {
            ctorAndArgs = cachedConstructorAndArgs.getAndSet(null);
            args = cachedArgs.getAndSet(null);
            containingIndex = cachedContainingIndex.getAndSet(null);
        } else {
            ctorAndArgs = nonThreadSafeCachedCtorAndArgs;
            nonThreadSafeCachedCtorAndArgs = null;
            args = nonThreadSafeCachedArgs;
            nonThreadSafeCachedArgs = null;
            containingIndex = nonThreadSafeCachedContainingIndex;
            nonThreadSafeCachedContainingIndex = null;
        }

        if ((containingIndex == null) || (containingIndex.length != index.length))  {
            containingIndex = new long[index.length];
        }
        System.arraycopy(index, 0, containingIndex, 0, index.length);

        if (args == null) {
            args = Arrays.copyOf(originalArgs, originalArgs.length);
        }
        args[containingIndexOffsetInArgs] = containingIndex;

        if (ctorAndArgs == null) {
            // We have nothing cached that's not being used. A bit of allocation in contended cases won't kill us:
            ctorAndArgs = new CtorAndArgs<T>(constructor, args);
        }
        ctorAndArgs.setArgs(args);

        return ctorAndArgs;
    }


    /**
     * Recycle an {@link CtorAndArgs} instance (place it back in the internal cache if desired). This is [very]
     * useful for avoiding a re-allocation of a new {@link CtorAndArgs} and an associated args array for
     * {@link #getForIndex(long...)} invocation in cases such as this (where the returned {@link CtorAndArgs}
     * is not constant across indices).
     * Recycling is optional, and is not guaranteed to occur.
     *
     * @param ctorAndArgs the {@link CtorAndArgs} instance to recycle
     */
    @SuppressWarnings("unchecked")
    public void recycle(final CtorAndArgs<T> ctorAndArgs) {
        // Only recycle ctorAndArgs if ctorAndArgs is compatible with our state:
        if ((ctorAndArgs == null) || (ctorAndArgs.getConstructor() != constructor)) {
            return;
        }
        Object[] args = ctorAndArgs.getArgs();
        if ((args == null) || (args.length != originalArgs.length)) {
            return;
        }
        long[] containingIndexes = (long []) args[containingIndexOffsetInArgs];

        if (keepInternalCachingThreadSafe) {
            cachedConstructorAndArgs.lazySet(ctorAndArgs);
            cachedArgs.lazySet(args);
            cachedContainingIndex.lazySet(containingIndexes);
        } else {
            nonThreadSafeCachedCtorAndArgs = ctorAndArgs;
            nonThreadSafeCachedArgs = args;
            nonThreadSafeCachedContainingIndex = containingIndexes;
        }
    }
}
