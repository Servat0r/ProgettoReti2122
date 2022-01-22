package winsome.util;

import java.util.function.BiFunction;

@FunctionalInterface
public interface Accumulator<T> extends BiFunction<T, T, T> { }