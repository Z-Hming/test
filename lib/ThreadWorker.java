package com.archly.delivery.engine.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * @author :zj.zou
 * @desc :
 * @date :2019.03.14
 */
@Slf4j
public class ThreadWorker implements Worker {

    private final ThreadPoolExecutor executor;
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final String executorName;

    public ThreadWorker(String name) {
        this(1, name, r -> {
            SecurityManager sm = System.getSecurityManager();
            ThreadGroup group = sm != null ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
            Thread thread = new Thread(group, r, name);
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        });
    }

    public ThreadWorker(int nCore, String name, ThreadFactory factory) {
        this.executorName = name;
        this.executor = new ThreadPoolExecutor(nCore, nCore, 0L, TimeUnit.MILLISECONDS, taskQueue, factory);
        log.info("start worker thread :[{}]", name);
    }

    public boolean execute(Work work) {
        if (executor.isShutdown()) {
            log.error("worker was shutdown. submit work failed ");
            return false;
        }
        executor.execute(() -> {
            try {
                work.action();
                int length = taskQueue.size();
                if (length > 1000) {
                    log.warn("worker queue size over warning length : {} - instance : {} ", length, work.getClass());
                }
            } catch (Exception e) {
                log.error("execute work error.", e);
            }
        });
        return true;
    }

    public Future<CallableWork.Result> submitCallable(CallableWork work) {
        if (executor.isShutdown()) {
            log.error("worker :[{}] was shutdown .", executorName);
            return null;
        }
        Future<CallableWork.Result> future = executor.submit(() -> {
            try {
                work.action();
                int length = taskQueue.size();
                if (length > 1000) {
                    log.warn("worker queue size over warning length : {} - instance : {} ", length, work.getClass());
                }
                return work.getResult();
            } catch (Exception e) {
                log.error("worker:[{}]  execute  task error.", executorName, e);
                final CallableWork.Result result = new CallableWork.Result();
                result.setSuccess(false);
                result.setThrowable(e);
                return result;
            }
        });
        return future;
    }


    public void stop(boolean shutdownNow) {
        log.info("shutdown worker:[{}]", executorName);
        if (shutdownNow) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }
        try {
            while (!executor.isTerminated()) {
                executor.awaitTermination(2, TimeUnit.SECONDS);
                log.info("waiting for worker :[{}]' submitted tasks to complete execution. surplus tasks:{}", executorName, taskQueue.size());
            }
        } catch (InterruptedException e) {
            log.error("worker[{}] shutdown , and waiting for terminated err.", executorName, e);
        }
    }
}
