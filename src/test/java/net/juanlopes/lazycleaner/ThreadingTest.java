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
