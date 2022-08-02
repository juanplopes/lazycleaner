package net.juanlopes.lazycleaner;

import java.util.function.BooleanSupplier;

public class Await {
    public static void until(BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean()) {
            Thread.sleep(1);
        }
    }
}
