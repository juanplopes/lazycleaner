package net.juanlopes.lazycleaner;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LazyCleaner {
    private static final Logger LOGGER = Logger.getLogger(LazyCleaner.class.getName());

    public interface Cleanable {
        void clean();
    }

    public interface CleaningAction {
        void onClean(boolean leak) throws Exception;
    }

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final long threadTtl;
    private final ThreadFactory threadFactory;
    private boolean threadRunning;
    private int watchedCount;
    private Node first;
    private Cleanable keepAliveCleanable;

    public LazyCleaner(long threadTtl, String threadName) {
        this(threadTtl, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public LazyCleaner(long threadTtl, ThreadFactory threadFactory) {
        this.threadTtl = threadTtl;
        this.threadFactory = threadFactory;
    }

    public synchronized LazyCleaner setKeepThreadAlive(boolean alive) {
        if (alive == (keepAliveCleanable != null)) return this;

        if (alive) {
            keepAliveCleanable = register(this, null);
        } else {
            keepAliveCleanable.clean();
            keepAliveCleanable = null;
        }
        return this;
    }

    public Cleanable register(Object obj, CleaningAction action) {
        return add(new Node(obj, action));
    }

    public int getWatchedCount() {
        return watchedCount;
    }

    public boolean isThreadRunning() {
        return threadRunning;
    }

    private synchronized boolean checkEmpty() {
        if (first == null) {
            threadRunning = false;
            return true;
        }
        return false;
    }

    private synchronized Node add(Node node) {
        if (first != null) {
            node.next = first;
            first.prev = node;
        }
        first = node;
        watchedCount++;

        if (!threadRunning) {
            startThread();
            threadRunning = true;
        }
        return node;
    }

    private void startThread() {
        Thread thread = threadFactory.newThread(() -> {
            while (true) {
                try {
                    Node ref = (Node) queue.remove(threadTtl);
                    if (ref != null) {
                        ref.onClean(true);
                    } else if (checkEmpty()) {
                        break;
                    }
                } catch (Throwable e) {
                    // Ignore exceptions from the cleanup action (including interruption of cleanup thread)
                    LOGGER.log(Level.WARNING, "Unexpected exception in cleaner thread main loop", e);
                }
            }
        });
        thread.start();
    }

    private synchronized boolean remove(Node node) {
        // If already removed, do nothing
        if (node.next == node)
            return false;

        // Update list
        if (first == node)
            first = node.next;
        if (node.next != null)
            node.next.prev = node.prev;
        if (node.prev != null)
            node.prev.next = node.next;

        // Indicate removal by pointing the cleaner to itself
        node.next = node;
        node.prev = node;

        watchedCount--;
        return true;

    }

    private class Node extends PhantomReference<Object> implements Cleanable, CleaningAction {
        private final CleaningAction action;
        private Node prev;
        private Node next;

        public Node(Object referent, CleaningAction action) {
            super(referent, queue);
            this.action = action;
            Objects.requireNonNull(referent); // poor man`s reachabilityFence
        }

        @Override
        public void clean() {
            onClean(false);
        }

        public void onClean(boolean leak) {
            if (!remove(this))
                return;
            try {
                if (action != null) {
                    action.onClean(leak);
                }
            } catch (Throwable e) {
                // Should not happen if cleaners are well-behaved
                LOGGER.log(Level.WARNING, "Unexpected exception in cleaner thread", e);
            }
        }
    }
}
