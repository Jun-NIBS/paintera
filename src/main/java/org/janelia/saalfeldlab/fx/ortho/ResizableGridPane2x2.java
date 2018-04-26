package org.janelia.saalfeldlab.fx.ortho;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

public class ResizableGridPane2x2< TL extends Node, TR extends Node, BL extends Node, BR extends Node >
{

	private final GridPane grid = new GridPane();

	private final GridConstraintsManager constraintsManager = new GridConstraintsManager( grid );

	private final ObjectProperty< TL > topLeft = new SimpleObjectProperty<>();

	private final ObjectProperty< TR > topRight = new SimpleObjectProperty<>();

	private final ObjectProperty< BL > bottomLeft = new SimpleObjectProperty<>();

	private final ObjectProperty< BR > bottomRight = new SimpleObjectProperty<>();
	{
		topLeft.addListener( ( obs, oldv, newv ) -> replace( grid, oldv, newv, 0, 0 ) );
		topRight.addListener( ( obs, oldv, newv ) -> replace( grid, oldv, newv, 1, 0 ) );
		bottomLeft.addListener( ( obs, oldv, newv ) -> replace( grid, oldv, newv, 0, 1 ) );
		bottomRight.addListener( ( obs, oldv, newv ) -> replace( grid, oldv, newv, 1, 1 ) );
	}

	public ResizableGridPane2x2(
			final TL topLeft,
			final TR topRight,
			final BL bottomLeft,
			final BR bottomRight )
	{
		super();
		grid.setHgap( 1 );
		grid.setVgap( 1 );
		this.topLeft.set( topLeft );
		this.topRight.set( topRight );
		this.bottomLeft.set( bottomLeft );
		this.bottomRight.set( bottomRight );
	}

	public Pane pane()
	{
		return this.grid;
	}

	public GridConstraintsManager constraintsManager()
	{
		return this.constraintsManager;
	}

	public ObjectProperty< TL > topLeftProperty()
	{
		return topLeft;
	}

	public ObjectProperty< TR > topRightProperty()
	{
		return topRight;
	}

	public ObjectProperty< BL > bottomLeftProperty()
	{
		return bottomLeft;
	}

	public ObjectProperty< BR > bottomRightProperty()
	{
		return bottomRight;
	}

	public TL getTopLeft()
	{
		return topLeft.get();
	}

	public TR getTopRight()
	{
		return topRight.get();
	}

	public BL getBototmLeft()
	{
		return bottomLeft.get();
	}

	public BR getBottomRight()
	{
		return bottomRight.get();
	}

	public static void replace( final GridPane grid, final Node oldValue, final Node newValue, final int col, final int row )
	{
		grid.getChildren().remove( oldValue );
		grid.add( newValue, col, row );
	}

}