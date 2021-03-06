package com.limbo.icedcoco;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.Buffer;
import java.util.LinkedList;
import java.util.ResourceBundle;

public class Controller implements Initializable{

    private ResourceBundle resources;
    private SessionInfo sessionInfo = new SessionInfo();
    private SettingsInfo settingsInfo = new SettingsInfo();
    private HotkeysInfo hotkeysInfo = new HotkeysInfo();
    private Scene scene;
    private Stage primaryStage;
    private float canvasZoomAmount = 0.05f;   // as a percentage, from 0 - 1
    private float minCanvasSize = 100f;     // min size for both of the width and height of the canvas
    private float startingCanvasSize = 1;
    private Image img;

    @FXML private GridPane masterPane;
    @FXML private ScrollPane canvasScrollPane;
    @FXML private AnchorPane canvasAnchorPane; // canvas is child of the anchor pane, anchor pane is child of the scroll pane
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem exportMenuItem;
    @FXML private Canvas canvas;
    @FXML private Button tool1;
    @FXML private Button tool2;
    @FXML private Button btnSettings;
    @FXML private TextArea captionTextArea;
    @FXML private TextField ellipseSizeTextField;
    @FXML private Slider ellipseSizeSlider;
    @FXML private VBox ellipseSizeVBox;
    @FXML private ToolBar toolsToolBar;
    @FXML private Button btnEllipse;

    private PolygonTool polygonTool;
    private EllipseTool ellipseTool;
    private SelectTool selectTool;
    private Tool currentTool = null;

    private PolyList polygons;
    private LinkedList<PolyList> polygonsList = new LinkedList<PolyList>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
        canvasScrollPane.setContent(canvas);
        this.polygons = sessionInfo.polygons;
        polygonTool = new PolygonTool(polygons);
        polygonTool.setCanvas(canvas);
        selectTool = new SelectTool(polygons);
        selectTool.setCanvas(canvas);
        setCurrentTool(selectTool);

        loadSettings();
    }

    /**
     * @param scene The scene object for the Controller.
     */
    public void setScene(Scene scene){
        this.scene = scene;
    }

    /**
     * @param stage The stage object for the Controller.
     */
    public void setStage(Stage stage){
        this.primaryStage = stage;
    }

    /**
     * The startup logic that requires the GUI to be initialized before executing.
     */
    public void start(){
        // call from main, initialising variables and things
        sessionInfo.captionTextArea = this.captionTextArea;
        sessionInfo.canvas = this.canvas;
        ellipseSizeSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                int size = (int)ellipseSizeSlider.getValue();
                ellipseSizeTextField.textProperty().setValue(String.valueOf(size));
                ellipseTool.setSize(size);
            }
        });
    }

    /**
     * @return The current area occupied by the canvas.
     */
    private double[] getCanvasArea(){
        // return the width and height as [width,height], of the space the canvas can expand to fill
        // canvas can occupy area from (1,1) to (3,5) of the gridpane. (column, row)
        double w = canvasScrollPane.getViewportBounds().getWidth();
        double h = canvasScrollPane.getViewportBounds().getHeight();
        return new double[] {w, h};
    }

    /**
     * @param img The displayed image.
     * @param newImage Flag to execute image set up logic.
     */
    private void drawImageInCanvas(Image img, boolean newImage){
        if(img==null){
            // do nothing
            return;
        }
        GraphicsContext gfx = canvas.getGraphicsContext2D();
        gfx.clearRect(0, 0, gfx.getCanvas().getWidth(), gfx.getCanvas().getHeight());
        // draw the image size to be its actual size if it can fit within the window,
        // or shrunk to fit the size of the canvas at its biggest
        // change this when we implement zooming maybe
        if (newImage) {
            double[] paneBounds = getCanvasArea();
            if (img.getWidth() < paneBounds[0] && img.getHeight() < paneBounds[1]) {
                canvas.setWidth(img.getWidth());
                canvas.setHeight(img.getHeight());
            } else {
                double shrinkFactor = 0;
                if (img.getWidth() / paneBounds[0] > img.getHeight() / paneBounds[1]) {
                    shrinkFactor = paneBounds[0] / img.getWidth();
                } else {
                    shrinkFactor = paneBounds[1] / img.getHeight();
                }
                canvas.setWidth(img.getWidth() * shrinkFactor);
                canvas.setHeight(img.getHeight() * shrinkFactor);
                startingCanvasSize = (float) (img.getWidth() * shrinkFactor);
            }
        }
        gfx.drawImage(img, 0, 0, canvas.getWidth(), canvas.getHeight());

        //sessionInfo settings
        sessionInfo.baseImage = img;
        sessionInfo.imageWidth = img.getWidth();
        sessionInfo.imageHeight = img.getHeight();
    }

    /**
     * Called from listeners defined in Main.java.
     */
    public void onWindowResize(){
        return;
    }

    /**
     * Exports the polygons to a RLE image.
     * @param event Not currently being used.
     */
    @FXML
    public void exportSegmentation(Event event){
        BufferedImage exportImage = sessionInfo.getSegmentationImage();

        try {
            FileChooser fc = new FileChooser();
            FileChooser.ExtensionFilter imgFilter = new FileChooser.ExtensionFilter("*.png", "*.PNG");
            fc.getExtensionFilters().add(imgFilter);
            File exportFile = fc.showSaveDialog(scene.getWindow());
            if (exportFile != null) {
                ImageIO.write(exportImage, "png", exportFile);
            }
        }catch(MalformedURLException mue){
            mue.printStackTrace();
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    /**
     * Displays the help dialog.
     * @param event Not currently used.
     */
    @FXML
    public void OpenHelpWindow(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("form_help.fxml"));
            Parent root = loader.load();
            ControllerHelp cont = loader.getController();

            Scene scene = new Scene(root);
            Stage stage = new Stage();
            stage.setScene(scene);
            cont.setScene(scene);
            stage.show();
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    /**
     * Creates the files required to store image meta data such as segmentations.
     * @param event Not currently used.
     */
    @FXML
    public void createMetadataFiles(ActionEvent event){
        Parent createFilesRoot;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("form_createMetadata.fxml"));
            createFilesRoot = loader.load();
            CreateMetadataController cont = loader.getController();
            cont.setSessionInfo(this.sessionInfo);
            Scene scene = new Scene(createFilesRoot, 640, 480);
            cont.setScene(scene);
            Stage popup = new Stage();
            popup.setScene(scene);
            popup.show();
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    /**
     * Opens the file dialog to select meta data files.
     * @param evet Not currently used.
     */
    @FXML
    public void selectMetadataFiles(ActionEvent evet){
        Parent createFilesRoot;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("form_selectMetadata.fxml"));
            createFilesRoot = loader.load();
            SelectMetadataController cont = loader.getController();
            cont.setSessionInfo(this.sessionInfo);
            Scene scene = new Scene(createFilesRoot, 640, 480);
            cont.setScene(scene);
            Stage popup = new Stage();
            popup.setScene(scene);
            popup.show();
            cont.start();
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }

    /**
     * Zooms in the image by enlarging the canvas.
     * @param event Not currently used.
     */
    @FXML
    public void growCanvas(Event event){
        // change by ten percent

        if(sessionInfo.baseImage==null){
            return;
        }
        // important to floor, otherwise the actual canvas size may end up one pixel larger than the image drawn inside it
        canvas.setWidth(Math.floor(canvas.getWidth() + canvasZoomAmount * sessionInfo.baseImage.getWidth()));
        canvas.setHeight(Math.floor(canvas.getHeight() + canvasZoomAmount * sessionInfo.baseImage.getHeight()));
        polygons.setScale(canvas.getWidth() / startingCanvasSize);
        canvas.getGraphicsContext2D().drawImage(sessionInfo.baseImage, 0,0,canvas.getWidth(), canvas.getHeight());
        if(currentTool!=null) currentTool.draw();
    }

    /**
     * Zooms out the image by shrinking the canvas.
     * @param event Not currently used.
     */
    @FXML
    public void shrinkCanvas(Event event){
        if(sessionInfo.baseImage==null){
            return;
        }
        double newWidth = Math.floor(canvas.getWidth() - canvasZoomAmount * sessionInfo.baseImage.getWidth());
        double newHeight = Math.floor(canvas.getHeight() - canvasZoomAmount * sessionInfo.baseImage.getHeight());
        if(newWidth < minCanvasSize || newHeight < minCanvasSize){
            return;
        }
        canvas.setWidth(newWidth);
        canvas.setHeight(newHeight);
        polygons.setScale(canvas.getWidth() / startingCanvasSize);
        canvas.getGraphicsContext2D().drawImage(sessionInfo.baseImage, 0,0,canvas.getWidth(), canvas.getHeight());
        if(currentTool!=null) currentTool.draw();
    }

    /**
     * Disables context menu items if they cannot be currently used.
     * @param event Not currently used.
     */
    @FXML
    public void prepareFileMenu(Event event){
        // validate all the menu items in the menu file
        // disable save if there is no image loaded and no valid .json files to save the metadata to
        saveMenuItem.setDisable(sessionInfo.baseImage==null | sessionInfo.imageDataFile==null);
        exportMenuItem.setDisable(sessionInfo.baseImage==null);
    }

    /**
     * Opens the file dialog to select an image.
     * @param event Not currently used.
     */
    @FXML
    public void menuOpenImage(ActionEvent event){

        // load the image to the canvas without any metadata
        System.out.println("Open file");
        FileChooser fc = new FileChooser();
        FileChooser.ExtensionFilter imgFilter = new FileChooser.ExtensionFilter("Images", "*.jpeg", "*.jpg", "*.JPG","*.JPEG", "*.png", "*.PNG", "*.gif", "*.GIF");
        // a catch all filter incase there is some weird image format we miss
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");
        fc.getExtensionFilters().add(imgFilter);
        File imgFile = fc.showOpenDialog(scene.getWindow());
        try {
            if (imgFile != null) {
                polygonsList = new LinkedList<PolyList>();
                img = new Image(imgFile.toURI().toURL().toExternalForm());       // not tested on mac
                sessionInfo.baseImageFile = imgFile;
                sessionInfo.polygons = new PolyList();
                drawImageInCanvas(img, true);
                // Reset the caption text
                captionTextArea.textProperty().setValue("");

                sessionInfo.checkImageMetadata();   // load existing data for captions, polygon vertices

                // TODO: Implement a proper way to initialize tools upon image load.

                // And make a new PolygonTool.
                this.polygons = sessionInfo.polygons;
                polygonTool = new PolygonTool(polygons);
                polygonTool.setCanvas(canvas);
                ellipseTool = new EllipseTool(polygons);
                ellipseTool.setCanvas(canvas);
                selectTool = new SelectTool(polygons);
                selectTool.setCanvas(canvas);

                // Reset the scale.
                polygons.setScale(1f);

                setCurrentTool(selectTool);
                currentTool.draw();
            }
        }catch(MalformedURLException mue){
            mue.printStackTrace();
        }
    }

    /**
     * Debug method?
     * @param event Not currently used.
     */
    @FXML
    public void menuOpenImageWithCoco(ActionEvent event){
        // load the image to the canvas with the metadata (make default opening method?)
        System.out.println("Open image with metadata");
    }

    /**
     * Saves the meta data to their files.
     * @param event Not currently used.
     */
    @FXML
    public void menuSaveData(ActionEvent event){
        System.out.println("saving to .json files...");
        sessionInfo.overwriteMetadata(primaryStage);
    }

    /**
     * Exits the entire application.
     * @param event Not currently used.
     */
    @FXML
    public void menuExit(ActionEvent event) {
        Platform.exit();
    }


    /**
     * Executes logic for when the mouse is clicked.
     * @param e The mouse event used.
     */
    @FXML
    private void onMouseClickedListener_Canvas (MouseEvent e) {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            currentTool.onMouseClicked(scaleMouseEvent(e));
            if(!(e.getButton()== MouseButton.SECONDARY))
            {
                PolyList p = new PolyList();
                p.clone(polygons);
                polygonsList.addFirst(p);
            }
        }

    }

    /**
     * Executes logic for when a mouse button is being pressed down.
     * @param e The mouse event used.
     */
    @FXML
    private void onMousePressedListener_Canvas (MouseEvent e) {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            currentTool.onMousePressed(scaleMouseEvent(e));
        }
    }

    /**
     * Executes logic for when a mouse button is released.
     * @param e The mouse event used.
     */
    @FXML
    private void onMouseReleasedListener_Canvas (MouseEvent e) {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            currentTool.onMouseReleased(scaleMouseEvent(e));

        }
    }

    /**
     * Executes logic for when the mouse is starting to be dragged across.
     * @param e The mouse event used.
     */
    @FXML
    private void onDragEnteredListener_Canvas (MouseEvent e) {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            currentTool.onDragEntered(scaleMouseEvent(e));
        }
    }

    /**
     * Executes logic for when the mouse is being dragged across.
     * @param e The mouse event used.
     */
    @FXML
    private void onMouseDraggedListener_Canvas (MouseEvent e) {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            currentTool.onMouseDragged(scaleMouseEvent(e));
        }
    }

    /**
     * Executes logic for when a keyboard key is being pressed.
     * @param e The keyboard event being used.
     */
    @FXML
    private void onKeyPressListener (KeyEvent e)
    {
        if (currentTool != null) {
            drawImageInCanvas(img, false);
            // TODO: Turn this into a class for global hotkey support
            if (e.getCode() == KeyCode.DELETE) {
                polygons.remove(polygons.getSelectedVertex());
            }
            if (e.getCode() == KeyCode.C) {
                if(polygonsList.size() > 1) {
                    polygonsList.removeFirst();
                    polygons.clear();
                    if((polygons.clone(polygonsList.getFirst())) == true)
                    {
                        polygonsList.removeFirst();
                        polygonsList.removeFirst();
                    }


                }
                else
                {
                    polygons.clear();
                }
                polygons.polygonClickedSecondary();
                //drawImageInCanvas(img, false);
                //currentTool.draw();
            }

            currentTool.onKeyPress(e);
        }
    }

    /**
     * Sets the current tool to be the polygon tool.
     */
    @FXML
    private void activatePolygonTool () {
        setCurrentTool(polygonTool);
    }

    /**
     * Sets the current tool to be the ellipse tool.
     */
    @FXML
    private void activateEllipseTool () {
        setCurrentTool(ellipseTool);
    }

    /**
     * Sets the current tool to be the select tool.
     */
    @FXML
    private void activateSelectTool () {
        setCurrentTool(selectTool);
    }

    /**
     * Wrapper to handle the ellipse tool box being visible.
     * @param selectedTool The tool to make current.
     */
    private void setCurrentTool(Tool selectedTool) {
        currentTool = selectedTool;

        if(selectedTool == ellipseTool) {
            if (!toolsToolBar.getItems().contains(ellipseSizeVBox)) toolsToolBar.getItems().add(toolsToolBar.getItems().indexOf(btnEllipse) + 1, ellipseSizeVBox);
        }
        else {
            if (toolsToolBar.getItems().contains(ellipseSizeVBox)) toolsToolBar.getItems().remove(ellipseSizeVBox);
        }

        // Handle no image being loaded.
        if (img == null) {
            currentTool = null;
        }
    }

    /**
     * Scales the mouse coordinates by the current scale size.
     * @param e The mouse event to scale.
     * @return The scaled mouse event.
     */
    private MouseEvent scaleMouseEvent (MouseEvent e) {
        MouseEvent se = new MouseEvent(e.getEventType(), e.getX() / polygons.getScale(), e.getY() / polygons.getScale(), e.getScreenX(), e.getScreenY(), e.getButton(), e.getClickCount(),
                e.isShiftDown(), e.isControlDown(), e.isAltDown(), e.isMetaDown(), e.isPrimaryButtonDown(), e.isMiddleButtonDown(),
                e.isSecondaryButtonDown(), e.isSynthesized(), e.isPopupTrigger(), e.isStillSincePress(), e.getPickResult());

        return se;
    }

    /**
     * Displays the settings form.
     */
    @FXML
    private void openSettingsForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("form_settings.fxml"));
            Parent root = loader.load();
            SettingsController cont = loader.getController();
            cont.setSettingsInfo(settingsInfo);
            cont.setHotkeysInfo(hotkeysInfo);

            Scene scene = new Scene(root);
            Stage stage = new Stage();
            stage.setScene(scene);
            cont.start();
            stage.show();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Changes the ellipse size whenever the enter key is pressed.
     * @param ke The keyboard event to use.
     */
    @FXML
    private void setEllipseToolSize (KeyEvent ke) {
        try {
            int size = Integer.parseInt(ellipseSizeTextField.textProperty().getValue());
            if (ke.getCode().equals(KeyCode.ENTER)) {
                ellipseSizeSlider.setValue(size);
                ellipseSizeTextField.positionCaret(ellipseSizeTextField.getLength());
            }
        }
        catch (Exception ex) {
            System.err.println(ex.getStackTrace());
        }
    }

    /**
     * Loads the settings and hotkeys from their respective XML files.
     */
    private void loadSettings () {
        try {
            File file = new File("Settings.xml");
            if (file.exists()) {
                settingsInfo = SettingsInfo.load("Settings.xml");
            } else {
                settingsInfo.loadDefault();
                settingsInfo.save("Settings.xml");
            }
            file = new File("Hotkeys.xml");
            if (file.exists()) {
                hotkeysInfo = HotkeysInfo.load("Hotkeys.xml");
            } else {
                hotkeysInfo.loadDefault();
                hotkeysInfo.save("Hotkeys.xml");
            }
        }
        catch (Exception ex) {
            System.err.println(ex.getStackTrace());
        }
    }
}
