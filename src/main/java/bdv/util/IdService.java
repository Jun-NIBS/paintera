package bdv.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Stack;

import org.apache.commons.lang.ArrayUtils;

import ch.systemsx.cisd.hdf5.IHDF5LongReader;

public class IdService {

	private static Stack<Long> reusableIds = new Stack<Long>();

	// don't spend more than 8K on remembering free'd ids
	private static int maxNumReusableIds = 1024;

	private static Long nextFree = new Long(0);
	
	/**
	 * Mark all ids in range [first, last] (inclusive) as used.
	 * 
	 * @param next
	 */
	public static synchronized void invalidate(long first, long last) {
		
		if (last >= nextFree)
			nextFree = last + 1;
	}

	/**
	 * Invalidate all ids contained in an HDF5 dataset.
	 * 
	 * @param reader
	 * @param dataset
	 */
	public static void invalidate(IHDF5LongReader reader, String dataset) {

		long[] ids = reader.readArray(dataset);
		invalidate(0, Collections.max(Arrays.asList(ArrayUtils.toObject(ids))));
	}

	/**
	 * Get a new id.
	 */
	public static synchronized long allocate() {

		long id;

		if (reusableIds.empty()) {

			id = nextFree;
			nextFree += 1;

		} else {

			id = reusableIds.pop();
		}

		return id;
	}

	/**
	 * Get a new set of ids.
	 * 
	 * @param n
	 *            The number of ids to allocate.
	 */
	public static synchronized long[] allocate(int n) {

		long[] ids = new long[n];

		for (int i = 0; i < n; i++) {

			if (!reusableIds.empty()) {

				ids[i] = allocate();

			} else {

				for (int j = i; j < n; j++) {

					ids[j] = nextFree;
					nextFree += 1;
				}
				break;
			}
		}

		return ids;
	}

	/**
	 * Mark an id as free, so that it can be allocated again. The service does
	 * not try to remember every single id that was free'd. If there are too
	 * many that haven't been allocated, the call to this method might just be
	 * ignored and you will never see the id again. In this case, false is
	 * returned.
	 * 
	 * @param id
	 * @return true, if the free'd id will be considered for further allocations
	 */
	public static synchronized boolean free(long id) {

		if (reusableIds.size() < maxNumReusableIds) {

			reusableIds.push(id);
			return true;
		}

		return false;
	}

	/**
	 * Mark an array of ids as free.
	 * 
	 * @param ids
	 */
	public static void free(long[] ids) {

		for (long id : ids)
			if (!free(id))
				break;
	}

	/**
	 * Mark a range of ids as free.
	 * 
	 * @param first
	 *            The first id to free.
	 * @param last
	 *            The last id to free.
	 */
	public static void free(long first, long last) {

		for (long id = first; id <= last; id++)
			if (!free(id))
				break;
	}
}
