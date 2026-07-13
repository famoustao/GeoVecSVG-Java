package com.geovecsvg.model;

/**
 * 线段
 */
public class LineSegment {
    public Point2D start;
    public Point2D end;
    public double confidence;

    public LineSegment(Point2D start, Point2D end) {
        this(start, end, 1.0);
    }

    public LineSegment(Point2D start, Point2D end, double confidence) {
        this.start = start;
        this.end = end;
        this.confidence = confidence;
    }

    public double length() {
        return start.distanceTo(end);
    }

    public double angleDegrees() {
        return Math.toDegrees(Math.atan2(end.y - start.y, end.x - start.x));
    }

    // 获取直线方程 Ax + By + C = 0
    public double[] getLineEquation() {
        double A = end.y - start.y;
        double B = start.x - end.x;
        double C = end.x * start.y - start.x * end.y;
        return new double[]{A, B, C};
    }

    // 计算两条直线的交点
    public static Point2D intersection(LineSegment l1, LineSegment l2) {
        double[] eq1 = l1.getLineEquation();
        double[] eq2 = l2.getLineEquation();
        double A1 = eq1[0], B1 = eq1[1], C1 = eq1[2];
        double A2 = eq2[0], B2 = eq2[1], C2 = eq2[2];

        double det = A1 * B2 - A2 * B1;
        if (Math.abs(det) < 1e-10) return null; // 平行

        double x = (B1 * C2 - B2 * C1) / det;
        double y = (A2 * C1 - A1 * C2) / det;
        return new Point2D(x, y);
    }

    // 点到直线的距离
    public double distanceToPoint(Point2D p) {
        double[] eq = getLineEquation();
        double A = eq[0], B = eq[1], C = eq[2];
        return Math.abs(A * p.x + B * p.y + C) / Math.sqrt(A * A + B * B);
    }

    public LineSegment copy() {
        return new LineSegment(start.copy(), end.copy(), confidence);
    }

    @Override
    public String toString() {
        return String.format("Line[%s -> %s]", start, end);
    }
}
