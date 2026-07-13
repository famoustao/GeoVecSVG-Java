package com.geovecsvg.optimization;

import com.geovecsvg.model.*;
import java.util.*;

/**
 * 几何约束优化器
 * - 共点约束（多线交于一点）
 * - 平行/垂直约束
 * - 水平/垂直约束
 * - 共线合并
 * 使用 Gauss-Seidel 迭代优化
 */
public class ConstraintOptimizer {

    private final ProcessingParams params;

    public ConstraintOptimizer(ProcessingParams params) {
        this.params = params;
    }

    /**
     * 执行完整优化
     */
    public void optimize(VectorResult result) {
        if (!params.enableOptimization) return;

        for (int iter = 0; iter < params.optimizationIterations; iter++) {
            // 1. 端点吸附到最近顶点
            snapEndpointsToVertices(result);

            // 2. 水平/垂直约束
            if (params.enforceHorizontalVertical) {
                enforceHorizontalVertical(result);
            }

            // 3. 平行约束
            enforceParallelConstraints(result);

            // 4. 垂直约束
            enforcePerpendicularConstraints(result);

            // 5. 共线合并
            result.lines = mergeCollinearLines(result.lines);
        }

        // 重新计算顶点
        result.vertices = PrimitiveDetector.computeVertices(
                result.lines, result.imageWidth, result.imageHeight, params.snapDistance);
    }

    /**
     * 将线段端点吸附到最近的顶点
     */
    private void snapEndpointsToVertices(VectorResult result) {
        if (result.vertices.isEmpty()) return;

        for (LineSegment line : result.lines) {
            // 起点
            Point2D nearestStart = findNearestVertex(line.start, result.vertices);
            if (nearestStart != null && line.start.distanceTo(nearestStart) < params.snapDistance) {
                line.start = nearestStart;
            }
            // 终点
            Point2D nearestEnd = findNearestVertex(line.end, result.vertices);
            if (nearestEnd != null && line.end.distanceTo(nearestEnd) < params.snapDistance) {
                line.end = nearestEnd;
            }
        }
    }

    private Point2D findNearestVertex(Point2D p, List<Point2D> vertices) {
        Point2D nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Point2D v : vertices) {
            double d = p.distanceTo(v);
            if (d < minDist) {
                minDist = d;
                nearest = v;
            }
        }
        return nearest;
    }

    /**
     * 强制接近水平/垂直的线完全水平/垂直
     */
    private void enforceHorizontalVertical(VectorResult result) {
        double tol = params.angleToleranceDeg;

        for (LineSegment line : result.lines) {
            double angle = Math.abs(line.angleDegrees()) % 180;
            if (angle > 90) angle = 180 - angle;

            if (angle < tol) {
                // 接近水平：调整 y 坐标为中点 y
                double y = (line.start.y + line.end.y) / 2;
                line.start.y = y;
                line.end.y = y;
            } else if (Math.abs(angle - 90) < tol) {
                // 接近垂直：调整 x 坐标为中点 x
                double x = (line.start.x + line.end.x) / 2;
                line.start.x = x;
                line.end.x = x;
            }
        }
    }

    /**
     * 平行约束：将角度相近的线的角度取平均
     */
    private void enforceParallelConstraints(VectorResult result) {
        double tol = params.angleToleranceDeg;
        int n = result.lines.size();
        boolean[] processed = new boolean[n];

        for (int i = 0; i < n; i++) {
            if (processed[i]) continue;

            List<Integer> group = new ArrayList<>();
            group.add(i);
            processed[i] = true;

            double refAngle = result.lines.get(i).angleDegrees();

            boolean changed = true;
            while (changed) {
                changed = false;
                for (int j = 0; j < n; j++) {
                    if (processed[j]) continue;
                    double angle = result.lines.get(j).angleDegrees();
                    double diff = Math.abs(normalizeAngle(angle - refAngle));
                    if (diff < tol) {
                        group.add(j);
                        processed[j] = true;
                        changed = true;
                        // 更新参考角度（平均）
                        double sum = 0;
                        for (int idx : group) {
                            sum += result.lines.get(idx).angleDegrees();
                        }
                        refAngle = sum / group.size();
                    }
                }
            }

            // 将组内所有线调整为平均角度，保持中点不变
            double avgAngle = refAngle;
            for (int idx : group) {
                LineSegment line = result.lines.get(idx);
                double mx = (line.start.x + line.end.x) / 2;
                double my = (line.start.y + line.end.y) / 2;
                double halfLen = line.length() / 2;
                double rad = Math.toRadians(avgAngle);

                line.start.x = mx - Math.cos(rad) * halfLen;
                line.start.y = my - Math.sin(rad) * halfLen;
                line.end.x = mx + Math.cos(rad) * halfLen;
                line.end.y = my + Math.sin(rad) * halfLen;
            }
        }
    }

    /**
     * 垂直约束
     */
    private void enforcePerpendicularConstraints(VectorResult result) {
        double tol = params.angleToleranceDeg;
        int n = result.lines.size();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double a1 = result.lines.get(i).angleDegrees();
                double a2 = result.lines.get(j).angleDegrees();
                double diff = Math.abs(normalizeAngle(a2 - a1));
                if (Math.abs(diff - 90) < tol) {
                    // 调整为精确垂直，向两条线的平均方向靠拢
                    double target = (a1 + a2 + 90) / 2;
                    setLineAngle(result.lines.get(i), target - 45);
                    setLineAngle(result.lines.get(j), target + 45);
                }
            }
        }
    }

    private void setLineAngle(LineSegment line, double angleDeg) {
        double mx = (line.start.x + line.end.x) / 2;
        double my = (line.start.y + line.end.y) / 2;
        double halfLen = line.length() / 2;
        double rad = Math.toRadians(angleDeg);
        line.start.x = mx - Math.cos(rad) * halfLen;
        line.start.y = my - Math.sin(rad) * halfLen;
        line.end.x = mx + Math.cos(rad) * halfLen;
        line.end.y = my + Math.sin(rad) * halfLen;
    }

    private double normalizeAngle(double angle) {
        angle = angle % 180;
        if (angle > 90) angle -= 180;
        if (angle < -90) angle += 180;
        return angle;
    }

    /**
     * 合并共线且重叠的线段
     */
    private List<LineSegment> mergeCollinearLines(List<LineSegment> lines) {
        return PrimitiveDetector.mergeCollinearLines(lines, params.angleToleranceDeg, params.snapDistance);
    }
}
