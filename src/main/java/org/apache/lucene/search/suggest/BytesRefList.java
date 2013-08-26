package org.apache.lucene.search.suggest;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.Arrays;
import java.util.Comparator;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.ByteBlockPool;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefIterator;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.SorterTemplate;

/**
 * A simple append only random-access {@link BytesRef} array that stores full
 * copies of the appended bytes in a {@link ByteBlockPool}.
 * 
 * 一个简单的append only，支持随机访问的的Bytesef数组 Bytesef相当于byte[]，不过在这基础上附加了offset和length属性
 * <b>Note: This class is not Thread-Safe!</b>
 * 
 * @lucene.internal
 * @lucene.experimental
 */
public final class BytesRefList {
	// TODO rename to BytesRefArray
	private final ByteBlockPool pool;// 实际用来保存数据的地方
	private int[] offsets = new int[1];// offsets[i]是第i个元素在pool中的offset。这也表明了这是一个append
										// only的数组
	private int lastElement = 0;// 最后一个元素的编号（也可以理解为元素个数）
	private int currentOffset = 0;// 当前偏移量，下次就从这里开始写数据
	private final Counter bytesUsed = Counter.newCounter(false);

	/**
	 * Creates a new {@link BytesRefList}
	 */
	public BytesRefList() {
		this.pool = new ByteBlockPool(
				new ByteBlockPool.DirectTrackingAllocator(bytesUsed));
		pool.nextBuffer();
		bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER
				+ RamUsageEstimator.NUM_BYTES_INT);
	}

	/**
	 * Clears this {@link BytesRefList}
	 */
	public void clear() {
		lastElement = 0;
		currentOffset = 0;
		Arrays.fill(offsets, 0);
		pool.reset();
	}

	/**
	 * Appends a copy of the given {@link BytesRef} to this {@link BytesRefList}
	 * .
	 * 
	 * @param bytes
	 *            the bytes to append
	 * @return the ordinal of the appended bytes
	 */
	public int append(BytesRef bytes) {
		if (lastElement >= offsets.length) {// 如果元素个数过多，就把offsets扩容
			int oldLen = offsets.length;
			offsets = ArrayUtil.grow(offsets, offsets.length + 1);
			bytesUsed.addAndGet((offsets.length - oldLen)// 从这个方法的命名上看这里有race
															// condition
					* RamUsageEstimator.NUM_BYTES_INT);
		}
		pool.copy(bytes);
		offsets[lastElement++] = currentOffset;// 保存当前元素的offset，然后元素个数自增
		currentOffset += bytes.length;// 更新offset
		return lastElement;
	}

	/**
	 * Returns the current size of this {@link BytesRefList}
	 * 
	 * @return the current size of this {@link BytesRefList}
	 */
	public int size() {
		return lastElement;
	}

	/**
	 * Returns the <i>n'th</i> element of this {@link BytesRefList}
	 * 
	 * @param spare
	 *            a spare {@link BytesRef} instance
	 * @param ord
	 *            the elements ordinal to retrieve
	 * @return the <i>n'th</i> element of this {@link BytesRefList}
	 */
	public BytesRef get(BytesRef spare, int ord) {
		// 所有的数据都放在pool中，所以要通过offset和length去pool中取
		if (lastElement > ord) {
			spare.offset = offsets[ord];// offset直接通过ord（index）从数据中拿到
			spare.length = ord == lastElement - 1 ? currentOffset
					- spare.offset// length就是offsets[ord + 1] -
									// offsets[ord].但要判断ord是不是最后一个元素
			: offsets[ord + 1] - spare.offset;
			pool.copyFrom(spare);
			return spare;
		}
		throw new IndexOutOfBoundsException("index " + ord
				+ " must be less than the size: " + lastElement);

	}

	/**
	 * Returns the number internally used bytes to hold the appended bytes in
	 * memory
	 * 
	 * @return the number internally used bytes to hold the appended bytes in
	 *         memory
	 */
	public long bytesUsed() {
		return bytesUsed.get();
	}

	private int[] sort(final Comparator<BytesRef> comp) {
		final int[] orderedEntries = new int[size()];
		for (int i = 0; i < orderedEntries.length; i++) {
			orderedEntries[i] = i;
		}
		new SorterTemplate() {
			@Override
			protected void swap(int i, int j) {
				final int o = orderedEntries[i];
				orderedEntries[i] = orderedEntries[j];
				orderedEntries[j] = o;
			}

			@Override
			protected int compare(int i, int j) {
				final int ord1 = orderedEntries[i], ord2 = orderedEntries[j];
				return comp.compare(get(scratch1, ord1), get(scratch2, ord2));
			}

			@Override
			protected void setPivot(int i) {
				final int ord = orderedEntries[i];
				get(pivot, ord);
			}

			@Override
			protected int comparePivot(int j) {
				final int ord = orderedEntries[j];
				return comp.compare(pivot, get(scratch2, ord));
			}

			private final BytesRef pivot = new BytesRef(),
					scratch1 = new BytesRef(), scratch2 = new BytesRef();
		}.quickSort(0, size() - 1);
		return orderedEntries;
	}

	/**
	 * sugar for {@link #iterator(Comparator)} with a <code>null</code>
	 * comparator
	 */
	public BytesRefIterator iterator() {
		return iterator(null);
	}

	/**
	 * <p>
	 * Returns a {@link BytesRefIterator} with point in time semantics. The
	 * iterator provides access to all so far appended {@link BytesRef}
	 * instances.
	 * </p>
	 * <p>
	 * If a non <code>null</code> {@link Comparator} is provided the iterator
	 * will iterate the byte values in the order specified by the comparator.
	 * Otherwise the order is the same as the values were appended.
	 * </p>
	 * <p>
	 * This is a non-destructive operation.
	 * </p>
	 */
	public BytesRefIterator iterator(final Comparator<BytesRef> comp) {
		final BytesRef spare = new BytesRef();
		final int size = size();
		final int[] ords = comp == null ? null : sort(comp);
		return new BytesRefIterator() {
			int pos = 0;

			@Override
			public BytesRef next() {
				if (pos < size) {
					return get(spare, ords == null ? pos++ : ords[pos++]);
				}
				return null;
			}

			@Override
			public Comparator<BytesRef> getComparator() {
				return comp;
			}
		};
	}
}
