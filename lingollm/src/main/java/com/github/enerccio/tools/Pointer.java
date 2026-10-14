package com.github.enerccio.tools;

import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Pointer<T> implements Iterable<T>, Cloneable, Serializable {
	private static final Pointer<Object> NULL_POINTER = new Pointer<Object>(null, true, true);

	public static <T> Pointer<T> of(T value) {
		if (value == null)
			throw new NullPointerException();
		return new Pointer<T>(value);
	}
	
	public static <T> Pointer<T> ofNullable(T value) {
		return new Pointer<T>(value);
	}
	
	public Pointer() {
		this(null);
	}

	public Pointer(T t) {
		this(t, false);
	}

	public Pointer(T t, boolean finalPointer) {
		this(t, finalPointer, false);
	}

	public Pointer(T t, boolean finalPointer, boolean checkNull) {
		this.pointedObject = t;
		this.finalPointer = finalPointer;
		this.checkNull = checkNull;
	}

	private T pointedObject;
	private final boolean finalPointer;
	private final boolean checkNull;

	public T fastGet() {
		if (checkNull && pointedObject == null)
			throw new NullPointerException();
		return pointedObject;
	}

	public synchronized T get() {
		return fastGet();
	}

	void fastSet(T t) {
		if (finalPointer)
			throw new UnsupportedOperationException("final");
		pointedObject = t;
	}

	public synchronized void set(T t) {
		fastSet(t);
	}

	public synchronized boolean isNull() {
		return fastIsNull();
	}

	public boolean fastIsNull() {
		return fastGet() == null;
	}

	public synchronized Pointer<T> asReadOnly() {
		return new Pointer<T>(fastGet(), true, checkNull);
	}

	public synchronized Pointer<T> asCheckedPointer() {
		return new Pointer<T>(fastGet(), finalPointer, true);
	}

	@SuppressWarnings("unchecked")
	public synchronized <X> Pointer<X> cast() {
		return (Pointer<X>) this;
	}

	public synchronized T setget(T t) {
		fastSet(t);
		return fastGet();
	}

	public synchronized T fetchAndStore(T newValue) {
		T oldValue = fastGet();
		fastSet(newValue);
		return oldValue;
	}

	public synchronized boolean compareAndSwap(T oldTest, T newValue) {
		T oldV = fastGet();
		if (nullSafeEquals(oldV, oldTest)) {
			fastSet(newValue);
			return true;
		}
		return false;
	}

	private static boolean nullSafeEquals(Object a, Object b) {
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	@Override
	public String toString() {
		return fastGet() == null ? "null" : fastGet().toString();
	}

	@SuppressWarnings("CloneDoesntCallSuperClone")
	public synchronized Object clone() {
        return new Pointer<T>(fastGet(), finalPointer, checkNull);
	}

	@Override
	public synchronized int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fastGet() == null) ? 0 : fastGet().hashCode());
		return result;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public synchronized boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		synchronized (obj) {
			Pointer other = (Pointer) obj;
			if (fastGet() == null) {
                return other.fastGet() == null;
			} else return fastGet().equals(other.fastGet());
        }
	}

	@Override
	public Iterator<T> iterator() {
		return new PointerIterator<T>();
	}

	private class PointerIterator<X> implements Iterator<X> {

		boolean iterated = false;

		@Override
		public boolean hasNext() {
			return !iterated;
		}

		@SuppressWarnings("unchecked")
		@Override
		public X next() {
			if (iterated)
				throw new NoSuchElementException();
			iterated = true;
			return (X) get();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	@SuppressWarnings("unchecked")
	public synchronized Pointer<T> filter(Predicate<? super T> predicate) {
		if (predicate.test(fastGet()))
			return (Pointer<T>) clone();
		return (Pointer<T>) NULL_POINTER;
	}

	@SuppressWarnings("unchecked")
	public synchronized <U> Pointer<U> flatMap(Function<? super T, Pointer<U>> mapper) {
		if (!fastIsNull()) {
			return mapper.apply(fastGet());
		}
		return (Pointer<U>) NULL_POINTER;
	}

	public synchronized void ifPresent(Consumer<? super T> consumer) {
		if (!fastIsNull())
			consumer.accept(fastGet());
	}

	public boolean isPresent() {
		return !isNull();
	}

	@SuppressWarnings("unchecked")
	public synchronized <U> Pointer<U> map(Function<? super T, ? extends U> mapper) {
		if (!fastIsNull()) {
			return new Pointer<U>(mapper.apply(fastGet()), finalPointer, checkNull);
		}
		return (Pointer<U>) NULL_POINTER;
	}
	
	public synchronized T orElse(T other) {
		if (fastIsNull()) {
			return other;
		}
		return fastGet();
	}
	
	public synchronized T orElseGet(Supplier<? extends T> other) {
		if (fastIsNull())
			return other.get();
		return fastGet();
	}
	
	public synchronized <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (fastIsNull())
			throw exceptionSupplier.get();
		return fastGet();
	}
}