package com.geovecsvg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 矢量化结果
 */
public class VectorResult {
    public List<LineSegment> lines = new ArrayList<>();
    public List<Circle> circles = new ArrayList<>();
    public List<Point2D> vertices = new ArrayList<>();
    public List<Polygon> filledRegions = new ArrayList<>();
    public List<TextRegion> textRegions = new ArrayList<>();
    public int imageWidth;
    public int imageHeight;

    public static class TextRegion {
        public int x, y, w, h;
        public String text;

        public TextRegion(int x, int y, int w, int h, String text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }
    }
}
