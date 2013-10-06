package org.threadly.concurrent.limiter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.BlockingTestRunnable;
import org.threadly.concurrent.PriorityScheduledExecutor;
import org.threadly.concurrent.limiter.ExecutorLimiter;
import org.threadly.test.concurrent.TestRunnable;
import org.threadly.test.concurrent.TestUtils;
import org.threadly.test.concurrent.TestablePriorityScheduler;

@SuppressWarnings("javadoc")
public class ExecutorLimiterTest {
  private static final int PARALLEL_COUNT = 2;
  private static final int THREAD_COUNT = PARALLEL_COUNT * 2;
  private static final int THREAD_SLEEP_TIME = 20;
  
  private volatile boolean parallelFailure;
  private AtomicInteger running;
  private PriorityScheduledExecutor executor;
  private ExecutorLimiter limiter;
  
  @Before
  public void setup() {
    parallelFailure = false;
    running = new AtomicInteger(0);
    executor = new PriorityScheduledExecutor(PARALLEL_COUNT, THREAD_COUNT, 1000);
    limiter = new ExecutorLimiter(executor, PARALLEL_COUNT);
  }
  
  @After
  public void tearDown() {
    executor.shutdown();
    executor = null;
    limiter = null;
    running = null;
  }
  
  @Test
  public void constructorFail() {
    try {
      new ExecutorLimiter(null, 100);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new ExecutorLimiter(Executors.newSingleThreadExecutor(), 0);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
  
  @Test
  public void getMaxConcurrencyTest() {
    assertEquals(limiter.getMaxConcurrency(), PARALLEL_COUNT);
  }
  
  @Test
  public void constructorEmptySubPoolNameTest() {
    PriorityScheduledExecutor executor = new PriorityScheduledExecutor(1, 1, 100);
    try {
      ExecutorLimiter limiter = new ExecutorLimiter(executor, 1, " ");
      
      assertNull(limiter.subPoolName);
    } finally {
      executor.shutdown();
    }
  }
  
  @Test
  public void consumeAvailableTest() {
    List<TestRunnable> runnables = new ArrayList<TestRunnable>(PARALLEL_COUNT);
    for (int i = 0; i < PARALLEL_COUNT; i++) {
      TestRunnable tr = new TestRunnable();
      runnables.add(tr);
      limiter.waitingTasks.add(limiter.new LimiterRunnableWrapper(tr));
    }
    
    limiter.consumeAvailable();
    
    // should be fully consumed
    assertEquals(limiter.waitingTasks.size(), 0);
    
    Iterator<TestRunnable> it = runnables.iterator();
    while (it.hasNext()) {
      it.next().blockTillFinished();  // throws exception if it does not finish
    }
  }
  
  @Test
  public void executeTest() {
    int runnableCount = 10;
    
    List<TestRunnable> runnables = new ArrayList<TestRunnable>(runnableCount);
    for (int i = 0; i < runnableCount; i++) {
      TestRunnable tr = new TestRunnable() {
        @Override
        public void handleRunStart() {
          running.incrementAndGet();
          if (running.get() > PARALLEL_COUNT) {
            parallelFailure = true;
          }
          TestUtils.sleep(THREAD_SLEEP_TIME);
        }
        
        @Override
        public void handleRunFinish() {
          running.decrementAndGet();
        }
      };
      limiter.execute(tr);
      runnables.add(tr);
    }
    
    assertFalse(parallelFailure);
    
    // verify execution
    Iterator<TestRunnable> it = runnables.iterator();
    while (it.hasNext()) {
      TestRunnable tr = it.next();
      tr.blockTillFinished();
      
      assertEquals(tr.getRunCount(), 1);
    }
  }
  
  @Test
  public void executeTestableSchedulerTest() {
    int runnableCount = 10;
    
    TestablePriorityScheduler testScheduler = new TestablePriorityScheduler(executor);
    ExecutorLimiter limiter = new ExecutorLimiter(testScheduler, PARALLEL_COUNT);
    
    List<TestRunnable> runnables = new ArrayList<TestRunnable>(runnableCount);
    for (int i = 0; i < runnableCount; i++) {
      TestRunnable tr = new TestRunnable();
      limiter.execute(tr);
      runnables.add(tr);
    }
    
    testScheduler.tick();
    
    // verify execution
    Iterator<TestRunnable> it = runnables.iterator();
    while (it.hasNext()) {
      TestRunnable tr = it.next();
      tr.blockTillFinished();
      
      assertEquals(tr.getRunCount(), 1);
    }
  }
  
  @Test (expected = IllegalArgumentException.class)
  public void executeFail() {
    limiter.execute(null);
    fail("Execption should have thrown");
  }
  
  @Test
  public void removeBlockedRunnableTest() {
    PriorityScheduledExecutor scheduler = new PriorityScheduledExecutor(1, 1, 1000);
    BlockingTestRunnable blockingRunnable = new BlockingTestRunnable();
    try {
      ExecutorLimiter limiter = new ExecutorLimiter(scheduler, 2);
      scheduler.execute(blockingRunnable);
      scheduler.execute(blockingRunnable);
      blockingRunnable.blockTillStarted();
      
      TestRunnable task = new TestRunnable();
      limiter.execute(task);
      
      assertFalse(scheduler.remove(new TestRunnable()));
      assertTrue(scheduler.remove(task));
    } finally {
      blockingRunnable.unblock();
      scheduler.shutdownNow();
    }
  }
}
