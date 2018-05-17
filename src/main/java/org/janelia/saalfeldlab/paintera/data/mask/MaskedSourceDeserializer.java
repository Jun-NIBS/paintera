package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public class MaskedSourceDeserializer implements JsonDeserializer< MaskedSource< ?, ? > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String UNDERLYING_SOURCE_CLASS_KEY = "sourceClass";

	private static final String UNDERLYING_SOURCE_KEY = "source";

	private static final String CURRENT_CACHE_DIR_KEY = "cacheDir";

	private static final String PERSIST_CANVAS_CLASS_KEY = "persistCanvasClass";

	private static final String PERSIST_CANVAS_KEY = "persistCanvas";

	private final Supplier< String > currentProjectDirectory;

	private final ExecutorService propagationExecutor;

	public MaskedSourceDeserializer( final Supplier< String > currentProjectDirectory, final ExecutorService propagationExecutor )
	{
		super();
		this.currentProjectDirectory = currentProjectDirectory;
		this.propagationExecutor = propagationExecutor;
	}

	@Override
	public MaskedSource< ?, ? > deserialize( final JsonElement el, final Type type, final JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
			final JsonObject map = el.getAsJsonObject();
			final TmpDirectoryCreator canvasCacheDirUpdate = new TmpDirectoryCreator( Paths.get( currentProjectDirectory.get() ), null );

			final String sourceClass = map.get( UNDERLYING_SOURCE_CLASS_KEY ).getAsString();
			final DataSource< ?, ? > source = context.deserialize( map.get( UNDERLYING_SOURCE_KEY ), Class.forName( sourceClass ) );

			final String persisterClass = map.get( PERSIST_CANVAS_CLASS_KEY ).getAsString();
			@SuppressWarnings( "unchecked" )
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground =
					( BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > ) context.deserialize(
							map.get( PERSIST_CANVAS_KEY ),
							Class.forName( persisterClass ) );

			final String initialCanvasPath = map.get( CURRENT_CACHE_DIR_KEY ).getAsString();

			final DataSource< ?, ? > masked = Masks.mask( source, initialCanvasPath, canvasCacheDirUpdate, mergeCanvasIntoBackground, propagationExecutor );
			return masked instanceof MaskedSource< ?, ? >
					? ( MaskedSource< ?, ? > ) masked
					: null;
		}
		catch ( final ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}
	}

	public static class Factory implements StatefulSerializer.Deserializer< MaskedSource< ?, ? >, MaskedSourceDeserializer >
	{

		@Override
		public MaskedSourceDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier< String > projectDirectory,
				final IntFunction< SourceState< ?, ? > > dependencyFromIndex )
		{
			return new MaskedSourceDeserializer( projectDirectory, arguments.propagationWorkers );
		}

	}

}
