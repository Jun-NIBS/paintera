package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;

public abstract class MeshExporter
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	protected int numberOfFaces = 0;

	public void exportMesh(
			final Function< Long, Interval[] >[][] blockListCaches,
			final Function< ShapeKey, Pair< float[], float[] > >[][] meshCaches,
			final long[] ids,
			final int scale,
			final String[] paths )
	{
		assert ids.length == paths.length;
		for ( int i = 0; i < ids.length; i++ )
		{
			numberOfFaces = 0;
			exportMesh( blockListCaches[ i ], meshCaches[ i ], ids[ i ], scale, paths[ i ] );
		}
	}

	public void exportMesh(
			final Function< Long, Interval[] >[] blockListCache,
			final Function< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final long id,
			final int scaleIndex,
			final String path )
	{
		// all blocks from id
		final Interval[] blocks = blockListCache[ scaleIndex ].apply( id );

		// generate keys from blocks, scaleIndex, and id
		final List< ShapeKey > keys = new ArrayList<>();
		for ( final Interval block : blocks )
			// ignoring simplification iterations parameter
			// TODO consider smoothing parameters
			keys.add( new ShapeKey( id, scaleIndex, 0, 0, 0, Intervals.minAsLongArray( block ), Intervals.maxAsLongArray( block ) ) );

		for ( final ShapeKey key : keys )
		{
			Pair< float[], float[] > verticesAndNormals;
			try
			{
				verticesAndNormals = meshCache[ scaleIndex ].apply( key );
				assert verticesAndNormals.getA().length == verticesAndNormals.getB().length: "Vertices and normals must have the same size.";
				save( path, id, verticesAndNormals.getA(), verticesAndNormals.getB(), hasFaces( numberOfFaces ) );
				numberOfFaces += verticesAndNormals.getA().length / 3;
			}
			catch ( final RuntimeException e )
			{
				LOG.warn( "{} : {}", e.getClass(), e.getMessage() );
				e.printStackTrace();
				throw e;
			}
		}

	}

	protected abstract void save( String path, long id, float[] vertices, float[] normals, boolean append );

	public static boolean hasFaces( final int numberOfFaces )
	{
		return numberOfFaces > 0;
	}

}
