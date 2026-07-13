package com.geovecsvg.gui;

import com.geovecsvg.ProcessingPipeline;
import com.geovecsvg.export.GeoGebraExporter;
import com.geovecsvg.export.SVGExporter;
import com.geovecsvg.model.ProcessingParams;
import com.geovecsvg.model.VectorResult;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * GeoVecSVG JavaFX 主界面
 * 现代风格，左侧控制面板 + 右侧画布预览
 */
public class MainApp extends Application {

    private ProcessingParams params = new ProcessingParams();
    private Mat currentImage;
    private VectorResult currentResult;
    private Canvas previewCanvas;
    private Label statusLabel;
    private Label lineCountLabel;
    private Label circleCountLabel;
    private Label vertexCountLabel;
    private Label fillCountLabel;
    private ProgressBar progressBar;
    private ImageView originalImageView;
    private ToggleButton showOriginalBtn;
    private double zoomLevel = 1.0;
    private double panX = 0, panY = 0;

    @Override
    public void start(Stage stage) {
        stage.setTitle("GeoVecSVG - 几何题图精确矢量化工具");
        stage.setMinWidth(1200);
        stage.setMinHeight(700);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // 左侧控制面板
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // 右侧画布区域
        BorderPane canvasArea = createCanvasArea();
        root.setCenter(canvasArea);

        // 底部状态栏
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("css/style.css").toExternalForm());

        stage.setScene(scene);
        stage.show();

        updateStatus("就绪", "ready");
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(320);

        ScrollPane scrollPane = new ScrollPane(sidebar);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");

        VBox content = new VBox();
        content.setSpacing(4);
        content.setPadding(new Insets(0, 4, 0, 0));

        // 标题
        Label title = new Label("GeoVecSVG");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("几何题图精确矢量化工具");
        subtitle.getStyleClass().add("app-subtitle");

        // 文件操作按钮
        HBox btnRow1 = new HBox(8);
        Button openBtn = new Button("打开图片");
        openBtn.getStyleClass().add("btn-primary");
        openBtn.setMaxWidth(Double.MAX_VALUE);
        openBtn.setOnAction(e -> openImage());

        Button processBtn = new Button("开始矢量化");
        processBtn.getStyleClass().add("btn-success");
        processBtn.setMaxWidth(Double.MAX_VALUE);
        processBtn.setOnAction(e -> processImage());

        HBox btnRow2 = new HBox(8);
        Button exportSvgBtn = new Button("导出 SVG");
        exportSvgBtn.getStyleClass().add("btn-secondary");
        exportSvgBtn.setMaxWidth(Double.MAX_VALUE);
        exportSvgBtn.setOnAction(e -> exportSVG());

        Button exportGgbBtn = new Button("导出 GeoGebra");
        exportGgbBtn.getStyleClass().add("btn-secondary");
        exportGgbBtn.setMaxWidth(Double.MAX_VALUE);
        exportGgbBtn.setOnAction(e -> exportGeoGebra());

        HBox.setHgrow(openBtn, Priority.ALWAYS);
        HBox.setHgrow(processBtn, Priority.ALWAYS);
        HBox.setHgrow(exportSvgBtn, Priority.ALWAYS);
        HBox.setHgrow(exportGgbBtn, Priority.ALWAYS);
        btnRow1.getChildren().addAll(openBtn, processBtn);
        btnRow2.getChildren().addAll(exportSvgBtn, exportGgbBtn);

        // 统计卡片
        HBox statsRow = new HBox(8);
        statsRow.getStyleClass().add("stats-card");
        statsRow.setPadding(new Insets(10, 12, 10, 12));

        VBox lineStat = createStatCard("线段", "0");
        VBox circleStat = createStatCard("圆", "0");
        VBox vertexStat = createStatCard("顶点", "0");
        VBox fillStat = createStatCard("区域", "0");

        lineCountLabel = (Label) lineStat.getChildren().get(0);
        circleCountLabel = (Label) circleStat.getChildren().get(0);
        vertexCountLabel = (Label) vertexStat.getChildren().get(0);
        fillCountLabel = (Label) fillStat.getChildren().get(0);

        statsRow.getChildren().addAll(lineStat, circleStat, vertexStat, fillStat);

        content.getChildren().addAll(title, subtitle, btnRow1, btnRow2, statsRow);

        // === 预处理参数
        Label sec1 = new Label("图像预处理");
        sec1.getStyleClass().add("section-title");

        Slider denoiseSlider = createSlider("去噪强度", 1, 9, params.denoiseSize,
                v -> params.denoiseSize = v.intValue());
        Slider morphSlider = createSlider("形态学闭运算", 1, 8, (int)params.morphCloseSize,
                v -> params.morphCloseSize = v);

        CheckBox fillCheck = new CheckBox("检测填充区域");
        fillCheck.setSelected(params.detectFilledRegions);
        fillCheck.getStyleClass().add("check-box");
        fillCheck.selectedProperty().addListener((obs, o, n) -> params.detectFilledRegions = n);

        content.getChildren().addAll(sec1, denoiseSlider, morphSlider, fillCheck);

        // === 直线检测参数
        Label sec2 = new Label("直线检测");
        sec2.getStyleClass().add("section-title");

        Slider houghThreshSlider = createSlider("霍夫阈值", 20, 200, params.houghThreshold,
                v -> params.houghThreshold = v.intValue());
        Slider minLenSlider = createSlider("最小线长", 10, 200, (int)params.minLineLength,
                v -> params.minLineLength = v);
        Slider maxGapSlider = createSlider("最大间隙", 1, 50, (int)params.maxLineGap,
                v -> params.maxLineGap = v);

        content.getChildren().addAll(sec2, houghThreshSlider, minLenSlider, maxGapSlider);

        // === 圆检测参数
        Label sec3 = new Label("圆检测");
        sec3.getStyleClass().add("section-title");

        Slider circParamSlider = createSlider("圆检测阈值", 10, 80, (int)params.houghCircParam2,
                v -> params.houghCircParam2 = v);
        Slider minRadiusSlider = createSlider("最小半径", 2, 50, params.minCircleRadius,
                v -> params.minCircleRadius = v.intValue());
        Slider maxRadiusSlider = createSlider("最大半径", 20, 300, params.maxCircleRadius,
                v -> params.maxCircleRadius = v.intValue());

        content.getChildren().addAll(sec3, circParamSlider, minRadiusSlider, maxRadiusSlider);

        // === 约束优化参数
        Label sec4 = new Label("几何约束优化");
        sec4.getStyleClass().add("section-title");

        CheckBox optCheck = new CheckBox("启用约束优化");
        optCheck.setSelected(params.enableOptimization);
        optCheck.getStyleClass().add("check-box");
        optCheck.selectedProperty().addListener((obs, o, n) -> params.enableOptimization = n);

        CheckBox hvCheck = new CheckBox("强制水平/垂直");
        hvCheck.setSelected(params.enforceHorizontalVertical);
        hvCheck.getStyleClass().add("check-box");
        hvCheck.selectedProperty().addListener((obs, o, n) -> params.enforceHorizontalVertical = n);

        Slider snapSlider = createSlider("吸附距离 (px)", 1, 10, (int)params.snapDistance,
                v -> params.snapDistance = v);
        Slider angleSlider = createSlider("角度容差 (°)", 0.5, 5.0, params.angleToleranceDeg,
                v -> params.angleToleranceDeg = v);
        Slider iterSlider = createSlider("优化迭代次数", 5, 50, params.optimizationIterations,
                v -> params.optimizationIterations = v.intValue());

        content.getChildren().addAll(sec4, optCheck, hvCheck, snapSlider, angleSlider, iterSlider);

        VBox wrapper = new VBox(content);
        wrapper.setPadding(new Insets(16, 12, 20, 4));
        ScrollPane sp = new ScrollPane(wrapper);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-pane");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox sidebarBox = new VBox(sp);
        sidebarBox.setPrefWidth(320);
        sidebarBox.getStyleClass().add("sidebar");
        return sidebarBox;
    }

    private VBox createStatCard(String label, String value) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(60);
        Label valLabel = new Label(value);
        valLabel.getStyleClass().add("stats-value");
        Label lblLabel = new Label(label);
        lblLabel.getStyleClass().add("stats-label");
        box.getChildren().addAll(valLabel, lblLabel);
        return box;
    }

    private VBox createSlider(String labelText, double min, double max, double initialValue,
                             java.util.function.Consumer<Double> onChange) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(4, 0, 4, 0));

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(labelText);
        label.getStyleClass().add("control-label");
        Label valueLabel = new Label(String.format("%.0f", initialValue));
        valueLabel.getStyleClass().add("control-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(label, spacer, valueLabel);

        Slider slider = new Slider(min, max, initialValue);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setMinorTickCount(4);
        slider.valueProperty().addListener((obs, o, n) -> {
            double v = n.doubleValue();
            valueLabel.setText(String.format("%.1f", v));
            onChange.accept(v);
        });

        box.getChildren().addAll(header, slider);
        return box;
    }

    private BorderPane createCanvasArea() {
        BorderPane area = new BorderPane();
        area.getStyleClass().add("canvas-container");
        area.setPadding(new Insets(16));

        // 顶部工具栏
        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("canvas-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        showOriginalBtn = new ToggleButton("原图");
        showOriginalBtn.setSelected(false);
        showOriginalBtn.selectedProperty().addListener((obs, o, n) -> updateCanvasDisplay());

        Button zoomInBtn = new Button("+");
        zoomInBtn.getStyleClass().add("btn-secondary");
        zoomInBtn.setOnAction(e -> { zoomLevel *= 1.2; updateCanvasDisplay(); });

        Button zoomOutBtn = new Button("-");
        zoomOutBtn.getStyleClass().add("btn-secondary");
        zoomOutBtn.setOnAction(e -> { zoomLevel /= 1.2; updateCanvasDisplay(); });

        Button fitBtn = new Button("适应窗口");
        fitBtn.getStyleClass().add("btn-secondary");
        fitBtn.setOnAction(e -> fitToWindow());

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(showOriginalBtn, zoomOutBtn, zoomInBtn, fitBtn, spacer, progressBar);

        // 画布
        StackPane canvasHolder = new StackPane();
        previewCanvas = new Canvas(800, 600);
        canvasHolder.getChildren().add(previewCanvas);

        originalImageView = new ImageView();
        originalImageView.setVisible(false);
        originalImageView.setPreserveRatio(true);
        canvasHolder.getChildren().add(originalImageView);

        // 拖拽平移
        final double[] dragStart = new double[2];
        canvasHolder.setOnMousePressed(e -> {
            dragStart[0] = e.getX() - panX;
            dragStart[1] = e.getY() - panY;
        });
        canvasHolder.setOnMouseDragged(e -> {
            panX = e.getX() - dragStart[0];
            panY = e.getY() - dragStart[1];
            updateCanvasDisplay();
        });
        canvasHolder.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            zoomLevel *= factor;
            updateCanvasDisplay();
        });

        area.setTop(toolbar);
        area.setCenter(canvasHolder);

        return area;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("就绪");
        statusLabel.getStyleClass().addAll("status-indicator", "ready");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label info = new Label("GeoVecSVG v1.0");

        bar.getChildren().addAll(statusLabel, spacer, info);
        return bar;
    }

    // ===== 事件处理 =====

    private void openImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择几何题图片");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.bmp"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        File file = chooser.showOpenDialog(null);
        if (file == null) return;

        try {
            currentImage = opencv_imgcodecs.imread(file.getAbsolutePath());
            if (currentImage.empty()) {
                updateStatus("无法加载图片失败", "error");
                return;
            }
            currentResult = null;
            updateStatus("已加载: " + file.getName(), "ready");
            showOriginalBtn.setSelected(true);
            loadOriginalImage();
            fitToWindow();
        } catch (Exception ex) {
            updateStatus("加载失败: " + ex.getMessage(), "error");
        }
    }

    private void loadOriginalImage() {
        if (currentImage == null) return;
        Image img = matToImage(currentImage);
        originalImageView.setImage(img);
    }

    private void processImage() {
        if (currentImage == null) {
            updateStatus("请先打开图片", "error");
            return;
        }

        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        updateStatus("处理中...", "processing");

        new Thread(() -> {
            try {
                ProcessingPipeline pipeline = new ProcessingPipeline(params);
                VectorResult result = pipeline.process(currentImage);

                javafx.application.Platform.runLater(() -> {
                    currentResult = result;
                    progressBar.setVisible(false);
                    updateStatus("处理完成", "ready");
                    updateStats(result);
                    showOriginalBtn.setSelected(false);
                    updateCanvasDisplay();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    updateStatus("处理失败: " + ex.getMessage(), "error");
                    ex.printStackTrace();
                });
            }
        }).start();
    }

    private void updateStats(VectorResult r) {
        lineCountLabel.setText(String.valueOf(r.lines.size()));
        circleCountLabel.setText(String.valueOf(r.circles.size()));
        vertexCountLabel.setText(String.valueOf(r.vertices.size()));
        fillCountLabel.setText(String.valueOf(r.filledRegions.size()));
    }

    private void updateCanvasDisplay() {
        if (showOriginalBtn.isSelected() && currentImage != null) {
            previewCanvas.setVisible(false);
            originalImageView.setVisible(true);
            originalImageView.setScaleX(zoomLevel);
            originalImageView.setScaleY(zoomLevel);
            originalImageView.setTranslateX(panX);
            originalImageView.setTranslateY(panY);
        } else if (currentResult != null) {
            previewCanvas.setVisible(true);
            originalImageView.setVisible(false);
            drawVectorResult();
        } else {
            previewCanvas.setVisible(true);
            originalImageView.setVisible(false);
            // 清空画布
            GraphicsContext gc = previewCanvas.getGraphicsContext2D();
            gc.setFill(Color.web("#f1f5f9"));
            gc.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
        }
    }

    private void drawVectorResult() {
        if (currentResult == null) return;

        double w = currentResult.imageWidth * zoomLevel;
        double h = currentResult.imageHeight * zoomLevel;
        previewCanvas.setWidth(w + 40);
        previewCanvas.setHeight(h + 40);

        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

        gc.save();
        gc.translate(20 + panX, 20 + panY);
        gc.scale(zoomLevel, zoomLevel);

        // 白色背景
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, currentResult.imageWidth, currentResult.imageHeight);

        // 填充区域
        for (var poly : currentResult.filledRegions) {
            gc.setFill(Color.web(poly.fillColor + "80"));
            double[] xs = new double[poly.size()];
            double[] ys = new double[poly.size()];
            for (int i = 0; i < poly.size(); i++) {
                xs[i] = poly.points.get(i).x;
                ys[i] = poly.points.get(i).y;
            }
            gc.fillPolygon(xs, ys, poly.size());
        }

        // 线条
        gc.setStroke(Color.web("#1a1a2e"));
        gc.setLineWidth(1.5 / zoomLevel);
        gc.setLineCap(StrokeLineCap.ROUND);
        for (var line : currentResult.lines) {
            gc.strokeLine(line.start.x, line.start.y, line.end.x, line.end.y);
        }

        // 圆
        gc.setStroke(Color.web("#e94560"));
        gc.setLineWidth(1.5 / zoomLevel);
        for (var c : currentResult.circles) {
            gc.strokeOval(c.center.x - c.radius, c.center.y - c.radius, c.radius * 2, c.radius * 2);
        }

        // 顶点
        gc.setFill(Color.web("#0f3460"));
        double vr = 2.5 / zoomLevel;
        for (var v : currentResult.vertices) {
            gc.fillOval(v.x - vr, v.y - vr, vr * 2, vr * 2);
        }

        gc.restore();
    }

    private void fitToWindow() {
        if (currentImage == null && currentResult == null) return;
        double iw = currentResult != null ? currentResult.imageWidth : currentImage.cols();
        double ih = currentResult != null ? currentResult.imageHeight : currentImage.rows();

        double cw = previewCanvas.getParent().getLayoutBounds().getWidth() - 60;
        double ch = previewCanvas.getParent().getLayoutBounds().getHeight() - 60;
        if (cw <= 0 || ch <= 0) { cw = 800; ch = 500; }

        zoomLevel = Math.min(cw / iw, ch / ih);
        panX = 0;
        panY = 0;
        updateCanvasDisplay();
    }

    private void exportSVG() {
        if (currentResult == null) {
            updateStatus("请先进行矢量化处理", "error");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出 SVG");
        chooser.setInitialFileName("output.svg");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG 文件", "*.svg"));
        File file = chooser.showSaveDialog(null);
        if (file == null) return;

        try {
            String svg = SVGExporter.export(currentResult);
            Files.write(Paths.get(file.getAbsolutePath()), svg.getBytes("UTF-8"));
            updateStatus("SVG 已导出: " + file.getName(), "ready");
        } catch (Exception ex) {
            updateStatus("导出失败: " + ex.getMessage(), "error");
        }
    }

    private void exportGeoGebra() {
        if (currentResult == null) {
            updateStatus("请先进行矢量化处理", "error");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出 GeoGebra");
        chooser.setInitialFileName("output_ggb.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));
        File file = chooser.showSaveDialog(null);
        if (file == null) return;

        try {
            String cmds = GeoGebraExporter.exportCommands(currentResult);
            Files.write(Paths.get(file.getAbsolutePath()), cmds.getBytes("UTF-8"));
            updateStatus("GeoGebra 已导出: " + file.getName(), "ready");
        } catch (Exception ex) {
            updateStatus("导出失败: " + ex.getMessage(), "error");
        }
    }

    private void updateStatus(String text, String type) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("ready", "processing", "error");
        statusLabel.getStyleClass().add(type);
    }

    // OpenCV Mat -> JavaFX Image
    private Image matToImage(Mat mat) {
        Mat rgb = new Mat();
        if (mat.channels() == 1) {
            opencv_imgproc.cvtColor(mat, rgb, opencv_imgproc.COLOR_GRAY2BGR);
        } else {
            opencv_imgproc.cvtColor(mat, rgb, opencv_imgproc.COLOR_BGR2RGB);
        }

        int w = rgb.cols();
        int h = rgb.rows();
        WritableImage image = new WritableImage(w, h);
        PixelWriter pw = image.getPixelWriter();

        byte[] buffer = new byte[w * h * 3];
        rgb.data().get(buffer);

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = buffer[idx] & 0xFF;
                int g = buffer[idx + 1] & 0xFF;
                int b = buffer[idx + 2] & 0xFF;
                pw.setArgb(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                idx += 3;
            }
        }

        rgb.close();
        return image;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
