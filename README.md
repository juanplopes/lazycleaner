# LazyCleaner

This project is a JDK 8 (and later) implementation of an object cleaner. This library provides 
similar feature as JDK 9's [java.lang.ref.Cleaner](https://docs.oracle.com/javase/9/docs/api/java/lang/ref/Cleaner.html) but compatible with older Java 
versions. The main difference is that this library defaults to lazy cleaner thread creation and, more importantly, the cleaning thread is terminated if no objects are being tracked for `threadTtl` milliseconds.

## Usage

To use it, add this dependency in you `pom.xml`:

```xml
<dependency>
    <groupId>net.juanlopes.lazycleaner</groupId>
    <artifactId>lazycleaner</artifactId>
    <version>1.0.0</version>
</dependency>  
```

Then, add it to your resource class like the following:

```java
public class Resource implements Closeable {
    // creates a LazyCleaner instance with a thread TTL of 30 seconds
    private static final LazyCleaner CLEANER = new LazyCleaner(30000, "Resource Cleaner");

    private final LazyCleaner.Cleanable cleanable;

    public Resource() {
        // make sure Disposer is static, otherwise it will keep a reference to this
        this.cleanable = CLEANER.register(this, new Disposer());
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private static class Disposer implements LazyCleaner.CleaningAction {
        @Override
        public void onClean(boolean leak) throws Exception {
            System.out.println("Cleaning Resource. Did it leak? " + leak);
        }
    }
}
```

If you want to eagerly start the cleaning thread, initialize your `LazyCleaner`
like the following:

```java
new LazyCleaner(30000, "Resource Cleaner").setKeepThreadAlive(true);
```
