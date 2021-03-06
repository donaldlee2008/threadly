package org.threadly.concurrent;

import static org.junit.Assert.*;
import static org.threadly.TestConstants.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.threadly.BlockingTestRunnable;
import org.threadly.concurrent.PriorityScheduler.OneTimeTaskWrapper;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.limiter.PrioritySchedulerLimiter;
import org.threadly.test.concurrent.AsyncVerifier;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.test.concurrent.TestRunnable;
import org.threadly.test.concurrent.TestUtils;

@SuppressWarnings("javadoc")
public class PrioritySchedulerTest extends SchedulerServiceInterfaceTest {
  @Override
  protected SchedulerServiceFactory getSchedulerServiceFactory() {
    return getPrioritySchedulerFactory();
  }
  
  protected PrioritySchedulerFactory getPrioritySchedulerFactory() {
    return new PrioritySchedulerTestFactory();
  }
  
  private static void blockTillWorkerAvailable(final PriorityScheduler scheduler) {
    new TestCondition() {
      @Override
      public boolean get() {
        synchronized (scheduler.workerPool.workersLock) {
          return ! scheduler.workerPool.availableWorkers.isEmpty();
        }
      }
    }.blockTillTrue();
  }
  
  @Test
  public void getDefaultPriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    TaskPriority priority = TaskPriority.High;
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1, priority, 1000);
      
      assertEquals(priority, scheduler.getDefaultPriority());
      
      priority = TaskPriority.Low;
      scheduler = factory.makePriorityScheduler(1, priority, 1000);
      assertEquals(priority, scheduler.getDefaultPriority());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings({ "unused", "deprecation" })
  @Test
  public void constructorFail() {
    try {
      new PriorityScheduler(0, 1, 1, TaskPriority.High, 1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new PriorityScheduler(2, 1, 1, TaskPriority.High, 1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new PriorityScheduler(1, 1, -1, TaskPriority.High, 1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new PriorityScheduler(1, 1, 1, TaskPriority.High, -1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new PriorityScheduler(0, TaskPriority.High, 1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new PriorityScheduler(1, TaskPriority.High, -1, null);
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
  
  @Test
  public void constructorNullPriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler executor = factory.makePriorityScheduler(1, null, 1);
      
      assertTrue(executor.getDefaultPriority() == PriorityScheduler.DEFAULT_PRIORITY);
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void constructorNullFactoryTest() {
    PriorityScheduler ps = new PriorityScheduler(1, TaskPriority.High, 1, null);
    // should be set with default
    assertNotNull(ps.workerPool.threadFactory);
  }
  
  @Test
  public void makeWithDefaultPriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    TaskPriority originalPriority = TaskPriority.Low;
    TaskPriority newPriority = TaskPriority.High;
    PriorityScheduler scheduler = factory.makePriorityScheduler(1, originalPriority, 1000);
    assertTrue(scheduler.makeWithDefaultPriority(originalPriority) == scheduler);
    PrioritySchedulerInterface newScheduler = scheduler.makeWithDefaultPriority(newPriority);
    try {
      assertEquals(newPriority, newScheduler.getDefaultPriority());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void getAndSetCorePoolSizeTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    int corePoolSize = 1;
    PriorityScheduler scheduler = factory.makePriorityScheduler(corePoolSize, 
                                                                corePoolSize + 10, 1000);
    try {
      assertEquals(corePoolSize, scheduler.getCorePoolSize());
      
      corePoolSize = 10;
      scheduler.setMaxPoolSize(corePoolSize + 10);
      scheduler.setCorePoolSize(corePoolSize);
      
      assertEquals(corePoolSize, scheduler.getCorePoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void getAndSetCorePoolSizeAboveMaxTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    int corePoolSize = 1;
    PriorityScheduler scheduler = factory.makePriorityScheduler(corePoolSize, corePoolSize, 1000);
    try {
      corePoolSize = scheduler.getMaxPoolSize() * 2;
      scheduler.setCorePoolSize(corePoolSize);
      
      assertEquals(corePoolSize, scheduler.getCorePoolSize());
      assertEquals(corePoolSize, scheduler.getMaxPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void lowerSetCorePoolSizeCleansWorkerTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    final int poolSize = 5;
    PriorityScheduler scheduler = factory.makePriorityScheduler(poolSize, poolSize, 0); // must have no keep alive time to work
    try {
      scheduler.prestartAllThreads();
      // must allow core thread timeout for this to work
      scheduler.allowCoreThreadTimeOut(true);
      TestUtils.blockTillClockAdvances();
      
      scheduler.setCorePoolSize(1);
      
      // verify worker was cleaned up
      assertEquals(0, scheduler.getCurrentPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void setCorePoolSizeFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    int corePoolSize = 1;
    int maxPoolSize = 10;
    // first construct a valid scheduler
    PriorityScheduler scheduler = factory.makePriorityScheduler(corePoolSize, maxPoolSize, 1000);
    try {
      // verify no negative values
      try {
        scheduler.setCorePoolSize(-1);
        fail("Exception should have been thrown");
      } catch (IllegalArgumentException expected) {
        // ignored
      }
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getAndSetPoolSizeTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    int corePoolSize = 1;
    PriorityScheduler scheduler = factory.makePriorityScheduler(corePoolSize);
    try {
      assertEquals(corePoolSize, scheduler.getMaxPoolSize());
      
      corePoolSize = 10;
      scheduler.setPoolSize(corePoolSize);
      
      assertEquals(corePoolSize, scheduler.getMaxPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void setPoolSizeFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    // first construct a valid scheduler
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    try {
      // verify no negative values
      try {
        scheduler.setPoolSize(-1);
        fail("Exception should have been thrown");
      } catch (IllegalArgumentException expected) {
        // ignored
      }
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void getAndSetMaxPoolSizeTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    final int originalCorePoolSize = 5;
    int maxPoolSize = originalCorePoolSize;
    PriorityScheduler scheduler = factory.makePriorityScheduler(originalCorePoolSize, maxPoolSize, 1000);
    try {
      maxPoolSize *= 2;
      scheduler.setMaxPoolSize(maxPoolSize);
      
      assertEquals(maxPoolSize, scheduler.getMaxPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void getAndSetMaxPoolSizeBelowCoreTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    final int originalPoolSize = 5;  // must be above 1
    int maxPoolSize = originalPoolSize;
    PriorityScheduler scheduler = factory.makePriorityScheduler(originalPoolSize, maxPoolSize, 1000);
    try {
      maxPoolSize = 1;
      scheduler.setMaxPoolSize(1);
      
      assertEquals(maxPoolSize, scheduler.getMaxPoolSize());
      assertEquals(maxPoolSize, scheduler.getCorePoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void lowerSetMaxPoolSizeCleansWorkerTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    final int poolSize = 5;
    PriorityScheduler scheduler = factory.makePriorityScheduler(poolSize, poolSize, 0); // must have no keep alive time to work
    try {
      scheduler.prestartAllThreads();
      // must allow core thread timeout for this to work
      scheduler.allowCoreThreadTimeOut(true);
      TestUtils.blockTillClockAdvances();
      
      scheduler.setMaxPoolSize(1);
      
      // verify worker was cleaned up
      assertEquals(0, scheduler.getCurrentPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void increaseMaxPoolSizeWithWaitingTaskTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    BlockingTestRunnable btr = new BlockingTestRunnable();
    try {
      scheduler.execute(btr);
      btr.blockTillStarted();
      // all these runnables should be blocked
      List<TestRunnable> executedRunnables = executeTestRunnables(scheduler, 0);
      
      scheduler.setPoolSize((TEST_QTY / 2) + 1); // this should allow the waiting test runnables to quickly execute
      
      Iterator<TestRunnable> it = executedRunnables.iterator();
      while (it.hasNext()) {
        it.next().blockTillStarted(); // will throw exception if not ran
      }
    } finally {
      btr.unblock();
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void setMaxPoolSizeFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(2, 2, 1000);
      try {
        scheduler.setMaxPoolSize(-1); // should throw exception for negative value
        fail("Exception should have been thrown");
      } catch (IllegalArgumentException e) {
        //expected
      }
    } finally {
      factory.shutdown();
    }
  }

  @Test
  public void setPoolSizeBlockedThreadsTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      
      BlockingTestRunnable btr = new BlockingTestRunnable();
      try {
        scheduler.execute(btr);
        
        btr.blockTillStarted();
        
        TestRunnable tr = new TestRunnable();
        scheduler.execute(tr);
        // should not be able to start
        assertEquals(0, tr.getRunCount());
        
        scheduler.setPoolSize(2);
        
        // tr should now be able to start, will throw exception if unable to
        tr.blockTillStarted();
        assertEquals(1, tr.getRunCount());
      } finally {
        btr.unblock();
      }
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getAndSetLowPriorityWaitTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    long lowPriorityWait = 1000;
    PriorityScheduler scheduler = factory.makePriorityScheduler(1, TaskPriority.High, lowPriorityWait);
    try {
      assertEquals(lowPriorityWait, scheduler.getMaxWaitForLowPriority());
      
      lowPriorityWait = Long.MAX_VALUE;
      scheduler.setMaxWaitForLowPriority(lowPriorityWait);
      
      assertEquals(lowPriorityWait, scheduler.getMaxWaitForLowPriority());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void setLowPriorityWaitFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    long lowPriorityWait = 1000;
    PriorityScheduler scheduler = factory.makePriorityScheduler(1, TaskPriority.High, lowPriorityWait);
    try {
      try {
        scheduler.setMaxWaitForLowPriority(-1);
        fail("Exception should have thrown");
      } catch (IllegalArgumentException e) {
        // expected
      }
      
      assertEquals(lowPriorityWait, scheduler.getMaxWaitForLowPriority());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void getAndSetKeepAliveTimeTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    long keepAliveTime = 1000;
    PriorityScheduler scheduler = factory.makePriorityScheduler(1, 1, keepAliveTime);
    try {
      assertEquals(keepAliveTime, scheduler.getKeepAliveTime());
      
      keepAliveTime = Long.MAX_VALUE;
      scheduler.setKeepAliveTime(keepAliveTime);
      
      assertEquals(keepAliveTime, scheduler.getKeepAliveTime());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test
  public void lowerSetKeepAliveTimeCleansWorkerTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    long keepAliveTime = 1000;
    final PriorityScheduler scheduler = factory.makePriorityScheduler(1, 1, keepAliveTime);
    try {
      scheduler.prestartAllCoreThreads();
      // must allow core thread timeout for this to work
      scheduler.allowCoreThreadTimeOut(true);
      TestUtils.blockTillClockAdvances();
      
      scheduler.setKeepAliveTime(0);
      
      // verify worker was cleaned up
      assertEquals(0, scheduler.getCurrentPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test (expected = IllegalArgumentException.class)
  public void setKeepAliveTimeFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(1, 1, 1000);
    
    try {
      scheduler.setKeepAliveTime(-1L); // should throw exception for negative value
      fail("Exception should have been thrown");
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getScheduledTaskCountTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler result = factory.makePriorityScheduler(1);
      // add directly to avoid starting the consumer
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(2, result.getScheduledTaskCount());
      
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(4, result.getScheduledTaskCount());
      assertEquals(4, result.getScheduledTaskCount(null));
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getScheduledTaskCountLowPriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler result = factory.makePriorityScheduler(1);
      // add directly to avoid starting the consumer
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(0, result.getScheduledTaskCount(TaskPriority.Low));
      
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(2, result.getScheduledTaskCount(TaskPriority.Low));
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getScheduledTaskCountHighPriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler result = factory.makePriorityScheduler(1);
      // add directly to avoid starting the consumer
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.highPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(2, result.getScheduledTaskCount(TaskPriority.High));
      
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      result.lowPriorityConsumer
            .executeQueue.add(new OneTimeTaskWrapper(new TestRunnable(), 0));
      
      assertEquals(2, result.getScheduledTaskCount(TaskPriority.High));
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getCurrentPoolSizeTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    try {
      // verify nothing at the start
      assertEquals(0, scheduler.getCurrentPoolSize());
      
      TestRunnable tr = new TestRunnable();
      scheduler.execute(tr);
      
      tr.blockTillStarted();  // wait for execution
      
      assertEquals(1, scheduler.getCurrentPoolSize());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void getCurrentRunningCountTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    try {
      // verify nothing at the start
      assertEquals(0, scheduler.getCurrentRunningCount());
      
      BlockingTestRunnable btr = new BlockingTestRunnable();
      scheduler.execute(btr);
      
      btr.blockTillStarted();
      
      assertEquals(1, scheduler.getCurrentRunningCount());
      
      btr.unblock();
      
      blockTillWorkerAvailable(scheduler);
      
      assertEquals(0, scheduler.getCurrentRunningCount());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void makeSubPoolTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(10);
    try {
      PrioritySchedulerInterface subPool = scheduler.makeSubPool(2);
      assertNotNull(subPool);
      assertTrue(subPool instanceof PrioritySchedulerLimiter);  // if true, test cases are covered under PrioritySchedulerLimiter unit cases
    } finally {
      factory.shutdown();
    }
  }
  
  @Test (expected = IllegalArgumentException.class)
  public void makeSubPoolFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    try {
      scheduler.makeSubPool(2);
      fail("Exception should have been thrown");
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void interruptedDuringRunTest() throws InterruptedException, TimeoutException {
    final long taskRunTime = 1000 * 10;
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      final AsyncVerifier interruptSentAV = new AsyncVerifier();
      TestRunnable tr = new TestRunnable() {
        @Override
        public void handleRunFinish() {
          long startTime = System.currentTimeMillis();
          Thread currentThread = Thread.currentThread();
          while (System.currentTimeMillis() - startTime < taskRunTime && 
                 ! currentThread.isInterrupted()) {
            // spin
          }
          
          interruptSentAV.assertTrue(currentThread.isInterrupted());
          interruptSentAV.signalComplete();
        }
      };
      
      ListenableFuture<?> future = scheduler.submit(tr);
      
      tr.blockTillStarted();
      assertEquals(1, scheduler.getCurrentPoolSize());
      
      // should interrupt
      assertTrue(future.cancel(true));
      interruptSentAV.waitForTest(); // verify thread was interrupted as expected
      
      // verify worker was returned to pool
      blockTillWorkerAvailable(scheduler);
      // verify pool size is still correct
      assertEquals(1, scheduler.getCurrentPoolSize());
      
      // verify interrupted status has been cleared
      final AsyncVerifier interruptClearedAV = new AsyncVerifier();
      scheduler.execute(new Runnable() {
        @Override
        public void run() {
          interruptClearedAV.assertFalse(Thread.currentThread().isInterrupted());
          interruptClearedAV.signalComplete();
        }
      });
      // block till we have verified that the interrupted status has been reset
      interruptClearedAV.waitForTest();
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void interruptedAfterRunTest() throws InterruptedException, TimeoutException {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      scheduler.prestartAllThreads();
      
      // send interrupt
      scheduler.workerPool.availableWorkers.getFirst().thread.interrupt();
      
      final AsyncVerifier av = new AsyncVerifier();
      scheduler.execute(new TestRunnable() {
        @Override
        public void handleRunStart() {
          av.assertFalse(Thread.currentThread().isInterrupted());
          av.signalComplete();
        }
      });
      
      av.waitForTest(); // will throw an exception if invalid
    } finally {
      factory.shutdown();
    }
  }
  
  @Override
  @Test
  public void executeTest() {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    try {
      super.executeTest();

      PrioritySchedulerInterface scheduler = priorityFactory.makePriorityScheduler(2);
      
      TestRunnable tr1 = new TestRunnable();
      TestRunnable tr2 = new TestRunnable();
      scheduler.execute(tr1, TaskPriority.High);
      scheduler.execute(tr2, TaskPriority.Low);
      scheduler.execute(tr1, TaskPriority.High);
      scheduler.execute(tr2, TaskPriority.Low);
      
      tr1.blockTillFinished(1000 * 10, 2); // throws exception if fails
      tr2.blockTillFinished(1000 * 10, 2); // throws exception if fails
    } finally {
      priorityFactory.shutdown();
    }
  }
  
  @Override
  @Test
  public void submitRunnableTest() throws InterruptedException, ExecutionException {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    try {
      super.submitRunnableTest();
      
      PrioritySchedulerInterface scheduler = priorityFactory.makePriorityScheduler(2);
      
      TestRunnable tr1 = new TestRunnable();
      TestRunnable tr2 = new TestRunnable();
      scheduler.submit(tr1, TaskPriority.High);
      scheduler.submit(tr2, TaskPriority.Low);
      scheduler.submit(tr1, TaskPriority.High);
      scheduler.submit(tr2, TaskPriority.Low);
      
      tr1.blockTillFinished(1000 * 10, 2); // throws exception if fails
      tr2.blockTillFinished(1000 * 10, 2); // throws exception if fails
    } finally {
      priorityFactory.shutdown();
    }
  }
  
  @Override
  @Test
  public void submitRunnableWithResultTest() throws InterruptedException, ExecutionException {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    try {
      super.submitRunnableWithResultTest();

      PrioritySchedulerInterface scheduler = priorityFactory.makePriorityScheduler(2);
      
      TestRunnable tr1 = new TestRunnable();
      TestRunnable tr2 = new TestRunnable();
      scheduler.submit(tr1, tr1, TaskPriority.High);
      scheduler.submit(tr2, tr2, TaskPriority.Low);
      scheduler.submit(tr1, tr1, TaskPriority.High);
      scheduler.submit(tr2, tr2, TaskPriority.Low);
      
      tr1.blockTillFinished(1000 * 10, 2); // throws exception if fails
      tr2.blockTillFinished(1000 * 10, 2); // throws exception if fails
    } finally {
      priorityFactory.shutdown();
    }
  }
  
  @Override
  @Test
  public void submitCallableTest() throws InterruptedException, ExecutionException {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    try {
      super.submitCallableTest();

      PrioritySchedulerInterface scheduler = priorityFactory.makePriorityScheduler(2);
      
      TestCallable tc1 = new TestCallable(0);
      TestCallable tc2 = new TestCallable(0);
      scheduler.submit(tc1, TaskPriority.High);
      scheduler.submit(tc2, TaskPriority.Low);
      
      tc1.blockTillTrue(); // throws exception if fails
      tc2.blockTillTrue(); // throws exception if fails
    } finally {
      priorityFactory.shutdown();
    }
  }
  
  @Test
  public void highPriorityDelaySetTest() {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    BlockingTestRunnable btr = new BlockingTestRunnable();
    try {
      final PriorityScheduler scheduler = priorityFactory.makePriorityScheduler(1);
      scheduler.prestartAllThreads();
      int behindWaitTime = -1 * (DELAY_TIME + PriorityScheduler.LOW_PRIORITY_WAIT_TOLLERANCE_IN_MS + 1);
      scheduler.highPriorityConsumer
               .scheduleQueue.add(new OneTimeTaskWrapper(btr, behindWaitTime));
      // this will start the consumer, allowing the previous task to get a worker, but block before this can run
      scheduler.addToScheduleQueue(TaskPriority.High, 
                                   new OneTimeTaskWrapper(new TestRunnable(), 1000 * 10));
      
      // block till we are sure the queue is in the correct state
      btr.blockTillStarted();
      
      assertTrue(scheduler.workerPool.lastHighDelayMillis >= behindWaitTime);
    } finally {
      btr.unblock();
      priorityFactory.shutdown();
    }
  }
  
  @Test
  public void lowPriorityFlowControlTest() {
    PrioritySchedulerFactory priorityFactory = getPrioritySchedulerFactory();
    try {
      final PriorityScheduler scheduler = priorityFactory.makePriorityScheduler(1);
      scheduler.prestartAllThreads();
      int behindWaitTime = -1 * (DELAY_TIME + PriorityScheduler.LOW_PRIORITY_WAIT_TOLLERANCE_IN_MS + 1);
      // make it seem like there is a high priority delay
      scheduler.workerPool.lastHighDelayMillis = behindWaitTime;
      
      TestRunnable lowPriorityRunnable = new TestRunnable();
      scheduler.execute(lowPriorityRunnable, TaskPriority.Low);
      
      assertTrue(lowPriorityRunnable.getDelayTillFirstRun() >= DELAY_TIME);
    } finally {
      priorityFactory.shutdown();
    }
  }
  
  @Test
  public void removeHighPriorityRecurringRunnableTest() {
    removeRecurringRunnableTest(TaskPriority.High);
  }
  
  @Test
  public void removeLowPriorityRecurringRunnableTest() {
    removeRecurringRunnableTest(TaskPriority.Low);
  }
  
  private void removeRecurringRunnableTest(TaskPriority priority) {
    int runFrequency = 1;
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      TestRunnable removedTask = new TestRunnable();
      TestRunnable keptTask = new TestRunnable();
      scheduler.scheduleWithFixedDelay(removedTask, 0, runFrequency, priority);
      scheduler.scheduleWithFixedDelay(keptTask, 0, runFrequency, priority);
      removedTask.blockTillStarted();
      
      assertFalse(scheduler.remove(new TestRunnable()));
      
      assertTrue(scheduler.remove(removedTask));
      
      // verify removed is no longer running, and the kept task continues to run
      int keptRunCount = keptTask.getRunCount();
      int runCount = removedTask.getRunCount();
      TestUtils.sleep(runFrequency * 10);

      // may be +1 if the task was running while the remove was called
      assertTrue(removedTask.getRunCount() == runCount || 
                 removedTask.getRunCount() == runCount + 1);
      
      assertTrue(keptTask.getRunCount() >= keptRunCount);
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void removeHighPriorityRunnableTest() {
    removeRunnableTest(TaskPriority.High);
  }
  
  @Test
  public void removeLowPriorityRunnableTest() {
    removeRunnableTest(TaskPriority.Low);
  }
  
  private void removeRunnableTest(TaskPriority priority) {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      TestRunnable removedTask = new TestRunnable();
      scheduler.submitScheduled(removedTask, 10 * 1000, priority);
      
      assertFalse(scheduler.remove(new TestRunnable()));
      
      assertTrue(scheduler.remove(removedTask));
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void removeHighPriorityCallableTest() {
    removeCallableTest(TaskPriority.High);
  }
  
  @Test
  public void removeLowPriorityCallableTest() {
    removeCallableTest(TaskPriority.Low);
  }
  
  private void removeCallableTest(TaskPriority priority) {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      TestCallable task = new TestCallable();
      scheduler.submitScheduled(task, 1000 * 10, priority);
      
      assertFalse(scheduler.remove(new TestCallable()));
      
      assertTrue(scheduler.remove(task));
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void wrapperSamePriorityTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler highPriorityScheduler = factory.makePriorityScheduler(1, TaskPriority.High, 200);
      assertTrue(highPriorityScheduler.makeWithDefaultPriority(TaskPriority.High) == highPriorityScheduler);
      
      PriorityScheduler lowPriorityScheduler = factory.makePriorityScheduler(1, TaskPriority.Low, 200);
      assertTrue(lowPriorityScheduler.makeWithDefaultPriority(TaskPriority.Low) == lowPriorityScheduler);
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void wrapperTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler highPriorityScheduler = factory.makePriorityScheduler(1, TaskPriority.High, 200);
      assertTrue(highPriorityScheduler.makeWithDefaultPriority(TaskPriority.Low).getDefaultPriority() == TaskPriority.Low);
      
      PriorityScheduler lowPriorityScheduler = factory.makePriorityScheduler(1, TaskPriority.Low, 200);
      assertTrue(lowPriorityScheduler.makeWithDefaultPriority(TaskPriority.High).getDefaultPriority() == TaskPriority.High);
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void isShutdownTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      
      assertFalse(scheduler.isShutdown());
      
      scheduler.shutdown();
      
      assertTrue(scheduler.isShutdown());
      
      scheduler = factory.makePriorityScheduler(1);
      scheduler.shutdownNow();
      
      assertTrue(scheduler.isShutdown());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void shutdownTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      /* adding a run time to have greater chances that runnable 
       * will be waiting to execute after shutdown call.
       */
      TestRunnable lastRunnable = executeTestRunnables(scheduler, 5).get(TEST_QTY - 1);
      
      scheduler.shutdown();
      
      // runnable should finish
      lastRunnable.blockTillFinished();
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void shutdownRecurringTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      final PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      TestRunnable tr = new TestRunnable();
      scheduler.scheduleWithFixedDelay(tr, 0, 0);
      
      tr.blockTillStarted();
      
      scheduler.shutdown();
      
      new TestCondition() {
        @Override
        public boolean get() {
          return scheduler.getCurrentPoolSize() == 0;
        }
      }.blockTillTrue();
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void shutdownFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      
      scheduler.shutdown();
      
      try {
        scheduler.execute(new TestRunnable());
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
      try {
        scheduler.schedule(new TestRunnable(), 1000, null);
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
      try {
        scheduler.scheduleWithFixedDelay(new TestRunnable(), 100, 100);
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void shutdownNowTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    BlockingTestRunnable btr = new BlockingTestRunnable();
    try {
      final PriorityScheduler scheduler = factory.makePriorityScheduler(1);

      // execute one runnable which will not complete
      scheduler.execute(btr);
      btr.blockTillStarted();
      
      final List<TestRunnable> expectedRunnables = new ArrayList<TestRunnable>(TEST_QTY);
      for (int i = 0; i < TEST_QTY; i++) {
        TestRunnable tr = new TestRunnable();
        if (i > 1) {
          /* currently the PriorityScheduler can not remove or cancel tasks which 
           * are waiting for a worker...see issue #75
           */
          expectedRunnables.add(tr);
        }
        scheduler.execute(tr, i % 2 == 0 ? TaskPriority.High : TaskPriority.Low);
      }
      
      // we have this test condition to make the test deterministic due to issue #75
      new TestCondition() {
        @Override
        public boolean get() {
          return scheduler.getScheduledTaskCount() == expectedRunnables.size();
        }
      }.blockTillTrue();
      
      List<Runnable> canceledRunnables = scheduler.shutdownNow();
      // unblock now so that others can run (if the unit test fails)
      btr.unblock();
      
      assertNotNull(canceledRunnables);
      assertTrue(canceledRunnables.containsAll(expectedRunnables));
      assertTrue(expectedRunnables.containsAll(canceledRunnables));
      
      Iterator<TestRunnable> it = expectedRunnables.iterator();
      while (it.hasNext()) {
        assertEquals(0, it.next().getRunCount());
      }
    } finally {
      btr.unblock();
      factory.shutdown();
    }
  }
  
  @Test
  public void shutdownNowFail() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    try {
      PriorityScheduler scheduler = factory.makePriorityScheduler(1);
      
      scheduler.shutdownNow();
      
      try {
        scheduler.execute(new TestRunnable());
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
      try {
        scheduler.schedule(new TestRunnable(), 1000, null);
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
      try {
        scheduler.scheduleWithFixedDelay(new TestRunnable(), 100, 100);
        fail("Execption should have been thrown");
      } catch (RejectedExecutionException e) {
        // expected
      }
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void addToQueueTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    long taskDelay = 1000 * 10; // make it long to prevent it from getting consumed from the queue
    
    PriorityScheduler scheduler = factory.makePriorityScheduler(1);
    try {
      // verify before state
      assertFalse(scheduler.highPriorityConsumer.isRunning());
      assertFalse(scheduler.lowPriorityConsumer.isRunning());
      
      scheduler.addToScheduleQueue(TaskPriority.High, 
                                   new OneTimeTaskWrapper(new TestRunnable(), taskDelay));

      assertEquals(1, scheduler.highPriorityConsumer.scheduleQueue.size());
      assertEquals(0, scheduler.lowPriorityConsumer.scheduleQueue.size());
      assertTrue(scheduler.highPriorityConsumer.isRunning());
      assertFalse(scheduler.lowPriorityConsumer.isRunning());
      
      scheduler.addToScheduleQueue(TaskPriority.Low, 
                                   new OneTimeTaskWrapper(new TestRunnable(), taskDelay));

      assertEquals(1, scheduler.highPriorityConsumer.scheduleQueue.size());
      assertEquals(1, scheduler.lowPriorityConsumer.scheduleQueue.size());
      assertTrue(scheduler.highPriorityConsumer.isRunning());
      assertTrue(scheduler.lowPriorityConsumer.isRunning());
    } finally {
      factory.shutdown();
    }
  }
  
  @Test
  public void makeNewWorkerAfterLowPriorityWaitTest() {
    PrioritySchedulerFactory factory = getPrioritySchedulerFactory();
    PriorityScheduler scheduler = factory.makePriorityScheduler(2, TaskPriority.High, DELAY_TIME);
    BlockingTestRunnable btr = new BlockingTestRunnable();
    try {
      scheduler.execute(btr);
      btr.blockTillStarted();
      
      assertEquals(1, scheduler.getCurrentPoolSize());
      
      TestRunnable lowPriorityRunnable = new TestRunnable();
      scheduler.execute(lowPriorityRunnable, TaskPriority.Low);
      
      lowPriorityRunnable.blockTillFinished();
      btr.unblock();
      assertTrue(lowPriorityRunnable.getDelayTillFirstRun() >= DELAY_TIME);
      assertEquals(2, scheduler.getCurrentPoolSize());
    } finally {
      btr.unblock();
      factory.shutdown();
    }
  }
  
  public interface PrioritySchedulerFactory extends SchedulerServiceFactory {
    @Deprecated
    public PriorityScheduler makePriorityScheduler(int corePoolSize, int maxPoolSize, 
                                                   long keepAliveTimeInMs, 
                                                   TaskPriority defaultPriority, 
                                                   long maxWaitForLowPriority);
    @Deprecated
    public PriorityScheduler makePriorityScheduler(int corePoolSize, int maxPoolSize, 
                                                   long keepAliveTimeInMs);
    
    public PriorityScheduler makePriorityScheduler(int poolSize, TaskPriority defaultPriority, 
                                                   long maxWaitForLowPriority);
    public PriorityScheduler makePriorityScheduler(int poolSize);
  }
  
  private static class PrioritySchedulerTestFactory implements PrioritySchedulerFactory {
    private final List<PriorityScheduler> executors;
    
    private PrioritySchedulerTestFactory() {
      executors = new LinkedList<PriorityScheduler>();
    }

    @Override
    public SubmitterSchedulerInterface makeSubmitterScheduler(int poolSize,
                                                              boolean prestartIfAvailable) {
      return makeSchedulerService(poolSize, prestartIfAvailable);
    }

    @Override
    public SubmitterExecutorInterface makeSubmitterExecutor(int poolSize,
                                                            boolean prestartIfAvailable) {
      return makeSchedulerService(poolSize, prestartIfAvailable);
    }

    @Override
    public SchedulerServiceInterface makeSchedulerService(int poolSize, boolean prestartIfAvailable) {
      PriorityScheduler result = makePriorityScheduler(poolSize, poolSize, Long.MAX_VALUE);
      if (prestartIfAvailable) {
        result.prestartAllThreads();
      }
      
      return result;
    }

    @Override
    public PriorityScheduler makePriorityScheduler(int corePoolSize, int maxPoolSize,
                                                   long keepAliveTimeInMs,
                                                   TaskPriority defaultPriority,
                                                   long maxWaitForLowPriority) {
      @SuppressWarnings("deprecation")
      PriorityScheduler result = new StrictPriorityScheduler(corePoolSize, maxPoolSize, 
                                                             keepAliveTimeInMs, defaultPriority, 
                                                             maxWaitForLowPriority);
      executors.add(result);
      
      return result;
    }

    @Override
    public PriorityScheduler makePriorityScheduler(int corePoolSize, int maxPoolSize, 
                                                   long keepAliveTimeInMs) {
      @SuppressWarnings("deprecation")
      PriorityScheduler result = new StrictPriorityScheduler(corePoolSize, maxPoolSize, 
                                                             keepAliveTimeInMs);
      executors.add(result);
      
      return result;
    }

    @Override
    public PriorityScheduler makePriorityScheduler(int poolSize, TaskPriority defaultPriority,
                                                   long maxWaitForLowPriority) {
      PriorityScheduler result = new StrictPriorityScheduler(poolSize, defaultPriority, 
                                                             maxWaitForLowPriority);
      executors.add(result);
      
      return result;
    }

    @Override
    public PriorityScheduler makePriorityScheduler(int poolSize) {
      PriorityScheduler result = new StrictPriorityScheduler(poolSize);
      executors.add(result);
      
      return result;
    }

    @Override
    public void shutdown() {
      Iterator<PriorityScheduler> it = executors.iterator();
      while (it.hasNext()) {
        it.next().shutdownNow();
      }
    }
  }
}
