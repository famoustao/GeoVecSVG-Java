package com.geovecsvg.gui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 带标签和数值显示的滑块控件
 */
public class SliderControl extends VBox {

    private final Slider slider;
    private final Label valueLabel;
    private final String format;

    public SliderControl(String labelText, double min, double max, double initialValue,
                         String format, Consumer<Double> onChange) {
        super(4);
        this.format = format;
        setPadding(new javafx.geometry.Insets(4, 0, 4, 0));

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(labelText);
        label.getStyleClass().add("control-label");
        valueLabel = new Label(String.format(format, initialValue));
        valueLabel.getStyleClass().add("control-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(label, spacer, valueLabel);

        slider = new Slider(min, max, initialValue);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setMinorTickCount(4);
        slider.valueProperty().addListener((obs, o, n) -> {
            double v = n.doubleValue();
            valueLabel.setText(String.format(format, v));
            onChange.accept(v);
        });

        getChildren().addAll(header, slider);
    }

    public Slider getSlider() {
        return slider;
    }

    public double getValue() {
        return slider.getValue();
    }
}
