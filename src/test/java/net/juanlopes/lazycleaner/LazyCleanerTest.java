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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class LazyCleanerTest {
    @Test
    public void testPhantomCleaner() throws InterruptedException {
        ArrayList<Object> list = new ArrayList<Object>(Arrays.asList(
                new Object(), new Object(), new Object()));

        final LazyCleaner t = new LazyCleaner(10, "Cleaner");
        assertThat(t.isThreadRunning()).isFalse();
        assertThat(t.getWatchedCount()).isEqualTo(0);

        final Map<Integer, Boolean> collected = new HashMap<Integer, Boolean>();
        List<LazyCleaner.Cleanable> cleaners = new ArrayList<LazyCleaner.Cleanable>();
        for (int i = 0; i < list.size(); i++) {
            final int ii = i;
            cleaners.add(t.register(list.get(i), new LazyCleaner.CleaningAction() {
                public void onClean(boolean leak) throws Exception {
                    collected.put(ii, leak);
                }
            }));
        }
        assertThat(t.getWatchedCount()).isEqualTo(3);
        Await.until(new Await.Condition() {
            public boolean get() {
                return t.isThreadRunning();
            }
        });

        cleaners.get(1).clean();

        list.clear();
        System.gc();

        Await.until(new Await.Condition() {
            public boolean get() {
                return !t.isThreadRunning();
            }
        });

        assertThat(t.getWatchedCount()).isEqualTo(0);
        assertThat(collected).containsOnly(
                entry(0, true),
                entry(1, false),
                entry(2, true));
    }

    @Test
    public void testForceThreadAlive() throws InterruptedException {
        final LazyCleaner t = new LazyCleaner(10, "Cleaner");

        for (int i = 0; i < 5; i++) {
            assertThat(t.isThreadRunning()).isFalse();
            assertThat(t.getWatchedCount()).isEqualTo(0);

            assertThat(t.setKeepThreadAlive(true)).isSameAs(t);
            Await.until(new Await.Condition() {
                public boolean get() {
                    return t.isThreadRunning();
                }
            });
            assertThat(t.getWatchedCount()).isEqualTo(1);

            t.setKeepThreadAlive(true);
            assertThat(t.getWatchedCount()).isEqualTo(1);

            t.setKeepThreadAlive(false);
            Await.until(new Await.Condition() {
                public boolean get() {
                    return !t.isThreadRunning();
                }
            });
            assertThat(t.getWatchedCount()).isEqualTo(0);
        }
    }

    @Test
    public void testGetThread() throws InterruptedException {
        String threadName = UUID.randomUUID().toString();
        final LazyCleaner t = new LazyCleaner(10, threadName);
        Object obj = new Object();
        t.register(obj, new LazyCleaner.CleaningAction() {
            public void onClean(boolean leak) throws Exception {
                throw new RuntimeException("abc");
            }
        });
        Await.until(new Await.Condition() {
            public boolean get() {
                return t.isThreadRunning();
            }
        });
        final Thread thread = getThreadByName(threadName);
        thread.interrupt();
        Await.until(new Await.Condition() {
            public boolean get() {
                return !thread.isInterrupted();
            }
        }); //will ignore interrupt

        obj = null;
        System.gc();
        Await.until(new Await.Condition() {
            public boolean get() {
                return !t.isThreadRunning();
            }
        });
    }

    public static Thread getThreadByName(String threadName) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals(threadName)) return t;
        }
        return null;
    }
}