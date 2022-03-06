package softwaredesign.controllers;

import com.sothawo.mapjfx.*;
import com.sothawo.mapjfx.event.MapViewEvent;

import javafx.animation.Transition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import org.alternativevision.gpx.GPXParser;
import org.alternativevision.gpx.beans.GPX;
import org.alternativevision.gpx.beans.Track;
import org.alternativevision.gpx.beans.Waypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import softwaredesign.Metrics;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MainSceneController {

    /** logger for the class. */
    private static final Logger logger = LoggerFactory.getLogger(MainSceneController.class);

    /** some coordinates from around town. */
    private static final Coordinate amsterdamCenter = new Coordinate(52.3676, 4.9041);

    /** default zoom value. */
    private static final int ZOOM_DEFAULT = 14;

    /** the markers. */
    private final Marker markerClick;

    /** button to set the map's zoom. */
    @FXML
    private Button buttonZoom;

    /** the MapView containing the map */
    @FXML
    private MapView mapView;

    /** the box containing the top controls, must be enabled when mapView is initialized */
    @FXML
    private HBox topControls;

    /** Slider to change the zoom value */
    @FXML
    private Slider sliderZoom;


    /** Need this variable across various methods */
    private CoordinateLine trackLine;


    public MainSceneController() {
        markerClick = Marker.createProvided(Marker.Provided.ORANGE).setVisible(false);
    }

    /**
     * called after the fxml is loaded and all objects are created. This is not called initialize any more,
     * because we need to pass in the projection before initializing.
     *
     * @param projection
     *     the projection to use in the map.
     */
    public void initMapAndControls(Projection projection) throws ParserConfigurationException, IOException, SAXException {
        logger.trace("begin initialize");

        // set the controls to disabled, this will be changed when the MapView is intialized
        setControlsDisable(true);

        // wire the zoom button and connect the slider to the map's zoom
        buttonZoom.setOnAction(event -> mapView.setZoom(ZOOM_DEFAULT));
        sliderZoom.valueProperty().bindBidirectional(mapView.zoomProperty());

        // watch the MapView's initialized property to finish initialization
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized();
            }
        });


        /** Get the GPX file */
        FileInputStream in;
        try {
            in = new FileInputStream("src/testfiles/testfile1.gpx");
        } catch (java.io.FileNotFoundException e) {
            System.out.println("ERROR: Could not find GPX file");
            return;
        }

        /** Parse the GPX file */
        GPXParser p = new GPXParser();
        GPX gpx = p.parseGPX(in);

        HashSet<Track> tracks = gpx.getTracks();
        if (tracks.size() == 0) {
            System.out.println("ERROR: There are no tracks to visualize in the provided GPX file");
            return;
        }

        /** Make a CoordinateLine for plotting */
        Coordinate[] trackCoordinates = {};
        for (Track track : tracks) {
            Metrics metrics =  new Metrics(track);
            trackCoordinates = metrics.getCoordinates();
            break; // Only interested in the first track (which is probably the only track anyway)
        }

        trackLine = new CoordinateLine(trackCoordinates);
        trackLine.setColor(Color.ORANGERED).setVisible(true);
        // How to make a marker:
        // Marker coordinateMarker = Marker.createProvided(Marker.Provided.BLUE).setPosition(testCoordinate).setVisible(true);



        setupEventHandlers();

        logger.trace("start map initialization");
        mapView.initialize(Configuration.builder()
                .projection(projection)
                .showZoomControls(false)
                .build());
        logger.debug("initialization finished");
    }

    /**
     * initializes the event handlers.
     */
    private void setupEventHandlers() {
        // add an event handler for singleclicks, set the click marker to the new position when it's visible
        mapView.addEventHandler(MapViewEvent.MAP_CLICKED, event -> {
            event.consume();
            final Coordinate newPosition = event.getCoordinate().normalize();

            if (markerClick.getVisible()) {
                final Coordinate oldPosition = markerClick.getPosition();
                if (oldPosition != null) {
                    animateClickMarker(oldPosition, newPosition);
                } else {
                    markerClick.setPosition(newPosition);
                    // adding can only be done after coordinate is set
                    mapView.addMarker(markerClick);
                }
            }
        });

        // add an event handler for MapViewEvent#MAP_EXTENT and set the extent in the map
        mapView.addEventHandler(MapViewEvent.MAP_EXTENT, event -> {
            event.consume();
            mapView.setExtent(event.getExtent());
        });

//        mapView.addEventHandler(MapViewEvent.MAP_POINTER_MOVED, event -> logger.debug("pointer moved to " + event.getCoordinate()));

        logger.trace("map handlers initialized");
    }

    private void animateClickMarker(Coordinate oldPosition, Coordinate newPosition) {
        // animate the marker to the new position
        final Transition transition = new Transition() {
            private final Double oldPositionLongitude = oldPosition.getLongitude();
            private final Double oldPositionLatitude = oldPosition.getLatitude();
            private final double deltaLatitude = newPosition.getLatitude() - oldPositionLatitude;
            private final double deltaLongitude = newPosition.getLongitude() - oldPositionLongitude;

            {
                setCycleDuration(Duration.seconds(1.0));
                setOnFinished(evt -> markerClick.setPosition(newPosition));
            }

            @Override
            protected void interpolate(double v) {
                final double latitude = oldPosition.getLatitude() + v * deltaLatitude;
                final double longitude = oldPosition.getLongitude() + v * deltaLongitude;
                markerClick.setPosition(new Coordinate(latitude, longitude));
            }
        };
        transition.play();
    }

    /**
     * enables / disables the different controls
     *
     * @param flag
     *     if true the controls are disabled
     */
    private void setControlsDisable(boolean flag) {
        topControls.setDisable(flag);
    }

    /**
     * finishes setup after the mpa is initialzed
     */
    private void afterMapIsInitialized() {
        logger.trace("map intialized");
        logger.debug("setting center and enabling controls...");
        // start at the harbour with default zoom
        mapView.setZoom(ZOOM_DEFAULT);
        mapView.setCenter(amsterdamCenter);


//        mapView.addMarker(testCoordinate);
        mapView.addCoordinateLine(trackLine);


        // now enable the controls
        setControlsDisable(false);
    }

}