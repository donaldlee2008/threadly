package org.threadly.concurrent.future;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * <p>Future where you can add a listener which is called once the future has completed.  The 
 * runnable will be called once the future completes either as a cancel, with result, or with an 
 * exception.</p>
 * 
 * @author jent - Mike Jensen
 * @since 1.0.0
 * @param <T> The result object type returned by this future
 */
public interface ListenableFuture<T> extends Future<T> {
  /**
   * Add a listener to be called once the future has completed.  If the future has already 
   * finished, this will be called immediately.
   * 
   * The listener from this call will execute on the same thread the result was produced on, or on 
   * the adding thread if the future is already complete.  If the runnable has high complexity, 
   * consider passing an {@link Executor} in for it to be ran on.
   * 
   * @param listener the listener to run when the computation is complete
   */
  public void addListener(Runnable listener);
  
  
  /**
   * Add a listener to be called once the future has completed.  If the future has already 
   * finished, this will be called immediately.
   * 
   * If the provided {@link Executor} is null, the listener will execute on the thread which 
   * computed the original future (once it is done).  If the future has already completed, the 
   * listener will execute immediately on the thread which is adding the listener.
   * 
   * @param listener the listener to run when the computation is complete
   * @param executor {@link Executor} the listener should be ran on, or {@code null}
   */
  public void addListener(Runnable listener, Executor executor);
  
  /**
   * Add a {@link FutureCallback} to be called once the future has completed.  If the future has 
   * already finished, this will be called immediately.
   * 
   * The callback from this call will execute on the same thread the result was produced on, or on 
   * the adding thread if the future is already complete.  If the callback has high complexity, 
   * consider passing an executor in for it to be called on.
   * 
   * @since 1.2.0
   * 
   * @param callback to be invoked when the computation is complete
   */
  public void addCallback(FutureCallback<? super T> callback);
  
  /**
   * Add a {@link FutureCallback} to be called once the future has completed.  If the future has 
   * already finished, this will be called immediately.
   * 
   * If the provided {@link Executor} is null, the callback will execute on the thread which 
   * computed the original future (once it is done).  If the future has already completed, the 
   * callback will execute immediately on the thread which is adding the callback.
   * 
   * @since 1.2.0
   * 
   * @param callback to be invoked when the computation is complete
   * @param executor {@link Executor} the callback should be ran on, or {@code null}
   */
  public void addCallback(FutureCallback<? super T> callback, Executor executor);
}
