package com.boundlessgeo.gsr.core.map;

import com.boundlessgeo.gsr.core.GSRModel;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.wms.WMSInfo;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.visitor.CalcResult;
import org.geotools.feature.visitor.MaxVisitor;
import org.geotools.feature.visitor.MinVisitor;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Detailed model of a MapService
 */
public class MapServiceRoot implements GSRModel{

    String mapName;
    List<Layer> layers = new ArrayList<Layer>();
    DateRange timeInfo;
    boolean singleFusedMapCache;
    String capabilities;

    public MapServiceRoot(WMSInfo service, List<LayerInfo> layers) throws IOException {
        this.mapName = service.getTitle() != null ? service.getTitle() : service.getName();

        int count = 0;
        for (LayerInfo l : layers) {
            this.layers.add(new Layer(count, l.getName()));
            count++;
        }
        Date[] dateRange = getCumulativeDateRange(layers);
        if (dateRange != null) {
            this.timeInfo = new DateRange(dateRange);
        }
        this.singleFusedMapCache = false;
        this.capabilities = "Query";
    }

    private static class Layer {
        int id;
        String name;

        Layer(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class DateRange {
        List<String> timeExtent;

        DateRange(Date[] dateRange) {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeExtent.add(format.format(dateRange[0]));
            timeExtent.add(format.format(dateRange[1]));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Date[] getCumulativeDateRange(List<LayerInfo> layers) throws IOException {
        Comparable overallMin = null;
        Comparable overallMax = null;
        for (LayerInfo l : layers) {
            FeatureTypeInfo ftInfo = (FeatureTypeInfo) l.getResource();
            DimensionInfo dimensionInfo = ftInfo.getMetadata().get(ResourceInfo.TIME, DimensionInfo.class);
            if (dimensionInfo != null && dimensionInfo.isEnabled()) {
                String timeProperty = dimensionInfo.getAttribute();
                FeatureSource<? extends FeatureType, ? extends Feature> source = ftInfo.getFeatureSource(null, null);
                FeatureCollection<? extends FeatureType, ? extends Feature> features = source.getFeatures();
                MaxVisitor max = new MaxVisitor(timeProperty, (SimpleFeatureType) features.getSchema());
                MinVisitor min = new MinVisitor(timeProperty, (SimpleFeatureType) features.getSchema());
                features.accepts(min, null);
                features.accepts(max, null);
                if (min.getResult() != CalcResult.NULL_RESULT) {
                    if (overallMin == null) {
                        overallMin = min.getMin();
                    } else {
                        overallMin = min.getMin().compareTo(overallMin) < 0 ? min.getMin() : overallMin;
                    }
                }

                if (max.getResult() != CalcResult.NULL_RESULT) {
                    if (overallMax == null) {
                        overallMax = max.getMax();
                    } else {
                        overallMax = max.getMax().compareTo(overallMax) > 0 ? max.getMax() : overallMax;
                    }
                }
            }
        }
        if (overallMin == null || overallMax == null) {
            return null;
        } else {
            return new Date[] { (Date) overallMin, (Date) overallMax };
        }
    }
}