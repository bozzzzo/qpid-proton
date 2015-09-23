/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.proton.engine.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.EventType;
import org.apache.qpid.proton.engine.Handler;
import org.apache.qpid.proton.engine.HandlerException;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.reactor.Reactor;
import org.apache.qpid.proton.reactor.Selectable;
import org.apache.qpid.proton.reactor.Task;
import org.apache.qpid.proton.reactor.impl.ReactorImpl;

/**
 * EventImpl
 *
 */

class EventImpl implements Event
{
    EventType type;
    Object context;
    EventImpl next;
    RecordImpl attachments = new RecordImpl();

    EventImpl()
    {
        this.type = null;
    }

    void init(EventType type, Object context)
    {
        this.type = type;
        this.context = context;
        this.attachments.clear();
    }

    void clear()
    {
        type = null;
        context = null;
        attachments.clear();
        nesting = 0;
    }

    @Override
    public EventType getEventType()
    {
        return type;
    }

    @Override
    public Type getType() {
        if (type instanceof Type) {
            return (Type)type;
        }
        return Type.NON_CORE_EVENT;
    }

    @Override
    public Object getContext()
    {
        return context;
    }

    @Override
    public Handler getRootHandler() {
        return ReactorImpl.ROOT.get(this);
    }

    private Handler delegated = null;

    private int nesting = 0;
    @Override
    public void dispatch(Handler handler) throws HandlerException
    {
        Handler old_delegated = delegated;
        try {
            dispatchEnter();
            delegated = handler;
            try {
                int unhandled = handler.getUnhandled();
                handler.handle(this);
                if (unhandled == handler.getUnhandled()) {
                    System.err.println(indent() + "Dispatched event type " + type + " on context " + context + " to handler " + handler.getClass().getName() + ":" + System.identityHashCode(handler));
                }
            } catch(HandlerException handlerException) {
                throw handlerException;
            } catch(RuntimeException runtimeException) {
                System.err.println(indent() + "Exception dispatching event type " + type + " to handler " + handler.getClass().getName() + ":" + System.identityHashCode(handler));
                throw new HandlerException(type, handler, runtimeException);
            }
            delegate();
        } finally {
            delegated = old_delegated;
            dispatchLeave();
        }
    }

    @Override
    public void delegate() throws HandlerException
    {
        if (delegated == null) {
            return; // short circuit
        }
        Iterator<Handler> children = delegated.children();
        Handler handler = delegated;
        delegated = null;
        while(children.hasNext()) {
            Handler child = children.next();
            System.err.println(indent() + "---- delegate event type " + type + " on context " + context + " from "  + handler.getClass().getName() + ":" + System.identityHashCode(handler) + " to child handler " + child.getClass().getName() + ":" + System.identityHashCode(child));
            dispatch(child);
        }
    }

    private String indent() { return String.format("%1$-" + nesting + "s", " "); }

    @Override
    public void redispatch(EventType as_type, Handler handler) throws HandlerException 
    {
        if (!as_type.isValid()) {
            throw new IllegalArgumentException("Can only redispatch valid event types");
        }
        EventType old = type;
        try {
            dispatchEnter();
            type = as_type;
            Class<? extends Handler> hc = handler.getClass();
            String name = hc.getName();
            System.err.println(indent() + "Redispatching event type " + type + " (was " + old + ") " + " to handler " + name + ":" + System.identityHashCode(handler));
            dispatch(handler);  
        }
        finally {
            dispatchLeave();
            type = old;
        }
    }

    @Override
    public Connection getConnection()
    {
        if (context instanceof Connection) {
            return (Connection) context;
        } else if (context instanceof Transport) {
            Transport transport = getTransport();
            if (transport == null) {
                return null;
            }
            return ((TransportImpl) transport).getConnectionImpl();
        } else {
            Session ssn = getSession();
            if (ssn == null) {
                return null;
            }
            return ssn.getConnection();
        }
    }

    @Override
    public Session getSession()
    {
        if (context instanceof Session) {
            return (Session) context;
        } else {
            Link link = getLink();
            if (link == null) {
                return null;
            }
            return link.getSession();
        }
    }

    @Override
    public Link getLink()
    {
        if (context instanceof Link) {
            return (Link) context;
        } else {
            Delivery dlv = getDelivery();
            if (dlv == null) {
                return null;
            }
            return dlv.getLink();
        }
    }

    @Override
    public Sender getSender()
    {
        if (context instanceof Sender) {
            return (Sender) context;
        } else {
            Link link = getLink();
            if (link instanceof Sender) {
                return (Sender) link;
            }
            return null;
        }
    }

    @Override
    public Receiver getReceiver()
    {
        if (context instanceof Receiver) {
            return (Receiver) context;
        } else {
            Link link = getLink();
            if (link instanceof Receiver) {
                return (Receiver) link;
            }
            return null;
        }
    }

    @Override
    public Delivery getDelivery()
    {
        if (context instanceof Delivery) {
            return (Delivery) context;
        } else {
            return null;
        }
    }

    @Override
    public Transport getTransport()
    {
        if (context instanceof Transport) {
            return (Transport) context;
        } else if (context instanceof Connection) {
        	return ((Connection)context).getTransport();	
        } else {
            return null;
        }
    }

    @Override
    public Selectable getSelectable() {
        if (context instanceof Selectable) {
            return (Selectable) context;
        } else {
            return null;
        }
    }

    @Override
    public Reactor getReactor() {
        if (context instanceof Reactor) {
            return (Reactor) context;
        } else if (context instanceof Task) {
            return ((Task)context).getReactor();
        } else if (context instanceof Transport) {
            return ((TransportImpl)context).getReactor();
        } else if (context instanceof Delivery) {
            return ((Delivery)context).getLink().getSession().getConnection().getReactor();
        } else if (context instanceof Link) {
            return ((Link)context).getSession().getConnection().getReactor();
        } else if (context instanceof Session) {
            return ((Session)context).getConnection().getReactor();
        } else if (context instanceof Connection) {
            return ((Connection)context).getReactor();
        } else if (context instanceof Selectable) {
            return ((Selectable)context).getReactor();
        }
        return null;
    }

    @Override
    public Task getTask() {
        if (context instanceof Task) {
            return (Task) context;
        } else {
            return null;
        }
    }

    @Override
    public Record attachments() {
        return attachments;
    }

    @Override
    public Event copy()
    {
       EventImpl newEvent = new EventImpl();
       newEvent.init(type, context);
       newEvent.attachments.copy(attachments);
       return newEvent;
    }

    @Override
    public String toString()
    {
        return "EventImpl{" + "type=" + type + ", context=" + context + '}';
    }


    @Override
    public int getNesting() {
        return nesting;
    }
    
    @Override
    public void dispatchEnter() {
        nesting++;
    }

    @Override
    public void dispatchLeave() {
        nesting--;
    }

}
