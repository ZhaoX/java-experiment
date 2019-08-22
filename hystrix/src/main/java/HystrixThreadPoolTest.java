import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by zhaoxin on 2019/8/22.
 */
public class HystrixThreadPoolTest {

  public static void main(String[] args) throws InterruptedException {
    final int coreSize = 2, maximumSize = 5, maxQueueSize = 10;
    final String commandName = "TestThreadPoolCommand";

    final HystrixCommand.Setter commandConfig = HystrixCommand.Setter
        .withGroupKey(HystrixCommandGroupKey.Factory.asKey(commandName))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandName))
        .andCommandPropertiesDefaults(
            HystrixCommandProperties.Setter()
                .withExecutionTimeoutEnabled(false))
        .andThreadPoolPropertiesDefaults(
            HystrixThreadPoolProperties.Setter()
                .withCoreSize(coreSize)
                .withMaximumSize(maximumSize)
                .withAllowMaximumSizeToDivergeFromCoreSize(true)
                .withMaxQueueSize(maxQueueSize)
                .withQueueSizeRejectionThreshold(maxQueueSize));

    // Run command once, so we can get metrics.
    HystrixCommand<Void> command = new HystrixCommand<Void>(commandConfig) {
      @Override protected Void run() throws Exception {
        return null;
      }
    };
    command.execute();
    Thread.sleep(100);

    final CountDownLatch stopLatch = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<Thread>();

    for (int i = 0; i < coreSize + maximumSize + maxQueueSize; i++) {
      final int fi = i + 1;

      Thread thread = new Thread(new Runnable() {
        public void run() {
          try {
            HystrixCommand<Void> command = new HystrixCommand<Void>(commandConfig) {
              @Override protected Void run() throws Exception {
                stopLatch.await();
                return null;
              }
            };
            command.execute();
          } catch (HystrixRuntimeException e) {
            System.out.println("Job:" + fi + " got rejected.");
          }
        }
      });
      threads.add(thread);
      thread.start();
      Thread.sleep(200);

      if(fi == coreSize || fi == coreSize + maximumSize || fi == coreSize + maximumSize + maxQueueSize ) {
        System.out.println("Started Jobs: " + fi);
        printThreadPoolStatus();
        System.out.println();
      }
    }

    stopLatch.countDown();

    for (Thread thread : threads) {
      thread.join();
    }

    System.out.println("DONE!");
    printThreadPoolStatus();
  }

  static void printThreadPoolStatus() {
    for (HystrixThreadPoolMetrics threadPoolMetrics : HystrixThreadPoolMetrics.getInstances()) {
      String name = threadPoolMetrics.getThreadPoolKey().name();
      Number poolSize = threadPoolMetrics.getCurrentPoolSize();
      Number queueSize = threadPoolMetrics.getCurrentQueueSize();
      System.out.println("ThreadPoolKey: " + name + ", PoolSize: " + poolSize + ", QueueSize: " + queueSize);
    }

  }


}
