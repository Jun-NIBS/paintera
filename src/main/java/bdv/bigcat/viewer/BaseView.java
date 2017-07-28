package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.HDF5LabelMultisetSourceSpec.HighlightingStreamConverter;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.cache.CacheControl;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

public class BaseView extends BorderPane
{

	public static final Class< ? >[] FOCUS_KEEPERS = { TextField.class };

	private final HashSet< ViewerNode > viewerNodes = new HashSet<>();

	private final HashMap< ViewerNode, ViewerTransformManager > managers = new HashMap<>();

	private final GridPane grid;

	private final GlobalTransformManager gm;

	private final GridConstraintsManager constraintsManager;

	private final ViewerOptions viewerOptions;

	private final ObservableList< SourceAndConverter< ? > > sourceLayers = FXCollections.observableArrayList();
	{
		sourceLayers.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) c -> {
			while ( c.next() );

		} );
	}

	private final ObservableList< ViewerActor > viewerActors = FXCollections.observableArrayList();
	{
		viewerActors.addListener( ( ListChangeListener< ViewerActor > ) c -> {
			while ( c.next() )
				if ( c.wasAdded() )
					for ( final ViewerActor actor : c.getAddedSubList() )
						viewerNodes.forEach( vn -> actor.onAdd().accept( ( ViewerPanel ) vn.getContent() ) );
				else if ( c.wasRemoved() )
					for ( final ViewerActor actor : c.getRemoved() )
						viewerNodes.forEach( vn -> actor.onRemove().accept( ( ViewerPanel ) vn.getContent() ) );
		} );
	}

	private final Consumer< ViewerPanel > onFocusEnter;

	private final Consumer< ViewerPanel > onFocusExit;

	public BaseView()
	{
		this( ViewerOptions.options() );
	}

	public BaseView( final ViewerOptions viewerOptions )
	{
		this( ( vp ) -> {}, ( vp ) -> {}, viewerOptions );
	}

	public BaseView( final Consumer< ViewerPanel > onFocusEnter, final Consumer< ViewerPanel > onFocusExit, final ViewerOptions viewerOptions )
	{
		super();
		this.gm = new GlobalTransformManager();
		gm.setTransform( new AffineTransform3D() );

		this.constraintsManager = new GridConstraintsManager();
		this.grid = constraintsManager.createGrid();
		this.centerProperty().set( grid );
		this.onFocusEnter = onFocusEnter;
		this.onFocusExit = onFocusExit;
		this.viewerOptions = viewerOptions;
		this.grid.requestFocus();
		this.setInfoNode( new Label( "Place your node here!" ) );
	}

	public void makeDefaultLayout()
	{
		addViewer( ViewerAxis.Z, 0, 0 );
		addViewer( ViewerAxis.X, 0, 1 );
		addViewer( ViewerAxis.Y, 1, 0 );
		this.viewerNodes.forEach( Node::requestFocus );
		this.grid.requestFocus();
	}

	public void setInfoNode( final Node node )
	{
		for ( final Node child : grid.getChildren() )
			if ( GridPane.getRowIndex( child ) == 1 && GridPane.getColumnIndex( child ) == 1 )
			{
				grid.getChildren().remove( child );
				break;
			}
		this.grid.add( node, 1, 1 );
	}

	public synchronized void addSource( final SourceAndConverter< ? > source, final Composite< ARGBType, ARGBType > comp )
	{
		if ( !sourceLayers.contains( source ) )
			this.sourceLayers.add( source );
	}

	public synchronized void removeSource( final Source< ? > source )
	{
		int i = 0;
		for ( ; i < sourceLayers.size(); ++i )
			if ( sourceLayers.get( i ).getSpimSource().equals( source ) )
				break;
		if ( i < sourceLayers.size() )
			sourceLayers.remove( i );
	}

	public synchronized void addActor( final ViewerActor actor )
	{
		this.viewerActors.add( actor );
	}

	public Scene createScene( final int width, final int height )
	{
		final Scene scene = new Scene( this, width, height );
		scene.setOnKeyTyped( event -> {
			if ( event.getCharacter().equals( "a" ) )
				maximizeActiveOrthoView( scene, event );
		} );

		return scene;
	}

	private void addViewerNodesHandler( final ViewerNode viewerNode, final Class< ? >[] focusKeepers )
	{

		viewerNode.addEventHandler( MouseEvent.MOUSE_CLICKED, event -> viewerNode.requestFocus() );

		viewerNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
			final Node focusOwner = viewerNode.sceneProperty().get().focusOwnerProperty().get();
			for ( final Class< ? > focusKeeper : focusKeepers )
				if ( focusKeeper.isInstance( focusOwner ) )
					return;
			viewerNode.requestFocus();
		} );

		handleFocusEvent( viewerNode );
	}

	private synchronized void handleFocusEvent( final ViewerNode viewerNode )
	{
		viewerNode.focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			final ViewerPanel viewer = ( ViewerPanel ) viewerNode.getContent();
			if ( viewer == null )
				return;
			else if ( newValue )
				this.onFocusEnter.accept( viewer );
			else
			{
				System.out.println( "Handling exit: accept viewer!" );
				this.onFocusExit.accept( viewer );
			}
		} );
	}

	private synchronized void addViewer( final ViewerAxis axis, final int rowIndex, final int colIndex )
	{
		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, gm, viewerOptions );
		this.viewerNodes.add( viewerNode );
		this.managers.put( viewerNode, viewerNode.manager() );

		sourceLayers.addListener( viewerNode );

		final Thread t = new Thread( () -> {
			boolean createdViewer = false;
			while ( !createdViewer )
			{
				try
				{
					Thread.sleep( 10 );
				}
				catch ( final InterruptedException e )
				{
					e.printStackTrace();
					return;
				}
				createdViewer = viewerNode.isReady();
			}
			createdViewer = true;
			sourceLayers.forEach( sl -> ( ( ViewerPanel ) viewerNode.getContent() ).addSource( sl ) );
			viewerActors.forEach( actor -> actor.onAdd().accept( ( ViewerPanel ) viewerNode.getContent() ) );

		} );
		t.start();

		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );

		this.grid.add( viewerNode, rowIndex, colIndex );
	}

	private void maximizeActiveOrthoView( final Scene scene, final Event event )
	{
		final Node focusOwner = scene.focusOwnerProperty().get();
		if ( viewerNodes.contains( focusOwner ) )
		{
			event.consume();
			if ( !constraintsManager.isFullScreen() )
			{
				viewerNodes.forEach( node -> node.setVisible( node == focusOwner ) );
				constraintsManager.maximize(
						GridPane.getRowIndex( focusOwner ),
						GridPane.getColumnIndex( focusOwner ),
						0 );
				( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
				grid.setHgap( 0 );
				grid.setVgap( 0 );
			}
			else
			{
				constraintsManager.resetToLast();
				viewerNodes.forEach( node -> node.setVisible( true ) );
				viewerNodes.forEach( node -> ( ( ViewerPanel ) node.getContent() ).requestRepaint() );
				grid.setHgap( 1 );
				grid.setVgap( 1 );
			}
		}
	}

	public Node globalSourcesInfoNode()
	{
		final FlowPane p = new FlowPane( Orientation.VERTICAL );

		final HashMap< Source< ? >, Node > sourceToEntry = new HashMap<>();

		final Function< SourceAndConverter< ? >, Node > entryCreator = ( sac ) -> {
			final FlowPane fp = new FlowPane();
			fp.getChildren().add( new Label( sac.getSpimSource().getName() ) );

			final Converter< ?, ARGBType > conv = sac.getConverter();

			if ( conv instanceof RealARGBConverter )
			{
				final RealARGBConverter< ? > c = ( RealARGBConverter< ? > ) conv;
				// alpha
				{
					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getAlpha() );
					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
						c.setAlpha( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}

				// min
				{
					final Spinner< Double > sp = new Spinner<>( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, c.getMin() );
					sp.valueProperty().addListener( ( ChangeListener< Double > ) ( observable, oldValue, newValue ) -> {
						c.setMin( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}

				// max
				{
					final Spinner< Double > sp = new Spinner<>( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, c.getMax() );
					sp.valueProperty().addListener( ( ChangeListener< Double > ) ( observable, oldValue, newValue ) -> {
						c.setMax( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}
			}

			else if ( conv instanceof HighlightingStreamConverter )
			{
				final HighlightingStreamConverter c = ( HighlightingStreamConverter ) conv;

				// alpha
				{
					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getAlpha() );
					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
						c.setAlpha( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}

				// highlighting alpha
				{
					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getHighlightAlpha() );
					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
						c.setHighlightAlpha( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}

				// invalid alpha
				{
					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getInvalidSegmentAlpha() );
					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
						c.setInvalidSegmentAlpha( newValue );
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
					} );
					sp.setEditable( true );
					fp.getChildren().add( sp );
				}
			}

			sourceToEntry.put( sac.getSpimSource(), fp );
			p.getChildren().add( fp );

			return fp;
		};
		for ( final SourceAndConverter< ? > source : sourceLayers )
			entryCreator.apply( source );

		sourceLayers.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) c -> {
			while ( c.next() )
				if ( c.wasRemoved() )
					c.getRemoved().forEach( rm -> p.getChildren().remove( sourceToEntry.remove( rm.getSpimSource() ) ) );
				else if ( c.wasAdded() )
					c.getAddedSubList().forEach( entryCreator::apply );

		} );

		return p;
	}
}
