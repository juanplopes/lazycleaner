package net.juanlopes.lazycleaner;

public class Await {
    public static void until(Condition condition) throws InterruptedException {
        while (!condition.get()) {
            Thread.sleep(1);
        }
    }

    public interface Condition {
        boolean get();
    }
}
