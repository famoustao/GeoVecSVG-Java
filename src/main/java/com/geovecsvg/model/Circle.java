package com.geovecsvg.model;

/**
 * 圆
 */
public class Circle {
    public Point2D center;
    public double radius;
    public double confidence;

    public Circle(Point2D center, double radius) {
        this(center, radius, 1.0);
    }

    public Circle(Point2D center, double radius, double confidence) {
        this.center = center;
        this.radius = radius;
        this.confidence = confidence;
    }

    public Circle copy() {
        return new Circle(center.copy(), radius, confidence);
    }

    @Override
    public String toString() {
        return String.format("Circle[center=%s, r=%.2f]", center, radius);
    }
}
