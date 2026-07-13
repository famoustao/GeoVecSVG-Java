package com.geovecsvg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 多边形（填充区域）
 */
public class Polygon {
    public List<Point2D> points;
    public String fillColor;

    public Polygon() {
        this.points = new ArrayList<>();
        this.fillColor = "#ffcccc";
    }

    public Polygon(List<Point2D> points) {
        this.points = points;
        this.fillColor = "#ffcccc";
    }

    public void addPoint(Point2D p) {
        points.add(p);
    }

    public int size() {
        return points.size();
    }

    public Polygon copy() {
        List<Point2D> copyPoints = new ArrayList<>();
        for (Point2D p : points) {
            copyPoints.add(p.copy());
        }
        Polygon p = new Polygon(copyPoints);
        p.fillColor = this.fillColor;
        return p;
    }
}
