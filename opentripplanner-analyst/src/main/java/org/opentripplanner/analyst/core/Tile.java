package org.opentripplanner.analyst.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.opentripplanner.analyst.request.RenderRequest;
import org.opentripplanner.analyst.request.TileRequest;
import org.opentripplanner.analyst.parameter.Style;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Tile {

    /* STATIC */
    private static final Logger LOG = LoggerFactory.getLogger(Tile.class);
    public static final Map<Style, IndexColorModel> modelsByStyle; 
    static {
        modelsByStyle = new EnumMap<Style, IndexColorModel>(Style.class);
        modelsByStyle.put(Style.COLOR30, buildDefaultColorMap());
        modelsByStyle.put(Style.DIFFERENCE, buildDifferenceColorMap());
        modelsByStyle.put(Style.TRANSPARENT, buildTransparentColorMap());
        modelsByStyle.put(Style.MASK, buildMaskColorMap(90));
        modelsByStyle.put(Style.BOARDINGS, buildBoardingColorMap());
    }
    
    /* INSTANCE */
    final GridGeometry2D gg;
    final int width, height;
    
    Tile(TileRequest req) {
        GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, req.width, req.height);
        this.gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(req.bbox));
        // TODO: check that gg intersects graph area 
        LOG.debug("preparing tile for {}", gg.getEnvelope2D());
        // Envelope2D worldEnv = gg.getEnvelope2D();
        this.width = gridEnv.width;
        this.height = gridEnv.height;
    }
    
    private static IndexColorModel buildDefaultColorMap() {
    	Color[] palette = new Color[256];
    	final int ALPHA = 0x60FFFFFF; // ARGB
    	for (int i = 0; i < 28; i++) {
    		// Note: HSB = Hue / Saturation / Brightness
        	palette[i + 00] =  new Color(ALPHA & Color.HSBtoRGB(0.333f, i * 0.037f, 0.8f), true); // Green
        	palette[i + 30] =  new Color(ALPHA & Color.HSBtoRGB(0.666f, i * 0.037f, 0.8f), true); // Blue
        	palette[i + 60] =  new Color(ALPHA & Color.HSBtoRGB(0.144f, i * 0.037f, 0.8f), true); // Yellow
        	palette[i + 90] =  new Color(ALPHA & Color.HSBtoRGB(0.000f, i * 0.037f, 0.8f), true); // Red
        	palette[i + 120] = new Color(ALPHA & Color.HSBtoRGB(0.000f, 0.000f, (29 - i) * 0.0172f), true); // Black
        }
    	for (int i = 28; i < 30; i++) {
        	palette[i + 00] =  new Color(ALPHA & Color.HSBtoRGB(0.333f, (30 - i) * 0.333f, 0.8f), true); // Green
        	palette[i + 30] =  new Color(ALPHA & Color.HSBtoRGB(0.666f, (30 - i) * 0.333f, 0.8f), true); // Blue
        	palette[i + 60] =  new Color(ALPHA & Color.HSBtoRGB(0.144f, (30 - i) * 0.333f, 0.8f), true); // Yellow
        	palette[i + 90] =  new Color(ALPHA & Color.HSBtoRGB(0.000f, (30 - i) * 0.333f, 0.8f), true); // Red
        	palette[i + 120] = new Color(ALPHA & Color.HSBtoRGB(0.000f, 0.000f, (29 - i) * 0.0172f), true); // Black
    	}
        for (int i = 150; i < palette.length; i++) {
        	palette[i] = new Color(0x00000000, true);
        }
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        for (int i = 0; i < palette.length; i++) {
        	r[i] = (byte)palette[i].getRed();
        	g[i] = (byte)palette[i].getGreen();
        	b[i] = (byte)palette[i].getBlue();
        	a[i] = (byte)palette[i].getAlpha();
        }
        return new IndexColorModel(8, 256, r, g, b, a);
    }

    private static IndexColorModel buildOldDefaultColorMap() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        Arrays.fill(a, (byte)0);
        for (int i=0; i<30; i++) {
            g[i + 00]  =  // <  30 green 
            a[i + 00]  =  
            b[i + 30]  =  // >= 30 blue
            a[i + 30]  =  
            g[i + 60]  =  // >= 60 yellow 
            r[i + 60]  =
            a[i + 60]  =  
            r[i + 90]  =  // >= 90 red
            a[i + 90]  =  
            b[i + 120] =  // >=120 pink fading to transparent 
            a[i + 120] =  
            r[i + 120] = (byte) (255 - (42 - i) * 6);
        }
        return new IndexColorModel(8, 256, r, g, b, a);
    }
    
    private static IndexColorModel buildDifferenceColorMap() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        Arrays.fill(a, (byte) 64);
        for (int i=0; i<118; i++) {
            g[117 - i] = (byte) (i * 2);
            r[137 + i] = (byte) (i * 2);
            a[117 - i] = (byte) (64+(i * 2));
            a[137 + i] = (byte) (64+(i * 2));
            //b[128 + i] = g[128 - i] = (byte)120; 
        }
//        for (int i=0; i<10; i++) {
//            byte v = (byte) (255 - i * 25);
//            g[127 - i] = v;
//            g[128 + i] = v;
//        }
//        a[255] = 64;
        return new IndexColorModel(8, 256, r, g, b, a);
    }

    private static IndexColorModel buildTransparentColorMap() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        for (int i=0; i<60; i++) {
            int alpha = 240 - i * 4;
            a[i] = (byte) alpha;
        }
        for (int i=60; i<255; i++) {
            a[i] = 0;
        }
        a[255] = (byte) 240;
        return new IndexColorModel(8, 256, r, g, b, a);
    }

    private static IndexColorModel buildMaskColorMap(int max) {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        for (int i=0; i<max; i++) {
            int alpha = (i * 210 / max);
            a[i] = (byte) alpha;
        }
        for (int i=max; i<=255; i++) {
            a[i] = (byte)210;
//            r[i] = (byte)255;
//            g[i] = (byte)128;
//            b[i] = (byte)128;
        }
        //a[255] = (byte) 240;
        return new IndexColorModel(8, 256, r, g, b, a);
    }

    private static IndexColorModel buildBoardingColorMap() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        byte[] a = new byte[256];
        Arrays.fill(a, (byte) 80);
        g[0] = (byte) 255;
        b[1] = (byte) 255;
        r[2] = (byte) 255;
        g[2] = (byte) 255;
        r[3] = (byte) 255;
        a[255] = 0;
        return new IndexColorModel(8, 256, r, g, b, a);
    }

    protected BufferedImage getEmptyImage(Style style) {
        IndexColorModel colorModel = modelsByStyle.get(style);
        if (colorModel == null)
            return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        else
            return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
    }
    
    public BufferedImage generateImage(ShortestPathTree spt, RenderRequest renderRequest) {
        long t0 = System.currentTimeMillis();
        BufferedImage image = getEmptyImage(renderRequest.style);
        byte[] imagePixelData = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
        int i = 0;
        final byte TRANSPARENT = (byte) 255;
        for (Sample s : getSamples()) {
            byte pixel;
            if (s != null) {
                if (renderRequest.style == Style.BOARDINGS) {
                    pixel = s.evalBoardings(spt);
                } else {
                    pixel = s.evalByte(spt); // renderRequest.style
                }
            } else {
                pixel = TRANSPARENT;
            }
            imagePixelData[i] = pixel;
            i++;
        }
        long t1 = System.currentTimeMillis();
        LOG.debug("filled in tile image from SPT in {}msec", t1 - t0);
        return image;
    }

    public BufferedImage linearCombination(
            double k1, ShortestPathTree spt1, 
            double k2, ShortestPathTree spt2, 
            double intercept, RenderRequest renderRequest) {
        long t0 = System.currentTimeMillis();
        BufferedImage image = getEmptyImage(renderRequest.style);
        byte[] imagePixelData = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
        int i = 0;
        final byte TRANSPARENT = (byte) 255;
        for (Sample s : getSamples()) {
            byte pixel;
            if (s != null) {
                double t = (k1 * s.eval(spt1) + k2 * s.eval(spt2)) / 60 + intercept; 
                if (t < 0 || t > 255)
                    t = TRANSPARENT;
                pixel = (byte) t;
            } else {
                pixel = TRANSPARENT;
            }
            imagePixelData[i] = pixel;
            i++;
        }
        long t1 = System.currentTimeMillis();
        LOG.debug("filled in tile image from SPT in {}msec", t1 - t0);
        return image;
    }

    public GridCoverage2D getGridCoverage2D(BufferedImage image) {
        GridCoverage2D gridCoverage = new GridCoverageFactory()
            .create("isochrone", image, gg.getEnvelope2D());
        return gridCoverage;
    }

    public abstract Sample[] getSamples();

    public static BufferedImage getLegend(Style style, int width, int height) {
        final int NBANDS = 150;
        final int LABEL_SPACING = 30; 
        IndexColorModel model = modelsByStyle.get(style);
        if (width < 140 || width > 2000)
            width = 140;
        if (height < 25 || height > 2000)
            height = 25;
        if (model == null)
            return null;
        WritableRaster raster = model.createCompatibleWritableRaster(width, height);
        byte[] pixels = ((DataBufferByte) raster.getDataBuffer()).getData();
        for (int row = 0; row < height; row++)
            for (int col = 0; col < width; col++)
                pixels[row * width + col] = (byte) (col * NBANDS / width);
        BufferedImage legend = model.convertToIntDiscrete(raster, false);
        Graphics2D gr = legend.createGraphics();
        gr.setColor(new Color(0));
        gr.drawString("travel time (minutes)", 0, 10);
        float scale = width / (float) NBANDS;
        for (int i = 0; i < NBANDS; i += LABEL_SPACING)
            gr.drawString(Integer.toString(i), i * scale, height);
        return legend;
    }

}
