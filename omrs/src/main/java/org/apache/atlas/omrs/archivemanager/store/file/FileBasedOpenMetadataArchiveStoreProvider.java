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
package org.apache.atlas.omrs.archivemanager.store.file;

import org.apache.atlas.omrs.archivemanager.store.OpenMetadataArchiveStoreProviderBase;

/**
 * FileBasedOpenMetadataArchiveStoreProvider is the OCF connector provider for the file based server configuration store.
 */
public class FileBasedOpenMetadataArchiveStoreProvider extends OpenMetadataArchiveStoreProviderBase
{
    /**
     * Constructor used to initialize the ConnectorProviderBase with the Java class name of the specific
     * configuration store implementation.
     */
    public FileBasedOpenMetadataArchiveStoreProvider()
    {
        Class    connectorClass = FileBasedOpenMetadataArchiveStoreConnector.class;

        super.setConnectorClassName(connectorClass.getName());
    }
}
