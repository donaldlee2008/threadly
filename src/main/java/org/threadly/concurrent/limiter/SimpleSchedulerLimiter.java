package org.threadly.concurrent.limiter;

import java.util.concurrent.Callable;

import org.threadly.concurrent.RunnableContainerInterface;
import org.threadly.concurrent.SimpleSchedulerInterface;
import org.threadly.concurrent.SubmitterSchedulerInterface;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.ListenableFutureTask;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.Clock;

/**
 * <p>This class is designed to limit how much parallel execution happens on a provided 
 * {@link SimpleSchedulerInterface}.  This allows the implementor to have one thread pool for all 
 * their code, and if they want certain sections to have less levels of parallelism (possibly 
 * because those those sections would completely consume the global pool), they can wrap the 
 * executor in this class.</p>
 * 
 * <p>Thus providing you better control on the absolute thread count and how much parallelism can 
 * occur in different sections of the program.</p>
 * 
 * <p>This is an alternative from having to create multiple thread pools.  By using this you also 
 * are able to accomplish more efficiently thread use than multiple thread pools would.</p>
 * 
 * @author jent - Mike Jensen
 * @since 2.0.0
 */
public class SimpleSchedulerLimiter extends ExecutorLimiter 
                                    implements SubmitterSchedulerInterface {
  protected final SimpleSchedulerInterface scheduler;
  
  /**
   * Constructs a new limiter that implements the {@link SubmitterSchedulerInterface}.
   * 
   * @param scheduler {@link SimpleSchedulerInterface} implementation to submit task executions to.
   * @param maxConcurrency maximum quantity of runnables to run in parallel
   */
  public SimpleSchedulerLimiter(SimpleSchedulerInterface scheduler, 
                                int maxConcurrency) {
    this(scheduler, maxConcurrency, null);
  }
  
  /**
   * Constructs a new limiter that implements the {@link SubmitterSchedulerInterface}.
   * 
   * @param scheduler {@link SimpleSchedulerInterface} implementation to submit task executions to.
   * @param maxConcurrency maximum quantity of runnables to run in parallel
   * @param subPoolName name to describe threads while tasks running in pool ({@code null} to not change thread names)
   */
  public SimpleSchedulerLimiter(SimpleSchedulerInterface scheduler, 
                                int maxConcurrency, String subPoolName) {
    super(scheduler, maxConcurrency, subPoolName);
    
    this.scheduler = scheduler;
  }

  @Override
  public ListenableFuture<?> submitScheduled(Runnable task, long delayInMs) {
    return submitScheduled(task, null, delayInMs);
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Runnable task, T result, long delayInMs) {
    ArgumentVerifier.assertNotNull(task, "task");
    
    ListenableFutureTask<T> ft = new ListenableFutureTask<T>(false, task, result);
    
    schedule(ft, delayInMs);
    
    return ft;
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Callable<T> task, long delayInMs) {
    ArgumentVerifier.assertNotNull(task, "task");
    
    ListenableFutureTask<T> ft = new ListenableFutureTask<T>(false, task);
    
    schedule(ft, delayInMs);
    
    return ft;
  }

  @Override
  public void schedule(Runnable task, long delayInMs) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(delayInMs, "delayInMs");
    
    if (delayInMs == 0) {
      execute(task);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable(task), delayInMs);
    }
  }

  @Override
  public void scheduleWithFixedDelay(Runnable task, long initialDelay,
                                     long recurringDelay) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(initialDelay, "initialDelay");
    ArgumentVerifier.assertNotNegative(recurringDelay, "recurringDelay");
    
    RecurringDelayWrapper rdw = new RecurringDelayWrapper(task, recurringDelay);
    
    if (initialDelay == 0) {
      executeWrapper(rdw);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable(rdw), initialDelay);
    }
  }

  @Override
  public void scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(initialDelay, "initialDelay");
    ArgumentVerifier.assertGreaterThanZero(period, "period");
    
    RecurringRateWrapper rrw = new RecurringRateWrapper(task, initialDelay, period);
    
    if (initialDelay == 0) {
      executeWrapper(rrw);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable(rrw), initialDelay);
    }
  }
  
  /**
   * <p>Small runnable that allows scheduled tasks to pass through the same execution queue that 
   * immediate execution has to.</p>
   * 
   * @author jent - Mike Jensen
   * @since 1.1.0
   */
  protected class DelayedExecutionRunnable implements Runnable, 
                                                      RunnableContainerInterface {
    private final LimiterRunnableWrapper lrw;

    protected DelayedExecutionRunnable(Runnable runnable) {
      this(new LimiterRunnableWrapper(executor, runnable));
    }

    protected DelayedExecutionRunnable(LimiterRunnableWrapper lrw) {
      this.lrw = lrw;
    }
    
    @Override
    public void run() {
      if (canRunTask()) {  // we can run in the thread we already have
        lrw.run();
      } else {
        addToQueue(lrw);
      }
    }

    @Override
    public Runnable getContainedRunnable() {
      return lrw.getContainedRunnable();
    }
  }

  /**
   * <p>Wrapper for recurring tasks that reschedule with a given delay after completing 
   * execution.</p>
   * 
   * @author jent - Mike Jensen
   * @since 3.1.0
   */
  protected class RecurringDelayWrapper extends LimiterRunnableWrapper {
    protected final long recurringDelay;
    protected final DelayedExecutionRunnable delayRunnable;
    
    public RecurringDelayWrapper(Runnable runnable, long recurringDelay) {
      super(scheduler, runnable);
      
      this.recurringDelay = recurringDelay;
      delayRunnable = new DelayedExecutionRunnable(this);
    }
    
    @Override
    protected void doAfterRunTasks() {
      scheduler.schedule(delayRunnable, recurringDelay);
    }
  }

  /**
   * <p>Wrapper for recurring tasks that reschedule at a fixed rate after completing execution.</p>
   * 
   * @author jent - Mike Jensen
   * @since 3.1.0
   */
  protected class RecurringRateWrapper extends LimiterRunnableWrapper {
    protected final long period;
    protected final DelayedExecutionRunnable delayRunnable;
    private long nextRunTime;
    
    public RecurringRateWrapper(Runnable runnable, long initialDelay, long period) {
      super(scheduler, runnable);
      
      this.period = period;
      delayRunnable = new DelayedExecutionRunnable(this);
      nextRunTime = Clock.accurateForwardProgressingMillis() + initialDelay + period;
    }
    
    @Override
    protected void doAfterRunTasks() {
      nextRunTime += period;
      long nextDelay = nextRunTime - Clock.accurateForwardProgressingMillis();
      if (nextDelay < 1) {
        executeWrapper(this);
      } else {
        scheduler.schedule(delayRunnable, nextDelay);
      }
    }
  }
}
