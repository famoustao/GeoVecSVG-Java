package com.geovecsvg.export;

import com.geovecsvg.model.*;
import java.util.List;

/**
 * GeoGebra 导出器
 * 支持两种格式：
 * 1. GeoGebra 命令脚本（可直接粘贴到 GeoGebra 输入栏执行）
 * 2. GeoGebra XML (.ggb 文件内容的一部分)
 */
public class GeoGebraExporter {

    /**
     * 导出为 GeoGebra 命令脚本
     * 图像坐标转换：GeoGebra y轴向上，SVG y轴向下
     */
    public static String exportCommands(VectorResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# GeoVecSVG - 几何题图矢量化结果\n");
        sb.append("# 可直接复制粘贴到 GeoGebra 输入栏执行\n\n");

        double h = result.imageHeight;

        // 顶点
        sb.append("# === 顶点 ===\n");
        char label = 'A';
        int idx = 0;
        for (Point2D v : result.vertices) {
            String name;
            if (idx < 26) {
                name = String.valueOf((char)('A' + idx));
            } else {
                name = "V" + (idx + 1);
            }
            // y 轴翻转
            double gy = h - v.y;
            sb.append(String.format("%s = (%s(%s, 2), %s(%s, 2))\n",
                    name, "Round", v.x, "Round", gy));
            idx++;
        }
        sb.append("\n");

        // 线段
        sb.append("# === 线段 ===\n");
        for (int i = 0; i < result.lines.size(); i++) {
            LineSegment line = result.lines.get(i);
            double sx = line.start.x, sy = h - line.start.y;
            double ex = line.end.x, ey = h - line.end.y;
            sb.append(String.format("Segment((%.1f, %.1f), (%.1f, %.1f))\n", sx, sy, ex, ey));
        }
        sb.append("\n");

        // 圆
        if (!result.circles.isEmpty()) {
            sb.append("# === 圆 ===\n");
            for (int i = 0; i < result.circles.size(); i++) {
                Circle c = result.circles.get(i);
                double cy = h - c.center.y;
                sb.append(String.format("Circle((%.1f, %.1f), %.1f)\n", c.center.x, cy, c.radius));
            }
            sb.append("\n");
        }

        // 填充区域
        if (!result.filledRegions.isEmpty()) {
            sb.append("# === 填充区域 ===\n");
            for (int i = 0; i < result.filledRegions.size(); i++) {
                Polygon poly = result.filledRegions.get(i);
                sb.append("Polygon(");
                List<Point2D> pts = poly.points;
                for (int j = 0; j < pts.size(); j++) {
                    double py = h - pts.get(j).y;
                    if (j > 0) sb.append(", ");
                    sb.append(String.format("(%.1f, %.1f)", pts.get(j).x, py));
                }
                sb.append(")\n");
            }
        }

        return sb.toString();
    }

    /**
     * 导出为 GeoGebra XML 格式（.ggb 文件）
     * 注意：完整的 .ggb 是 zip 包，这里输出的是 geogebra.xml 的内容
     */
    public static String exportXML(VectorResult result) {
        StringBuilder sb = new StringBuilder();
        double h = result.imageHeight;
        double w = result.imageWidth;

        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<geogebra format=\"5.0\" version=\"5.0\" app=\"classic\" platform=\"w\">\n");
        sb.append("  <gui>\n");
        sb.append("    <window width=\"800\" height=\"600\"/>\n");
        sb.append("    <perspectives>\n");
        sb.append("      <perspective id=\"tmp\"></perspective>\n");
        sb.append("    </perspectives>\n");
        sb.append("  </gui>\n");
        sb.append("  <euclidianView>\n");
        sb.append(String.format("    <size width=\"%d\" height=\"%d\"/>\n", (int)w, (int)h));
        sb.append(String.format("    <coordSystem xZero=\"%d\" yZero=\"%d\" scale=\"50\" yscale=\"-50\"/>\n",
                0, (int)h));
        sb.append("    <grid show=\"false\"/>\n");
        sb.append("    <axes show=\"false\"/>\n");
        sb.append("  </euclidianView>\n");
        sb.append("  <construction>\n");

        // 顶点
        int labelIdx = 0;
        for (Point2D v : result.vertices) {
            String name;
            if (labelIdx < 26) {
                name = String.valueOf((char)('A' + labelIdx));
            } else {
                name = "V" + (labelIdx + 1);
            }
            double gy = h - v.y;
            sb.append(String.format("    <element type=\"point\" label=\"%s\">\n", name));
            sb.append("      <coords x=\"" + v.x + "\" y=\"" + gy + "\" z=\"1\"/>\n");
            sb.append("    </element>\n");
            labelIdx++;
        }

        // 线段
        for (int i = 0; i < result.lines.size(); i++) {
            LineSegment line = result.lines.get(i);
            String label = "L" + (i + 1);
            double sy = h - line.start.y;
            double ey = h - line.end.y;
            sb.append(String.format("    <element type=\"segment\" label=\"%s\">\n", label));
            sb.append(String.format("      <coords x=\"%.2f\" y=\"%.2f\" z=\"1\"/>\n", line.start.x, sy));
            sb.append(String.format("      <coords x=\"%.2f\" y=\"%.2f\" z=\"1\"/>\n", line.end.x, ey));
            sb.append("    </element>\n");
        }

        // 圆
        for (int i = 0; i < result.circles.size(); i++) {
            Circle c = result.circles.get(i);
            String label = "c" + (i + 1);
            double cy = h - c.center.y;
            sb.append(String.format("    <element type=\"circle\" label=\"%s\">\n", label));
            sb.append(String.format("      <coords x=\"%.2f\" y=\"%.2f\" z=\"1\"/>\n", c.center.x, cy));
            sb.append(String.format("      <lineStyle thickness=\"2\"/>\n"));
            sb.append("    </element>\n");
        }

        sb.append("  </construction>\n");
        sb.append("</geogebra>\n");

        return sb.toString();
    }
}
