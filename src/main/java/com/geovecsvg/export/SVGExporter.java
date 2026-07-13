package com.geovecsvg.export;

import com.geovecsvg.model.*;
import java.util.List;

/**
 * SVG 精确矢量图导出器
 * 所有线条都是数学绝对直线，圆由圆心+半径精确描述
 */
public class SVGExporter {

    private static final String STROKE_COLOR = "#1a1a2e";
    private static final double STROKE_WIDTH = 1.5;
    private static final String CIRCLE_COLOR = "#e94560";
    private static final double CIRCLE_STROKE = 1.5;
    private static final String VERTEX_COLOR = "#0f3460";
    private static final double VERTEX_RADIUS = 2.5;

    public static String export(VectorResult result) {
        return export(result, true, true, true);
    }

    public static String export(VectorResult result, boolean showVertices, boolean showCircles, boolean showFill) {
        StringBuilder sb = new StringBuilder();

        // SVG 头
        int w = result.imageWidth;
        int h = result.imageHeight;
        sb.append(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
        sb.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" ", w, h));
        sb.append(String.format("viewBox=\"0 0 %d %d\">\n", w, h));
        sb.append(String.format("  <defs>\n"));
        sb.append(String.format("    <style>\n"));
        sb.append(String.format("      .line { stroke: %s; stroke-width: %.1f; fill: none; stroke-linecap: round; }\n",
                STROKE_COLOR, STROKE_WIDTH));
        sb.append(String.format("      .circle { stroke: %s; stroke-width: %.1f; fill: none; }\n",
                CIRCLE_COLOR, CIRCLE_STROKE));
        sb.append(String.format("      .vertex { fill: %s; }\n", VERTEX_COLOR));
        sb.append(String.format("      .fill-region { fill-opacity: 0.5; stroke: none; }\n"));
        sb.append(String.format("    </style>\n"));
        sb.append(String.format("  </defs>\n\n"));

        // 白色背景
        sb.append(String.format("  <rect width=\"%d\" height=\"%d\" fill=\"white\"/>\n\n", w, h));

        // 填充区域
        if (showFill) {
            sb.append("  <!-- Filled Regions -->\n");
            for (Polygon poly : result.filledRegions) {
                sb.append("  <path class=\"fill-region\" d=\"");
                List<Point2D> pts = poly.points;
                if (!pts.isEmpty()) {
                    sb.append(String.format("M %.1f %.1f ", pts.get(0).x, pts.get(0).y));
                    for (int i = 1; i < pts.size(); i++) {
                        sb.append(String.format("L %.1f %.1f ", pts.get(i).x, pts.get(i).y));
                    }
                    sb.append("Z");
                }
                sb.append(String.format("\" fill=\"%s\"/>\n", poly.fillColor));
            }
            sb.append("\n");
        }

        // 线条（数学绝对直线）
        sb.append("  <!-- Line Segments -->\n");
        sb.append("  <g class=\"lines\">\n");
        for (LineSegment line : result.lines) {
            sb.append(String.format("    <line class=\"line\" x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\"/>\n",
                    line.start.x, line.start.y, line.end.x, line.end.y));
        }
        sb.append("  </g>\n\n");

        // 圆
        if (showCircles && !result.circles.isEmpty()) {
            sb.append("  <!-- Circles -->\n");
            sb.append("  <g class=\"circles\">\n");
            for (Circle c : result.circles) {
                sb.append(String.format("    <circle class=\"circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"/>\n",
                        c.center.x, c.center.y, c.radius));
            }
            sb.append("  </g>\n\n");
        }

        // 顶点
        if (showVertices && !result.vertices.isEmpty()) {
            sb.append("  <!-- Vertices -->\n");
            sb.append("  <g class=\"vertices\">\n");
            for (Point2D v : result.vertices) {
                sb.append(String.format("    <circle class=\"vertex\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.1f\"/>\n",
                        v.x, v.y, VERTEX_RADIUS));
            }
            sb.append("  </g>\n\n");
        }

        sb.append("</svg>\n");
        return sb.toString();
    }
}
