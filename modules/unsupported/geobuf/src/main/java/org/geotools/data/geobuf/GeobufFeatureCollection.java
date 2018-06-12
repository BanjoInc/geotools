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

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import jo.ban.proto.GeoBufProtos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

/**
 * GeobufFeatureCollection encodes and decodes SimpleFeatureCollections.
 *
 * @author Jared Erickson
 */
public class GeobufFeatureCollection {

    private GeobufFeature geobufFeature;

    public GeobufFeatureCollection() {
        this(new GeobufFeature());
    }

    public GeobufFeatureCollection(GeobufFeature geobufFeature) {
        this.geobufFeature = geobufFeature;
    }

    public SimpleFeatureCollection decode(InputStream in) throws IOException {
        GeoBufProtos.Data data = GeoBufProtos.Data.parseFrom(in);
        return decode(data);
    }

    public void encode(SimpleFeatureCollection featureCollection, OutputStream out)
            throws IOException {
        encode(featureCollection).writeTo(out);
    }

    public SimpleFeatureCollection decode(GeoBufProtos.Data data) throws IOException {
        GeobufFeatureType geobufFeatureType =
                new GeobufFeatureType(
                        geobufFeature.getGeobufGeometry().getPrecision(),
                        geobufFeature.getGeobufGeometry().getDimension());
        SimpleFeatureType featureType = geobufFeatureType.getFeatureType("features", data);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        MemoryDataStore memoryDataStore = new MemoryDataStore(featureType);
        for (int i = 0; i < data.getFeatureCollection().getFeaturesCount(); i++) {
            memoryDataStore.addFeature(geobufFeature.decode(data, i, featureBuilder));
        }
        return memoryDataStore.getFeatureSource("features").getFeatures();
    }

    protected GeoBufProtos.Data.FeatureCollection encodeAsFeatureCollection(
            SimpleFeatureCollection featureCollection) {
      GeoBufProtos.Data.FeatureCollection.Builder featureCollectionBuilder =
          GeoBufProtos.Data.FeatureCollection.newBuilder();
        SimpleFeatureIterator it = featureCollection.features();
        try {
            while (it.hasNext()) {
                featureCollectionBuilder.addFeatures(geobufFeature.encode(it.next()));
            }
        } finally {
            it.close();
        }
        return featureCollectionBuilder.build();
    }

    public GeoBufProtos.Data encode(SimpleFeatureCollection featureCollection) {
      GeoBufProtos.Data.Builder dataBuilder = GeoBufProtos.Data.newBuilder();
        for (AttributeDescriptor descriptor :
                featureCollection.getSchema().getAttributeDescriptors()) {
            if (!(descriptor instanceof GeometryDescriptor)) {
                dataBuilder.addKeys(descriptor.getLocalName());
            }
        }
        dataBuilder.setDimensions(geobufFeature.getGeobufGeometry().getDimension());
        dataBuilder.setPrecision(geobufFeature.getGeobufGeometry().getPrecision());
        dataBuilder.setFeatureCollection(encodeAsFeatureCollection(featureCollection));
        GeoBufProtos.Data data = dataBuilder.build();
        return data;
    }

    protected int countFeatures(InputStream in) throws IOException {
      GeoBufProtos.Data data = GeoBufProtos.Data.parseFrom(in);
        if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.GEOMETRY) {
            return 1;
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE) {
            return 1;
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE_COLLECTION) {
            return data.getFeatureCollection().getFeaturesCount();
        } else {
            return -1;
        }
    }

    protected ReferencedEnvelope getBounds(InputStream in) throws IOException {
      GeoBufProtos.Data data = GeoBufProtos.Data.parseFrom(in);
        if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.GEOMETRY) {
            Geometry g = geobufFeature.getGeobufGeometry().decode(data.getGeometry());
            Envelope env = g.getEnvelopeInternal();
            return new ReferencedEnvelope(
                    env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY(), null);
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE) {
            Geometry g = (Geometry) geobufFeature.decode(data).getDefaultGeometry();
            Envelope env = g.getEnvelopeInternal();
            return new ReferencedEnvelope(
                    env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY(), null);
        } else if (data.getDataTypeCase() == GeoBufProtos.Data.DataTypeCase.FEATURE_COLLECTION) {
            SimpleFeatureCollection fc = decode(data);
            return fc.getBounds();
        } else {
            return null;
        }
    }
}
