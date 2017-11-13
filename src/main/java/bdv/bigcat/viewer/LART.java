package bdv.bigcat.viewer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import com.google.gson.JsonObject;
import com.sun.javafx.application.PlatformImpl;

import bdv.AbstractViewerSetupImgLoader;
import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.solver.SolverQueueServerZMQ;
import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5Utils;
import bdv.util.Prefs;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import gnu.trove.map.hash.TLongLongHashMap;
import javafx.application.Platform;
import javafx.stage.Stage;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class LART
{

	public static void main( final String[] args ) throws Exception
	{
		Prefs.showMultibox( false );
		Prefs.showTextOverlay( false );
		// need to add resolution to Constantin's data!
		final String rawFile = "/data/hanslovskyp/constantin-example-data/data/raw.h5";
		PlatformImpl.startup( () -> {} );
		final String rawDataset = "data_u8";
		final String labelsFile = "/data/hanslovskyp/constantin-example-data/data/seg.h5";
		final String labelsDataset = "data";

		final String actionReceiverAddress = "ipc://actions";

		// https://github.com/zeromq/jeromq
		// ipc:// protocol works only between jeromq (uses tcp://127.0.0.1:port
		// internally).
		// ipc:// protocol with zeromq. Java doesn't support UNIX domain socket.
		// WHY?
		final String solutionRequestResponseAddress = "ipc:///tmp/mc-solver";

		final String solutionDistributionAddress = "ipc://solution";

		final String latestSolutionRequestAddress = "ipc://latest-solution";

		final int ioThreads = 1;

		final long minWaitTimeAfterLastAction = 100;

		final Context ctx = ZMQ.context( ioThreads );
		final Socket initialSolutionSocket = ctx.socket( ZMQ.REQ );
		initialSolutionSocket.connect( solutionRequestResponseAddress );
		initialSolutionSocket.send( "" );
		final byte[] solutionBytes = initialSolutionSocket.recv();
		final TLongLongHashMap initialSolutionHashMap = new TLongLongHashMap();
		final ByteBuffer bb = ByteBuffer.wrap( solutionBytes );
		for ( int i = 0; bb.hasRemaining(); ++i )
			initialSolutionHashMap.put( i, bb.getLong() );
		final Supplier< TLongLongHashMap > initialSolution = () -> initialSolutionHashMap;

		final SolverQueueServerZMQ solveQueue = new SolverQueueServerZMQ(
				actionReceiverAddress,
				solutionRequestResponseAddress,
				solutionDistributionAddress,
				initialSolution,
				latestSolutionRequestAddress,
				ioThreads,
				minWaitTimeAfterLastAction );

		final double[] resolution = { 4, 4, 40 };
		final double[] offset = { 0, 0, 0 };
		final int[] cellSize = { 145, 53, 5 };

		final int numPriorities = 20;
		final SharedQueue sharedQueue = new SharedQueue( 12, numPriorities );
		final VolatileGlobalCellCache cellCache = new VolatileGlobalCellCache( 1, 12 );

		final RandomAccessibleIntervalDataSource< UnsignedByteType, VolatileUnsignedByteType > rawSource =
				H5Utils.createH5RawSource( "raw", rawFile, rawDataset, cellSize, resolution, sharedQueue, numPriorities - 1, UnsignedByteType::new, VolatileUnsignedByteType::new );

		final double[] min = Arrays.stream( Intervals.minAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final AffineTransform3D affine = new AffineTransform3D();
		rawSource.getSourceTransform( 0, 0, affine );
		affine.apply( min, min );
		affine.apply( max, max );

		final Atlas viewer = new Atlas(
				new FinalInterval( Arrays.stream( min ).mapToLong( Math::round ).toArray(),
						Arrays.stream( max ).mapToLong( Math::round ).toArray() ),
				sharedQueue );

		final AffineTransform3D tf = new AffineTransform3D();
		final double scale = 1e-3;
		tf.scale( scale );
		tf.translate( scale * -1938, scale * -1760, scale * -664 );
		viewer.setTransform( tf );

		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage, "l’art" );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			stage.show();
		} );

		viewer.addRawSource( rawSource, 0, 255 );

		final Socket assignmentSocket = ctx.socket( ZMQ.REQ );
		final Socket solutionSocket = ctx.socket( ZMQ.SUB );

		assignmentSocket.connect( actionReceiverAddress );
		solutionSocket.connect( solutionDistributionAddress );
		solutionSocket.subscribe( "".getBytes() );

		final Consumer< Action > actionBroadcast = action -> {
			final JsonObject json = new JsonObject();
			json.add( "actions", Action.toJson( Arrays.asList( action ) ) );
			json.addProperty( "version", "1" );
			assignmentSocket.send( json.toString() );
			System.out.println( "Sent action " + json.toString() + " WAITING FOR RESPONSE! on socket " + actionReceiverAddress );
			final byte[] response = assignmentSocket.recv();
			System.out.println( "GOT RESPONSE: " + Arrays.toString( response ) );
		};

		final Supplier< TLongLongHashMap > solutionReceiver = () -> {
			final byte[] data = solutionSocket.recv();
			final ByteBuffer dataBuffer = ByteBuffer.wrap( data );
			final TLongLongHashMap result = new TLongLongHashMap();
			while ( dataBuffer.hasRemaining() )
				result.put( dataBuffer.getLong(), dataBuffer.getLong() );
			return result;
		};

		final HDF5LabelMultisetDataSource labelSpec2 = new HDF5LabelMultisetDataSource(
				labelsFile,
				labelsDataset,
				cellSize,
				actionBroadcast,
				solutionReceiver, () -> initialSolutionHashMap,
				"labels",
				cellCache,
				1 );
		viewer.addLabelSource( labelSpec2 );

		initialSolutionSocket.send( "" );
		initialSolutionSocket.recv();
		initialSolutionSocket.close();

	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

	public static class ARGBConvertedSource< T > implements Source< VolatileARGBType >
	{
		final private AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader;

		private final int setupId;

		private final Converter< Volatile< T >, VolatileARGBType > converter;

		final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[] {
					new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
					new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
			};
		}

		public ARGBConvertedSource(
				final int setupId,
				final AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader,
				final Converter< Volatile< T >, VolatileARGBType > converter )
		{
			this.setupId = setupId;
			this.loader = loader;
			this.converter = converter;
		}

		final public AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > getLoader()
		{
			return loader;
		}

		@Override
		public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
		{
			return Converters.convert(
					loader.getVolatileImage( t, level ),
					converter,
					new VolatileARGBType() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		/**
		 * TODO Store this in a field
		 */
		@Override
		public int getNumMipmapLevels()
		{
			return loader.getMipmapResolutions().length;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{

			final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
					Views.extendValue( getSource( t, level ), new VolatileARGBType( 0 ) );
			switch ( method )
			{
			case NLINEAR:
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default:
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}

		@Override
		public VolatileARGBType getType()
		{
			return new VolatileARGBType();
		}

		@Override
		public String getName()
		{
			return "1 2 3";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		// TODO: make ARGBType version of this source
		public Source nonVolatile()
		{
			return this;
		}
	}

}