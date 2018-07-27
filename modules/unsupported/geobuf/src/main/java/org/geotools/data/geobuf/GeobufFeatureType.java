/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2015, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.data.geobuf;

import jo.ban.proto.GeoBufProtos;
import java.io.*;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

/**
 * GeobufFeatureType encodes and decodes SimpleFeatureTypes
 *
 * @author Jared Erickson
 */
public class GeobufFeatureType {

    private int precision;

    private int dimension;

    public GeobufFeatureType() {
        this(6, 2);
    }

    public GeobufFeatureType(int precision, int dimension) {
        this.precision = precision;
        this.dimension = dimension;
    }

    public void encode(SimpleFeatureType featureType, OutputStream out) throws IOException {
        GeoBufProtos.Data.Builder dataBuilder = GeoBufProtos.Data.newBuilder();
        encode(featureType, dataBuilder);
        GeoBufProtos.Data data = dataBuilder.build();
        data.writeTo(out);
    }

    public SimpleFeatureType decode(String name, InputStream inputStream) throws IOException {
      GeoBufProtos.Data data = GeoBufProtos.Data.parseFrom(inputStream);
        return getFeatureType(name, data);
    }

    protected void encode(SimpleFeatureType featureType, GeoBufProtos.Data.Builder dataBuilder) {
        // Keys
        for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
            if (!(descriptor instanceof GeometryDescriptor)) {
                dataBuilder.addKeys(descriptor.getLocalName());
            }
        }
        // Max Coordinate Dimension
        dataBuilder.setDimensions(dimension);
        // Number of digits after decimal point
        dataBuilder.setPrecision(precision);
        // Set Feature Collection
        dataBuilder.setFeatureCollection(GeoBufProtos.Data.FeatureCollection.newBuilder().build());
    }

    public SimpleFeatureType getFeatureType(String name, GeoBufProtos.Data data) throws IOException {
        SimpleFeatureTypeBuilder featureTypeBuilder = new SimpleFeatureTypeBuilder();
        featureTypeBuilder.setName(name);
        if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.GEOMETRY) {
            featureTypeBuilder.setDefaultGeometry("geom");
            featureTypeBuilder.add("geom", getGeometryType(data.getGeometry()));
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE) {
            featureTypeBuilder.setDefaultGeometry("geom");
            featureTypeBuilder.add("geom", getGeometryType(data.getFeature().getGeometry()));
            int keyCount = data.getKeysCount();
            for (int i = 0; i < keyCount; i++) {
                String key = data.getKeys(i);
                Class type = getType(data.getFeature().getValues(i).getValueTypeCase());
                featureTypeBuilder.add(key, type);
            }
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE_COLLECTION) {
            featureTypeBuilder.setDefaultGeometry("geom");
            if (data.getFeatureCollection().getFeaturesCount() == 0) {
                featureTypeBuilder.add("geom", Geometry.class);
            } else {
                featureTypeBuilder.add(
                        "geom",
                        getGeometryType(data.getFeatureCollection().getFeatures(0).getGeometry()));
            }
            int keyCount = data.getKeysCount();
            for (int i = 0; i < keyCount; i++) {
                String key = data.getKeys(i);
                Class type = String.class;
                if (data.getFeatureCollection().getFeaturesCount() > 0
                        && i < data.getFeatureCollection().getFeatures(0).getValuesCount()) {
                    type =
                            getType(
                                    data.getFeatureCollection()
                                            .getFeatures(0)
                                            .getValues(i)
                                            .getValueTypeCase());
                }
                featureTypeBuilder.add(key, type);
            }
        } else {
            throw new IOException("Unknown Data Type!");
        }
        return featureTypeBuilder.buildFeatureType();
    }

    protected Class<?> getType(GeoBufProtos.Data.Value.ValueTypeCase vtc) {
        if (vtc == GeoBufProtos.Data.Value.ValueTypeCase.STRING_VALUE) {
            return String.class;
        } else if (vtc == GeoBufProtos.Data.Value.ValueTypeCase.POS_INT_VALUE
                || vtc == GeoBufProtos.Data.Value.ValueTypeCase.NEG_INT_VALUE) {
            return Integer.class;
        } else if (vtc == GeoBufProtos.Data.Value.ValueTypeCase.BOOL_VALUE) {
            return Boolean.class;
        } else if (vtc == GeoBufProtos.Data.Value.ValueTypeCase.DOUBLE_VALUE) {
            return Boolean.class;
        } else if (vtc == GeoBufProtos.Data.Value.ValueTypeCase.JSON_VALUE) {
            return String.class;
        } else {
            return Object.class;
        }
    }

    protected Class<? extends Geometry> getGeometryType(GeoBufProtos.Data.Geometry g) {
        if (g.getType() == GeoBufProtos.Data.Geometry.Type.POINT) {
            return Point.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.LINESTRING) {
            return LineString.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.POLYGON) {
            return Polygon.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.MULTIPOINT) {
            return MultiPoint.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.MULTILINESTRING) {
            return MultiLineString.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.MULTIPOLYGON) {
            return MultiPolygon.class;
        } else if (g.getType() == GeoBufProtos.Data.Geometry.Type.GEOMETRYCOLLECTION) {
            return GeometryCollection.class;
        } else {
            return Geometry.class;
        }
    }
}
