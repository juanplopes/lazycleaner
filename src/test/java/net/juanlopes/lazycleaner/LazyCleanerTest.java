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
        ArrayList<Object> list = new ArrayList<>(Arrays.asList(
                new Object(), new Object(), new Object()));

        LazyCleaner t = new LazyCleaner(10, "Cleaner");
        assertThat(t.isThreadRunning()).isFalse();
        assertThat(t.getWatchedCount()).isEqualTo(0);

        Map<Integer, Boolean> collected = new HashMap<>();
        List<LazyCleaner.Cleanable> cleaners = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            int ii = i;
            cleaners.add(t.register(list.get(i), leak -> collected.put(ii, leak)));
        }
        assertThat(t.getWatchedCount()).isEqualTo(3);
        Await.until(t::isThreadRunning);

        cleaners.get(1).clean();

        list.clear();
        System.gc();

        Await.until(() -> !t.isThreadRunning());

        assertThat(t.getWatchedCount()).isEqualTo(0);
        assertThat(collected).containsOnly(
                entry(0, true),
                entry(1, false),
                entry(2, true));
    }

    @Test
    public void testForceThreadAlive() throws InterruptedException {
        ArrayList<Object> list = new ArrayList<>(Arrays.asList(
                new Object(), new Object(), new Object()));

        LazyCleaner t = new LazyCleaner(10, "Cleaner");
        assertThat(t.isThreadRunning()).isFalse();
        assertThat(t.getWatchedCount()).isEqualTo(0);

        assertThat(t.setKeepThreadAlive(true)).isSameAs(t);
        Await.until(t::isThreadRunning);
        assertThat(t.getWatchedCount()).isEqualTo(1);

        t.setKeepThreadAlive(true);
        assertThat(t.getWatchedCount()).isEqualTo(1);

        t.setKeepThreadAlive(false);
        Await.until(() -> !t.isThreadRunning());
        assertThat(t.getWatchedCount()).isEqualTo(0);
    }

    @Test
    public void testGetThread() throws InterruptedException {
        String threadName = UUID.randomUUID().toString();
        LazyCleaner t = new LazyCleaner(10, threadName);
        Object obj = new Object();
        t.register(obj, leak -> {
            throw new RuntimeException("abc");
        });
        Await.until(t::isThreadRunning);
        Thread thread = getThreadByName(threadName);
        thread.interrupt();
        Await.until(() -> !thread.isInterrupted()); //will ignore interrupt

        obj = null;
        System.gc();
        Await.until(() -> !t.isThreadRunning());
    }

    public static Thread getThreadByName(String threadName) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals(threadName)) return t;
        }
        return null;
    }
}