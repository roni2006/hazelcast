/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapLoader;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.hazelcast.spi.properties.GroupProperty.MAP_LOAD_ALL_PUBLISHES_ADD_EVENT;
import static com.hazelcast.util.StringUtil.LOCALE_INTERNAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class InterceptorTest extends HazelcastTestSupport {

    @Test
    public void testMapInterceptor() {
        Config config = getConfig();
        TestHazelcastInstanceFactory nodeFactory = createHazelcastInstanceFactory(2);
        HazelcastInstance hz = nodeFactory.newHazelcastInstance(config);
        nodeFactory.newHazelcastInstance(config);

        IMap<Object, Object> map = hz.getMap("testMapInterceptor");
        String id = map.addInterceptor(new SimpleInterceptor());

        map.put(1, "New York");
        map.put(2, "Istanbul");
        map.put(3, "Tokyo");
        map.put(4, "London");
        map.put(5, "Paris");
        map.put(6, "Cairo");
        map.put(7, "Hong Kong");

        try {
            map.remove(1);
        } catch (Exception ignore) {
        }
        try {
            map.remove(2);
        } catch (Exception ignore) {
        }

        assertEquals(6, map.size());
        assertNull(map.get(1));
        assertEquals(map.get(2), "ISTANBUL:");
        assertEquals(map.get(3), "TOKYO:");
        assertEquals(map.get(4), "LONDON:");
        assertEquals(map.get(5), "PARIS:");
        assertEquals(map.get(6), "CAIRO:");
        assertEquals(map.get(7), "HONG KONG:");

        map.removeInterceptor(id);
        map.put(8, "Moscow");

        assertNull(map.get(1));
        assertEquals(map.get(2), "ISTANBUL");
        assertEquals(map.get(3), "TOKYO");
        assertEquals(map.get(4), "LONDON");
        assertEquals(map.get(5), "PARIS");
        assertEquals(map.get(6), "CAIRO");
        assertEquals(map.get(7), "HONG KONG");
        assertEquals(map.get(8), "Moscow");
    }

    @Test
    public void testMapInterceptorOnNewMember() {
        Config config = getConfig();
        TestHazelcastInstanceFactory nodeFactory = createHazelcastInstanceFactory(2);
        HazelcastInstance hz1 = nodeFactory.newHazelcastInstance(config);
        IMap<Integer, Object> map1 = hz1.getMap("map");

        for (int i = 0; i < 100; i++) {
            map1.put(i, i);
        }

        map1.addInterceptor(new NegativeInterceptor());
        for (int i = 0; i < 100; i++) {
            assertEquals("Expected negative value on map1.get(" + i + ")", i * -1, map1.get(i));
        }

        HazelcastInstance hz2 = nodeFactory.newHazelcastInstance(config);
        IMap<Integer, Object> map2 = hz2.getMap("map");
        for (int i = 0; i < 100; i++) {
            assertEquals("Expected negative value on map1.get(" + i + ")", i * -1, map1.get(i));
            assertEquals("Expected negative value on map1.get(" + i + ")", i * -1, map2.get(i));
        }
    }

    @Test
    public void testGetAll_withGetInterceptor() {
        HazelcastInstance instance = createHazelcastInstance(getConfig());
        IMap<Integer, String> map = instance.getMap(randomString());
        map.addInterceptor(new SimpleInterceptor());

        Set<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < 100; i++) {
            map.put(i, String.valueOf(i));
            set.add(i);
        }

        Map<Integer, String> allValues = map.getAll(set);
        for (int i = 0; i < 100; i++) {
            assertEquals("Expected intercepted value on map.getAll()", String.valueOf(i) + ":", allValues.get(i));
        }
    }

    @Test
    public void testPutEvent_withInterceptor() {
        HazelcastInstance instance = createHazelcastInstance(getConfig());
        IMap<Integer, String> map = instance.getMap(randomString());
        map.addInterceptor(new SimpleInterceptor());
        final EntryAddedLatch listener = new EntryAddedLatch();
        map.addEntryListener(listener, true);

        String value = "foo";
        map.put(1, value);

        final String expectedValue = value.toUpperCase(LOCALE_INTERNAL);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() {
                assertEquals(expectedValue, listener.getAddedValue());
            }
        }, 15);
    }

    @Test
    public void testPutEvent_withInterceptor_withEntryProcessor_withMultipleKeys() {
        HazelcastInstance instance = createHazelcastInstance(getConfig());
        IMap<Integer, String> map = instance.getMap(randomString());
        map.addInterceptor(new SimpleInterceptor());
        final EntryAddedLatch listener = new EntryAddedLatch();
        map.addEntryListener(listener, true);

        String value = "foo";
        Set<Integer> keys = new HashSet<Integer>();
        keys.add(1);
        map.executeOnKeys(keys, new EntryPutProcessor("foo"));

        final String expectedValue = value.toUpperCase(LOCALE_INTERNAL);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() {
                assertEquals(expectedValue, listener.getAddedValue());
            }
        }, 15);
    }

    @Test
    public void testPutEvent_withInterceptor_withEntryProcessor() {
        HazelcastInstance instance = createHazelcastInstance(getConfig());
        IMap<Integer, String> map = instance.getMap(randomString());
        map.addInterceptor(new SimpleInterceptor());
        final EntryAddedLatch listener = new EntryAddedLatch();
        map.addEntryListener(listener, true);

        String value = "foo";
        map.executeOnKey(1, new EntryPutProcessor("foo"));

        final String expectedValue = value.toUpperCase(LOCALE_INTERNAL);
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() {
                assertEquals(expectedValue, listener.getAddedValue());
            }
        }, 15);
    }

    @Test
    public void testPutEvent_withInterceptor_withLoadAll() {
        String name = randomString();
        Config config = getConfig();
        config.setProperty(MAP_LOAD_ALL_PUBLISHES_ADD_EVENT.getName(), "true");
        MapStoreConfig mapStoreConfig = new MapStoreConfig()
                .setEnabled(true)
                .setImplementation(new DummyLoader());
        config.getMapConfig(name).setMapStoreConfig(mapStoreConfig);

        HazelcastInstance instance = createHazelcastInstance(config);
        IMap<Integer, String> map = instance.getMap(name);
        map.addInterceptor(new SimpleInterceptor());
        final EntryAddedLatch listener = new EntryAddedLatch();
        map.addEntryListener(listener, true);

        Set<Integer> keys = new HashSet<Integer>();
        keys.add(1);
        map.loadAll(keys, false);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() {
                assertEquals("FOO-1", listener.getAddedValue());
            }
        }, 15);
    }

    static class DummyLoader implements MapLoader<Integer, String> {

        @Override
        public String load(Integer key) {
            return "foo-" + key;
        }

        @Override
        public Map<Integer, String> loadAll(Collection<Integer> keys) {
            Map<Integer, String> map = new HashMap<Integer, String>(keys.size());
            for (Integer key : keys) {
                map.put(key, load(key));
            }
            return map;
        }

        @Override
        public Iterable<Integer> loadAllKeys() {
            return null;
        }
    }

    static class EntryPutProcessor extends AbstractEntryProcessor<Integer, String> {

        String value;

        EntryPutProcessor(String value) {
            this.value = value;
        }

        @Override
        public Object process(Map.Entry<Integer, String> entry) {
            return entry.setValue(value);
        }
    }

    static class EntryAddedLatch implements EntryAddedListener<Integer, String> {

        AtomicReference<String> value = new AtomicReference<String>();

        @Override
        public void entryAdded(EntryEvent<Integer, String> event) {
            value.compareAndSet(null, event.getValue());
        }

        String getAddedValue() {
            return value.get();
        }
    }

    public static class SimpleInterceptor implements MapInterceptor, Serializable {

        @Override
        public Object interceptGet(Object value) {
            if (value == null) {
                return null;
            }
            return value + ":";
        }

        @Override
        public void afterGet(Object value) {
        }

        @Override
        public Object interceptPut(Object oldValue, Object newValue) {
            return newValue.toString().toUpperCase(LOCALE_INTERNAL);
        }

        @Override
        public void afterPut(Object value) {
        }

        @Override
        public Object interceptRemove(Object removedValue) {
            if (removedValue.equals("ISTANBUL")) {
                throw new RuntimeException("you can not remove this");
            }
            return removedValue;
        }

        @Override
        public void afterRemove(Object value) {
        }
    }

    static class NegativeInterceptor implements MapInterceptor, Serializable {

        @Override
        public Object interceptGet(Object value) {
            return ((Integer) value) * -1;
        }

        @Override
        public void afterGet(Object value) {
        }

        @Override
        public Object interceptPut(Object oldValue, Object newValue) {
            return newValue;
        }

        @Override
        public void afterPut(Object value) {
        }

        @Override
        public Object interceptRemove(Object removedValue) {
            return removedValue;
        }

        @Override
        public void afterRemove(Object value) {
        }
    }
}
