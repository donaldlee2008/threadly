package org.threadly.concurrent;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.test.TestRunnable;
import org.threadly.test.TestUtil;

@SuppressWarnings("javadoc")
public class TestablePrioritySchedulerTest {
  private static final int RUNNABLE_COUNT = 10;
  private static final int THREAD_COUNT = 100;
  
  private PriorityScheduledExecutor parentScheduler;
  private TestablePriorityScheduler testScheduler;
  
  @Before
  public void setup() {
    parentScheduler = new PriorityScheduledExecutor(THREAD_COUNT, THREAD_COUNT, 
                                                    1000, TaskPriority.High, 200);
    testScheduler = new TestablePriorityScheduler(parentScheduler);
  }
  
  @After
  public void tearDown() {
    testScheduler = null;
    parentScheduler.shutdown();
    parentScheduler = null;
  }
  
  @Test
  public void executeTest() {
    List<TestRunnable> runnables = new ArrayList<TestRunnable>(RUNNABLE_COUNT);
    for (int i = 0; i < RUNNABLE_COUNT; i++) {
      TestRunnable tr = new TestRunnable();
      runnables.add(tr);
      testScheduler.execute(tr);
    }
    
    assertEquals(testScheduler.tick(), RUNNABLE_COUNT); // should execute all
    
    Iterator<TestRunnable> it = runnables.iterator();
    while (it.hasNext()) {
      assertEquals(it.next().getRunCount(), 1);
    }
    
    assertEquals(testScheduler.tick(), 0); // should not execute anything
    
    it = runnables.iterator();
    while (it.hasNext()) {
      assertEquals(it.next().getRunCount(), 1);
    }
  }
  
  @Test
  public void scheduleExecuteTest() {
    long scheduleDelay = 1000 * 10;
    
    TestRunnable executeRun = new TestRunnable();
    TestRunnable scheduleRun = new TestRunnable();
    
    testScheduler.schedule(scheduleRun, scheduleDelay);
    testScheduler.execute(executeRun);

    long startTime = System.currentTimeMillis();
    assertEquals(testScheduler.tick(startTime), 1);

    assertEquals(executeRun.getRunCount(), 1);   // should have run
    assertEquals(scheduleRun.getRunCount(), 0);  // should NOT have run yet
    
    assertEquals(testScheduler.tick(startTime + scheduleDelay), 1);
    
    assertEquals(executeRun.getRunCount(), 1);   // should NOT have run again
    assertEquals(scheduleRun.getRunCount(), 1);  // should have run
    
    assertEquals(testScheduler.tick(startTime + scheduleDelay), 0); // should not execute anything
    
    assertEquals(executeRun.getRunCount(), 1);   // should NOT have run again
    assertEquals(scheduleRun.getRunCount(), 1);  // should NOT have run again
  }
  
  @Test
  public void recurringExecuteTest() {
    long delay = 1000 * 10;
    
    TestRunnable immediateRun = new TestRunnable();
    TestRunnable initialDelay = new TestRunnable();
    
    testScheduler.scheduleWithFixedDelay(immediateRun, 0, delay);
    testScheduler.scheduleWithFixedDelay(initialDelay, delay, delay);

    long startTime = System.currentTimeMillis();
    assertEquals(testScheduler.tick(startTime), 1);
    
    assertEquals(immediateRun.getRunCount(), 1);  // should have run
    assertEquals(initialDelay.getRunCount(), 0);  // should NOT have run yet

    assertEquals(testScheduler.tick(startTime + delay), 2);
    
    assertEquals(immediateRun.getRunCount(), 2);  // should have run again
    assertEquals(initialDelay.getRunCount(), 1);  // should have run for the first time
    
    assertEquals(testScheduler.tick(startTime + (delay * 2)), 2);
    
    assertEquals(immediateRun.getRunCount(), 3);  // should have run again
    assertEquals(initialDelay.getRunCount(), 2);  // should have run again
    
    assertEquals(testScheduler.tick(startTime + (delay * 2)), 0); // should not execute anything
    
    assertEquals(immediateRun.getRunCount(), 3);  // should NOT have run again
    assertEquals(initialDelay.getRunCount(), 2);  // should NOT have run again
  }
  
  @Test
  public void tickTimeNoProgressTest() {
    for (int i = 0; i < RUNNABLE_COUNT; i++) {
      TestRunnable tr = new TestRunnable();
      testScheduler.execute(tr);
    }

    long now;
    assertEquals(testScheduler.tick(now = System.currentTimeMillis()), RUNNABLE_COUNT); // should execute all
    
    TestUtil.blockTillClockAdvances();
    
    for (int i = 0; i < RUNNABLE_COUNT; i++) {
      TestRunnable tr = new TestRunnable();
      testScheduler.execute(tr);
    }
    
    assertEquals(testScheduler.tick(now), RUNNABLE_COUNT); // should execute all again
  }
  
  @Test (expected = IllegalArgumentException.class)
  public void tickFail() {
    long now;
    testScheduler.tick(now = System.currentTimeMillis());
    
    testScheduler.tick(now - 1);
    fail("Exception should have been thrown");
  }
}
