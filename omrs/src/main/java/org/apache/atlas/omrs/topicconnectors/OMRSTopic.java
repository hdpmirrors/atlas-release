/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.omrs.topicconnectors;


import org.apache.atlas.omrs.eventmanagement.events.v1.OMRSEventV1;

/**
 * OMRSTopic defines the interface to the messaging Topic for OMRS Events.  It implemented by the OMRSTopicConnector.
 */
public interface OMRSTopic
{
    /**
     * Register a listener object.  This object will be supplied with all of the events
     * received on the topic.
     *
     * @param newListener - object implementing the OMRSTopicListener interface
     */
    void registerListener(OMRSTopicListener  newListener);


    /**
     * Sends the supplied event to the topic.
     *
     * @param event - OMRSEvent object containing the event properties.
     */
    void sendEvent(OMRSEventV1 event);
}
