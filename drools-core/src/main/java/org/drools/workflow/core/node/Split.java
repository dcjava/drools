package org.drools.workflow.core.node;

/*
 * Copyright 2005 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.drools.workflow.core.Connection;
import org.drools.workflow.core.Constraint;
import org.drools.workflow.core.Node;
import org.drools.workflow.core.impl.NodeImpl;

/**
 * Default implementation of a split node.
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class Split extends NodeImpl {

    public static final int TYPE_UNDEFINED = 0;
    /**
     * All outgoing connections of a split of this type are triggered
     * when its incoming connection has been triggered.  A split of this
     * type should have no constraints linked to any of its outgoing
     * connections.
     */
    public static final int TYPE_AND       = 1;
    /**
     * Exactly one outgoing connection of a split of this type is triggered
     * when its incoming connection has been triggered.  Which connection
     * is based on the constraints associated with each of the connections:
     * the connection with the highest priority whose constraint is satisfied
     * is triggered.  
     */
    public static final int TYPE_XOR       = 2;
    /**
     * One or multiple outgoing connections of a split of this type are
     * triggered when its incoming connection has been triggered.  Which
     * connections is based on the constraints associated with each of the
     * connections: all connections whose constraint is satisfied are
     * triggered.  
     */
    public static final int TYPE_OR        = 3;

    private static final long serialVersionUID = 400L;

    private int type;
    private Map<Connection, Constraint> constraints = new HashMap<Connection, Constraint>();

    public Split() {
        this.type = TYPE_UNDEFINED;
    }

    public Split(final int type) {
        this.type = type;
    }

    public void setType(final int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    public Constraint getConstraint(final Connection connection) {
        if ( connection == null ) {
            throw new IllegalArgumentException( "connection is null" );
        }

        // dirty hack because keys were entered wrong
        // probably caused by xstreams
        // TODO xstream 1.3.0 should fix this by default; in 1.2.2 it's fixable: http://jira.codehaus.org/browse/XSTR-363
        final HashMap<Connection, Constraint> newMap = new HashMap<Connection, Constraint>();
        for (final Iterator<Map.Entry<Connection, Constraint>> it = this.constraints.entrySet().iterator(); it.hasNext(); ) {
            final Entry<Connection, Constraint> entry = it.next();
            newMap.put(entry.getKey(), entry.getValue());
        }
        this.constraints = newMap;

        if ( this.type == TYPE_OR || this.type == TYPE_XOR ) {
            return this.constraints.get(connection);
        }
        throw new UnsupportedOperationException( "Constraints are " + "only supported with XOR or OR split types, not with: " + getType() );
    }

    public void setConstraint(final Connection connection,
                              final Constraint constraint) {
        if ( this.type == TYPE_OR || this.type == TYPE_XOR ) {
            if ( connection == null ) {
                throw new IllegalArgumentException( "connection is null" );
            }
            if (getOutgoingConnections(Node.CONNECTION_DEFAULT_TYPE) != null
                    && !getOutgoingConnections(Node.CONNECTION_DEFAULT_TYPE).contains(connection)) {
                throw new IllegalArgumentException("connection is unknown:" + connection);
            }
            this.constraints.put( connection,
                                  constraint );
        } else {
            throw new UnsupportedOperationException( "Constraints are " + "only supported with XOR or OR split types, not with type:" + getType() );
        }
    }

    public Map<Connection, Constraint> getConstraints() {
        if ( this.type == TYPE_OR || this.type == TYPE_XOR ) {
            return Collections.unmodifiableMap( this.constraints );
        }
        throw new UnsupportedOperationException( "Constraints are " + "only supported with XOR or OR split types, not with: " + getType() );
    }

    public Connection getFrom() {
        final List<Connection> list =
            getIncomingConnections(Node.CONNECTION_DEFAULT_TYPE);
        if (list.size() > 0) {
            return (Connection) list.get(0);
        }
        return null;
    }
    
    public List<Connection> getDefaultOutgoingConnections() {
        return getOutgoingConnections(Node.CONNECTION_DEFAULT_TYPE);
    }

    public void validateAddIncomingConnection(final String type,
            final Connection connection) {
        super.validateAddIncomingConnection(type, connection);
        if (!Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                "This type of node only accepts default incoming connection type!");
        }
        if (!getIncomingConnections(Node.CONNECTION_DEFAULT_TYPE).isEmpty()) {
            throw new IllegalArgumentException(
                "This type of node cannot have more than one incoming connection!");
        }
    }

    public void validateAddOutgoingConnection(final String type, final Connection connection) {
        super.validateAddOutgoingConnection(type, connection);
        if (!Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                "This type of node only accepts default outgoing connection type!");
        }
    }

    public void removeOutgoingConnection(final String type, final Connection connection) {
        super.removeOutgoingConnection(type, connection);
        this.constraints.remove(connection);
    }

}