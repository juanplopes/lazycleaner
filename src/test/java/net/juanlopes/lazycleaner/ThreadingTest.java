/*
 * Copyright 2022 Juan Lopes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.juanlopes.lazycleaner;

import org.junit.Test;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadingTest {

    @Test
    public void testThreads() throws InterruptedException {
        final AtomicLong created = new AtomicLong();
        final AtomicLong disposed = new AtomicLong();
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 8; i++) {
            threads.add(new Thread(new Runnable() {
                public void run() {
                    for (int j = 0; j < 100000; j++) {
                        Resource res = new Resource(created, disposed);
                        if (j % 2 == 0) {
                            res.close();
                        }
                    }
                }
            }));
        }
        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        System.gc();
        Await.until(new Await.Condition() {
            public boolean get() {
                return disposed.get() == created.get();
            }
        });
    }

    private static class Resource implements Closeable {
        private static final LazyCleaner CLEANER = new LazyCleaner(10, "Resource Cleaner");
        private final LazyCleaner.Cleanable cleanable;

        public Resource(AtomicLong created, AtomicLong disposed) {
            created.incrementAndGet();
            cleanable = CLEANER.register(this, new MyCleaningAction(disposed));
        }

        public void close() {
            cleanable.clean();
        }

        private static class MyCleaningAction implements LazyCleaner.CleaningAction {
            private final AtomicLong disposed;

            public MyCleaningAction(AtomicLong disposed) {
                this.disposed = disposed;
            }

            public void onClean(boolean leak) throws Exception {
                disposed.incrementAndGet();
            }
        }
    }
}
