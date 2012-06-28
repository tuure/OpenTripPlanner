/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.opentripplanner.routing.core.GraphBuilderAnnotation;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.GraphBuilderAnnotation.Variety;
import org.opentripplanner.routing.edgetype.FreeEdge;
import org.opentripplanner.routing.edgetype.PlainStreetEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.edgetype.TurnEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.StreetVertex;
import org.opentripplanner.routing.vertextype.TurnVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreetUtils {

    private static Logger _log = LoggerFactory.getLogger(StreetUtils.class);

    /**
     * Make an ordinary graph into an edge-based graph.
     * 
     * @param endpoints
     * @param coordinateToStreetNames
     */
    public static void makeEdgeBased(Graph graph, Collection<IntersectionVertex> endpoints,
            Map<Edge, TurnRestriction> restrictions) {

        Map<PlainStreetEdge, TurnVertex> turnVertices = new HashMap<PlainStreetEdge, TurnVertex>();
        /* generate turns */

        _log.debug("converting to edge-based graph");
        for (IntersectionVertex v : endpoints) {
            for (Edge e_in : v.getIncoming()) {
                for (Edge e_out : v.getOutgoing()) {
                    if (e_in instanceof PlainStreetEdge && e_out instanceof PlainStreetEdge) {
                        PlainStreetEdge pse_in = (PlainStreetEdge) e_in;
                        PlainStreetEdge pse_out = (PlainStreetEdge) e_out;
                        // do not make turn edges for U turns unless they are dead ends
                        if (pse_in.getFromVertex() == pse_out.getToVertex() && 
                            pse_in.getId().equals(pse_out.getId()) &&
                            v.getDegreeOut() > 1) {
                                continue;
                        }
                        TurnVertex tv_in = getTurnVertexForEdge(graph, turnVertices, pse_in);
                        TurnVertex tv_out = getTurnVertexForEdge(graph, turnVertices, pse_out);
                        TurnEdge turn = tv_in.makeTurnEdge(tv_out);
                        if (restrictions != null) {
                            TurnRestriction restriction = restrictions.get(pse_in);
                            if (restriction != null) {
                                if (restriction.type == TurnRestrictionType.NO_TURN
                                        && restriction.to == e_out) {
                                    turn.setRestrictedModes(restriction.modes);
                                } else if (restriction.type == TurnRestrictionType.ONLY_TURN
                                        && restriction.to != e_out) {
                                    turn.setRestrictedModes(restriction.modes);
                                }
                            }
                        }
                    } else { // turn involving a plainstreetedge and a freeedge
                        Vertex fromv = null;
                        Vertex tov = null;
                        if (e_in instanceof PlainStreetEdge) {
                            fromv = getTurnVertexForEdge(graph, turnVertices,
                                    (PlainStreetEdge) e_in);
                        } else if (e_in instanceof FreeEdge) {
                            fromv = e_in.getFromVertex(); // fromv for incoming
                        }
                        if (e_out instanceof PlainStreetEdge) {
                            tov = getTurnVertexForEdge(graph, turnVertices, (PlainStreetEdge) e_out);
                        } else if (e_out instanceof FreeEdge) {
                            tov = e_out.getToVertex(); // tov for outgoing
                        }
                        if (fromv instanceof TurnVertex) {
                            ((TurnVertex) fromv).makeTurnEdge((StreetVertex) tov);
                        } else {
                            new FreeEdge(fromv, tov);
                        }
                    }
                }
            }
        }

        /* remove standard graph */
        for (IntersectionVertex iv : endpoints) {
            graph.removeVertex(iv);
        }
    }

    private static TurnVertex getTurnVertexForEdge(Graph graph,
            Map<PlainStreetEdge, TurnVertex> turnVertices, PlainStreetEdge pse) {

        TurnVertex tv = turnVertices.get(pse);
        if (tv != null) {
            return tv;
        }

        tv = pse.createTurnVertex(graph);
        turnVertices.put(pse, tv);
        return tv;
    }

    public static void pruneFloatingIslands(Graph graph) {
        _log.debug("pruning");
        Map<Vertex, HashSet<Vertex>> subgraphs = new HashMap<Vertex, HashSet<Vertex>>();
        Map<Vertex, ArrayList<Vertex>> neighborsForVertex = new HashMap<Vertex, ArrayList<Vertex>>();

        RoutingRequest options = new RoutingRequest(new TraverseModeSet(TraverseMode.WALK, TraverseMode.TRANSIT));

        for (Vertex gv : graph.getVertices()) {
            if (!(gv instanceof StreetVertex)) {
                continue;
            }
            State s0 = new State(gv, options);
            for (Edge e : gv.getOutgoing()) {
                Vertex in = gv;
                if (!(e instanceof StreetEdge)) {
                    continue;
                }
                State s1 = e.traverse(s0);
                if (s1 == null) {
                    continue;
                }
                Vertex out = s1.getVertex();

                ArrayList<Vertex> vertexList = neighborsForVertex.get(in);
                if (vertexList == null) {
                    vertexList = new ArrayList<Vertex>();
                    neighborsForVertex.put(in, vertexList);
                }
                vertexList.add(out);

                vertexList = neighborsForVertex.get(out);
                if (vertexList == null) {
                    vertexList = new ArrayList<Vertex>();
                    neighborsForVertex.put(out, vertexList);
                }
                vertexList.add(in);
            }
        }

        ArrayList<HashSet<Vertex>> islands = new ArrayList<HashSet<Vertex>>();
        /* associate each node with a subgraph */
        for (Vertex gv : graph.getVertices()) {
            if (!(gv instanceof StreetVertex)) {
                continue;
            }
            Vertex vertex = gv;
            if (subgraphs.containsKey(vertex)) {
                continue;
            }
            if (!neighborsForVertex.containsKey(vertex)) {
                continue;
            }
            HashSet<Vertex> subgraph = computeConnectedSubgraph(neighborsForVertex, vertex);
            for (Vertex subnode : subgraph) {
                subgraphs.put(subnode, subgraph);
            }
            islands.add(subgraph);
        }
    	
    	/* remove all tiny subgraphs */
        for (HashSet<Vertex> island : islands) {
            if (island.size() < 20) {
                _log.warn(GraphBuilderAnnotation.register(graph, Variety.GRAPH_CONNECTIVITY, 
                        island.iterator().next(), island));
                depedestrianizeOrRemove(graph, island);
            }
        }
        if (graph.removeEdgelessVertices() > 0) {
            _log.warn("Removed edgeless vertices after pruning islands.");
        }
    }

    private static void depedestrianizeOrRemove(Graph graph, Collection<Vertex> vertices) {
        for (Vertex v : vertices) {
            Collection<Edge> outgoing = new ArrayList<Edge>(v.getOutgoing());
            for (Edge e : outgoing) {
                if (e instanceof PlainStreetEdge) {
                    PlainStreetEdge pse = (PlainStreetEdge) e;
                    StreetTraversalPermission permission = pse.getPermission();
                    permission = permission.remove(StreetTraversalPermission.PEDESTRIAN);
                    permission = permission.remove(StreetTraversalPermission.BICYCLE);
                    if (permission == StreetTraversalPermission.NONE) {
                        pse.detach();
                    } else {
                        pse.setPermission(permission);
                    }
                }
                
                if (e instanceof TurnEdge) {
                    TurnEdge turn = (TurnEdge) e;
                    StreetTraversalPermission permission = turn.getPermission();
                    permission = permission.remove(StreetTraversalPermission.PEDESTRIAN);
                    permission = permission.remove(StreetTraversalPermission.BICYCLE);
                    if (permission == StreetTraversalPermission.NONE) {
                        turn.detach();
                    } else {
                        ((TurnVertex) turn.getFromVertex()).setPermission(permission);
                    }
                }
            }
        }
        for (Vertex v : vertices) {
            if (v.getDegreeOut() + v.getDegreeIn() == 0) {
                graph.remove(v);
            }
        }
    }

    private static HashSet<Vertex> computeConnectedSubgraph(
            Map<Vertex, ArrayList<Vertex>> neighborsForVertex, Vertex startVertex) {
        HashSet<Vertex> subgraph = new HashSet<Vertex>();
        Queue<Vertex> q = new LinkedList<Vertex>();
        q.add(startVertex);
        while (!q.isEmpty()) {
            Vertex vertex = q.poll();
            for (Vertex neighbor : neighborsForVertex.get(vertex)) {
                if (!subgraph.contains(neighbor)) {
                    subgraph.add(neighbor);
                    q.add(neighbor);
                }
            }
        }
        return subgraph;
    }
}
