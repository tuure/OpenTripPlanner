package org.opentripplanner.routing.patch;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.opentripplanner.common.geometry.GeometryUtils;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

public class LineStringAdapter extends XmlAdapter<Coordinate[], LineString> {

	@Override
	public LineString unmarshal(Coordinate[] v) throws Exception {
		return GeometryUtils.getGeometryFactory().createLineString(v);
	}

	@Override
	public Coordinate[] marshal(LineString v) throws Exception {
		return v.getCoordinates();
	}

}
