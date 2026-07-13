# GeoVecSVG - 几何题图精确矢量化工具 (JavaFX版)

将几何题图片精确转换为 SVG 矢量图，支持导出为 GeoGebra 格式。

**基于 Java + JavaFX 开发，界面现代美观，跨平台一键运行。**

## ✨ 特性

- **精确矢量化**：线条为数学绝对直线，圆由圆心+半径精确定义
- **几何约束优化**：自动修正平行、垂直、共点关系
- **填充区域检测**：自动提取 HSV 有色填充多边形
- **文字区域分离**：避免文字笔画被误识别为线条
- **多格式导出**：SVG / GeoGebra 命令脚本 / GeoGebra XML
- **现代 GUI**：JavaFX + 自定义 CSS，暗色/亮色主题，参数实时调节

## 🏗️ 核心算法

基于 **基元检测 + 几何约束优化** 的四步处理流程：

1. **图像预处理** - Otsu 二值化、高斯去噪、HSV 颜色分割、文字区域分离
2. **几何基元检测** - HoughLinesP 直线检测、HoughCircles + RANSAC 圆拟合、交点计算
3. **几何约束优化** - 共点/平行/垂直约束、Gauss-Seidel 迭代优化
4. **精确导出** - SVG 数学直线 / GeoGebra 命令脚本

## 🚀 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+

### 编译运行

```bash
# 编译
mvn package

# 运行 GUI
java -jar target/GeoVecSVG-1.0.0.jar
```

### 或者使用 Maven 直接运行

```bash
mvn javafx:run
```

## 📦 项目结构

```
src/main/java/com/geovecsvg/
  ├── model/              # 数据模型（点、线段、圆、多边形等）
  ├── preprocess/         # 图像预处理模块
  ├── detection/          # 几何基元检测模块
  ├── optimization/       # 几何约束优化模块
  ├── export/             # 导出器（SVG / GeoGebra）
  ├── gui/                # JavaFX GUI 界面
  └── ProcessingPipeline.java  # 处理管道
src/main/resources/
  └── com/geovecsvg/gui/css/  # GUI 样式表
```

## 🔧 技术栈

| 模块 | 技术 |
|------|------|
| GUI | JavaFX 21 |
| 图像处理 | OpenCV 4.9 (JavaCPP) |
| 构建 | Maven |
| 语言 | Java 17 |

## 📄 License

MIT
