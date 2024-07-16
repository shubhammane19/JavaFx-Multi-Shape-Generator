import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.util.Duration;
 
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Random;
 
public class OverlayIt extends Application {
  private static final int    N_SHAPES   = 1000;
  private static final double PAUSE_SECS = 3;
  private static final double FADE_SECS  = 0;
  private static final String IMAGE_URL  = "https://farm9.staticflickr.com/8205/8264285440_f5617efb71_b.jpg";
 
  private ShapeMachine machine;
  private ParallelDimension parallel;
  private ContinualShapeGenerator generator;
  private VisualizedImage visualizedImage;
 
  public static void main(String[] args) { launch(args); }
 
  @Override
  public void init() throws MalformedURLException, URISyntaxException {
    visualizedImage = new VisualizedImage(IMAGE_URL, FADE_SECS);
    double maxShapeSize = visualizedImage.getWidth() / 8;
    double minShapeSize = maxShapeSize / 2;
    machine = new ShapeMachine(visualizedImage.getWidth(), visualizedImage.getHeight(), maxShapeSize, minShapeSize);
    parallel = new ParallelDimension(machine, N_SHAPES);
    generator = new ContinualShapeGenerator(parallel, visualizedImage, PAUSE_SECS);
  }
 
  @Override public void start(final Stage stage) throws IOException, URISyntaxException {
    Scene scene = new Scene(visualizedImage);
    configureExitOnAnyKey(stage, scene);
 
    stage.setScene(scene);
    stage.setResizable(false);
    stage.show();
 
    generator.generate();
  }
 
  private void configureExitOnAnyKey(final Stage stage, Scene scene) {
    scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
      @Override
      public void handle(KeyEvent keyEvent) {
        stage.hide();
      }
    });
  }
 
  static String getResource(String resourceName) {
    if (resourceName.matches("file:.*|http:.*|https:.*|jar:.*"))
      return resourceName;
 
    try {
      return OverlayIt.class.getResource(resourceName).toURI().toURL().toExternalForm();
    } catch (Exception e) {
      System.out.println("Unable to retrieve resource: " + resourceName);
      e.printStackTrace();
    }
 
    return null;
  }
}
 
class VisualizedImage extends Group {
  private final Image backgroundImage;
  private final Group visualizer;
  private final FadeTransition fadeIn, fadeOut;
  private final boolean doFade;
 
  VisualizedImage(String location, double fadeDuration) {
    backgroundImage = new Image(OverlayIt.getResource(location));
    visualizer = new Group();
 
    getChildren().add(new ImageView(backgroundImage));
    getChildren().add(visualizer);
    setClip(new Rectangle(0, 0, backgroundImage.getWidth(), backgroundImage.getHeight()));
 
    doFade = fadeDuration > 0;
 
    fadeIn = new FadeTransition(Duration.seconds(fadeDuration), visualizer);
    fadeIn.setFromValue(0.4);
    fadeIn.setToValue(1.0);
 
    fadeOut = new FadeTransition(Duration.seconds(fadeDuration), visualizer);
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.4);
  }
 
  public double getWidth() {
    return backgroundImage.getWidth();
  }
 
  public double getHeight() {
    return backgroundImage.getHeight();
  }
 
  public void replaceOverlay(final ObservableList<Shape> shapes) {
    if (!doFade) {
      visualizer.getChildren().setAll(shapes);
    } else {
      fadeIn.stop();
      fadeOut.play();
      fadeOut.setOnFinished(new EventHandler<ActionEvent>() {
        @Override
        public void handle(ActionEvent actionEvent) {
          visualizer.getChildren().setAll(shapes);
          fadeIn.play();
        }
      });
    }
  }
}
 
class ContinualShapeGenerator {
  private Timeline updater = new Timeline();
 
  ContinualShapeGenerator(final ParallelDimension parallel, final VisualizedImage visualizer, final double pauseSecs) {
    updater.getKeyFrames().addAll(
      new KeyFrame(
        Duration.seconds(pauseSecs),
        new EventHandler<ActionEvent>() {
          @Override
          public void handle(ActionEvent actionEvent) {
            if (Service.State.READY == parallel.getState()) {
              parallel.start();
            } else {
              System.out.println("Frame skipped");
            }
          }
        }
      )
    );
 
    parallel.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
      @Override public void handle(WorkerStateEvent workerStateEvent) {
        visualizer.replaceOverlay(parallel.getValue());
        parallel.reset();
        updater.play();
      }
    });
 
    parallel.setOnFailed(new EventHandler<WorkerStateEvent>() {
      @Override
      public void handle(WorkerStateEvent workerStateEvent) {
        System.out.println("Parallel task failed");
        parallel.getException().printStackTrace();
        updater.play();
      }
    });
  }
 
  public void generate() {
    updater.play();
  }
}
 
class ParallelDimension extends Service<ObservableList<Shape>> {
  private final ShapeMachine machine;
  private final int nShapes;
 
  ParallelDimension(ShapeMachine machine, int nShapes) {
    this.machine = machine;
    this.nShapes = nShapes;
  }
 
  @Override protected Task<ObservableList<Shape>> createTask() {
    return new Task<ObservableList<Shape>>() {
      @Override protected ObservableList<Shape> call() throws Exception {
        ObservableList<Shape> shapes = FXCollections.observableArrayList();
        for (int i = 0; i < nShapes; i++) {
          shapes.add(machine.randomShape());
        }
 
        return shapes;
      }
    };
  }
}
 
class ShapeMachine {
 
  private static final Random random = new Random();
  private final double canvasWidth, canvasHeight, maxShapeSize, minShapeSize;
 
  ShapeMachine(double canvasWidth, double canvasHeight, double maxShapeSize, double minShapeSize) {
    this.canvasWidth  = canvasWidth;
    this.canvasHeight = canvasHeight;
    this.maxShapeSize = maxShapeSize;
    this.minShapeSize = minShapeSize;
  }
 
  private Color randomColor() {
    return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256), 0.1 + random.nextDouble() * 0.9);
  }
 
  enum Shapes { Circle, Rectangle, Line }
 
  public Shape randomShape() {
    Shape shape = null;
 
    switch (Shapes.values()[random.nextInt(Shapes.values().length)]) {
      case Circle:    shape = randomCircle();    break;
      case Rectangle: shape = randomRectangle(); break;
      case Line:      shape = randomLine();      break;
      default: System.out.println("Unknown Shape"); System.exit(1);
    }
 
    Color fill = randomColor();
    shape.setFill(fill);
    shape.setStroke(deriveStroke(fill));
    shape.setStrokeWidth(deriveStrokeWidth(shape));
    shape.setStrokeLineCap(StrokeLineCap.ROUND);
    shape.relocate(randomShapeX(), randomShapeY());
 
    return shape;
  }
 
  private double deriveStrokeWidth(Shape shape) {
    return Math.max(shape.getLayoutBounds().getWidth() / 10, shape.getLayoutBounds().getHeight() / 10);
  }
 
  private Color deriveStroke(Color fill) {
    return fill.desaturate();
  }
 
  private double randomShapeSize() {
    double range = maxShapeSize - minShapeSize;
    return random.nextDouble() * range + minShapeSize;
  }
 
  private double randomShapeX() {
    return random.nextDouble() * (canvasWidth + maxShapeSize) - maxShapeSize / 2;
  }
 
  private double randomShapeY() {
    return random.nextDouble() * (canvasHeight + maxShapeSize) - maxShapeSize / 2;
  }
 
  private Shape randomLine() {
    int xZero = random.nextBoolean() ? 1 : 0;
    int yZero = random.nextBoolean() || xZero == 0 ? 1 : 0;
 
    int xSign = random.nextBoolean() ? 1 : -1;
    int ySign = random.nextBoolean() ? 1 : -1;
 
    return new Line(0, 0, xZero * xSign * randomShapeSize(), yZero * ySign * randomShapeSize());
  }
 
  private Shape randomRectangle() {
    return new Rectangle(0, 0, randomShapeSize(), randomShapeSize());
  }
 
  private Shape randomCircle() {
    double radius = randomShapeSize() / 2;
    return new Circle(radius, radius, radius);
  }
}