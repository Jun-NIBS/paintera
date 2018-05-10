package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;

public class N5HDF5LabelMeta< D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > extends N5HDF5Meta< D > implements N5LabelMeta< D, T >
{

	public N5HDF5LabelMeta(
			final N5HDF5Reader n5,
			final String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}