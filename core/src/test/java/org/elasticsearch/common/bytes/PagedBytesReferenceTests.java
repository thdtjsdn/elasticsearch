/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.bytes;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.BytesRefIterator;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.ReleasableBytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.ByteArray;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

public class PagedBytesReferenceTests extends AbstractBytesReferenceTestCase {

    protected BytesReference newBytesReference(int length) throws IOException {
        // we know bytes stream output always creates a paged bytes reference, we use it to create randomized content
        ReleasableBytesStreamOutput out = new ReleasableBytesStreamOutput(length, bigarrays);
        for (int i = 0; i < length; i++) {
            out.writeByte((byte) random().nextInt(1 << 8));
        }
        assertThat(out.size(), Matchers.equalTo(length));
        BytesReference ref = out.bytes();
        assertThat(ref.length(), Matchers.equalTo(length));
        assertThat(ref, Matchers.instanceOf(PagedBytesReference.class));
        return ref;
    }

    public void testToBytesArrayMaterializedPages() throws IOException {
        // we need a length != (n * pagesize) to avoid page sharing at boundaries
        int length = 0;
        while ((length % PAGE_SIZE) == 0) {
            length = randomIntBetween(PAGE_SIZE, PAGE_SIZE * randomIntBetween(2, 5));
        }
        BytesReference pbr = newBytesReference(length);
        BytesArray ba = pbr.toBytesArray();
        BytesArray ba2 = pbr.toBytesArray();
        assertNotNull(ba);
        assertNotNull(ba2);
        assertEquals(pbr.length(), ba.length());
        assertEquals(ba.length(), ba2.length());
        // ensure no single-page optimization
        assertNotSame(ba.array(), ba2.array());
    }

    public void testArray() throws IOException {
        int[] sizes = {0, randomInt(PAGE_SIZE), PAGE_SIZE, randomIntBetween(2, PAGE_SIZE * randomIntBetween(2, 5))};

        for (int i = 0; i < sizes.length; i++) {
            BytesReference pbr = newBytesReference(sizes[i]);
            // verify that array() is cheap for small payloads
            if (sizes[i] <= PAGE_SIZE) {
                byte[] array = pbr.array();
                assertNotNull(array);
                assertEquals(sizes[i], array.length);
                assertSame(array, pbr.array());
            } else {
                try {
                    pbr.array();
                    fail("expected IllegalStateException");
                } catch (IllegalStateException isx) {
                    // expected
                }
            }
        }
    }

    public void testToBytes() throws IOException {
        int[] sizes = {0, randomInt(PAGE_SIZE), PAGE_SIZE, randomIntBetween(2, PAGE_SIZE * randomIntBetween(2, 5))};

        for (int i = 0; i < sizes.length; i++) {
            BytesReference pbr = newBytesReference(sizes[i]);
            byte[] bytes = pbr.toBytes();
            assertEquals(sizes[i], bytes.length);
            // verify that toBytes() is cheap for small payloads
            if (sizes[i] <= PAGE_SIZE) {
                assertSame(bytes, pbr.toBytes());
            } else {
                assertNotSame(bytes, pbr.toBytes());
            }
        }
    }

    public void testHasArray() throws IOException {
        int length = randomIntBetween(10, PAGE_SIZE * randomIntBetween(1, 3));
        BytesReference pbr = newBytesReference(length);
        // must return true for <= pagesize
        assertEquals(length <= PAGE_SIZE, pbr.hasArray());
    }

}
