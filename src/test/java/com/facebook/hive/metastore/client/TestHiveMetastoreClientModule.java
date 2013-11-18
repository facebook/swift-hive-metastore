/*
 * Copyright (C) 2013 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.hive.metastore.client;

import com.facebook.hive.metastore.client.testing.DummyHiveMetastoreServerModule;
import com.facebook.hive.metastore.client.testing.NetUtils;
import com.facebook.swift.codec.guice.ThriftCodecModule;
import com.facebook.swift.service.guice.ThriftClientModule;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;

import io.airlift.bootstrap.Bootstrap;

import org.apache.hadoop.hive.metastore.api.Table;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestHiveMetastoreClientModule
{
    @Inject
    public HiveMetastoreProvider<HiveMetastore> metastoreProvider = null;

    @Test
    public void testSimple() throws Exception
    {
        final String port = Integer.toString(NetUtils.findUnusedPort());

        final Map<String, String> properties = ImmutableMap.of("hive-metastore.port", port,
                                                               "thrift.port", port);

        final Injector inj = new Bootstrap(new DummyHiveMetastoreServerModule(),
                                           new HiveMetastoreClientModule(),
                                           new ThriftClientModule(),
                                           new ThriftCodecModule())
            .setRequiredConfigurationProperties(properties)
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        final HiveMetastore metastore = metastoreProvider.get();

        assertNotNull(metastore);

        final Table table = metastore.getTable("hello", "world");
        assertNotNull(table);
        assertEquals("hello", table.getDbName());
        assertEquals("world", table.getTableName());

    }

    @Test
    public void testLateConnectIsOk() throws Exception
    {
        final String port = Integer.toString(NetUtils.findUnusedPort());

        final Map<String, String> properties = ImmutableMap.of(
            "hive-metastore.port", port,
            "hive-metastore.max-retries", "0");

        final Injector inj = new Bootstrap(new HiveMetastoreClientModule(),
                                           new ThriftClientModule(),
                                           new ThriftCodecModule())
            .setRequiredConfigurationProperties(properties)
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        try (HiveMetastore metastore = metastoreProvider.get()) {
            assertFalse(metastore.isConnected());
        }

        final Map<String, String> serverProps = ImmutableMap.of("thrift.port", port);

        new Bootstrap(new DummyHiveMetastoreServerModule(),
                      new HiveMetastoreClientModule(),
                      new ThriftClientModule(),
                      new ThriftCodecModule())
            .setRequiredConfigurationProperties(serverProps)
            .strictConfig()
            .initialize();

        try (HiveMetastore metastore = metastoreProvider.get()) {
            assertFalse(metastore.isConnected());

            final Table table = metastore.getTable("hello", "world");
            assertNotNull(table);
            assertEquals("hello", table.getDbName());
            assertEquals("world", table.getTableName());

            assertTrue(metastore.isConnected());
        }
    }
}
