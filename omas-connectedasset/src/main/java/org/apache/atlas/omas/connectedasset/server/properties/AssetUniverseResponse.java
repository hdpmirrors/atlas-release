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
package org.apache.atlas.omas.connectedasset.server.properties;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.atlas.omas.connectedasset.properties.AssetUniverse;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.PUBLIC_ONLY;


/**
 * AssetUniverseResponse is the response structure used on the Connected Asset OMAS REST API calls that return an
 * AssetUniverse object as a response.
 */
@JsonAutoDetect(getterVisibility=PUBLIC_ONLY, setterVisibility=PUBLIC_ONLY, fieldVisibility=NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown=true)
public class AssetUniverseResponse extends ConnectedAssetOMASAPIResponse
{
    private AssetUniverse assetUniverse = null;


    /**
     * Default constructor
     */
    public AssetUniverseResponse()
    {
    }


    /**
     * Return the AssetUniverse object.
     *
     * @return all details known about the asset
     */
    public AssetUniverse getAssetUniverse()
    {
        return assetUniverse;
    }


    /**
     * Set up the AssetUniverse object.
     *
     * @param assetUniverse - all details known about the asset
     */
    public void setAssetUniverse(AssetUniverse assetUniverse)
    {
        this.assetUniverse = assetUniverse;
    }


    @Override
    public String toString()
    {
        return "AssetUniverseResponse{" +
                "assetUniverse=" + assetUniverse +
                ", relatedHTTPCode=" + relatedHTTPCode +
                ", exceptionClassName='" + exceptionClassName + '\'' +
                ", exceptionErrorMessage='" + exceptionErrorMessage + '\'' +
                ", exceptionSystemAction='" + exceptionSystemAction + '\'' +
                ", exceptionUserAction='" + exceptionUserAction + '\'' +
                '}';
    }
}
