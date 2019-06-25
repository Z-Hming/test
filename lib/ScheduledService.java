
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.*;


public class ScheduledService {

    private ScheduledExecutorService service;

    public ScheduledService() {
        service = Executors.newScheduledThreadPool(1, r -> {
            SecurityManager sm = System.getSecurityManager();
            ThreadGroup group = sm != null ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
            Thread thread = new Thread(group, r, "Thread-Scheduled");
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        });
    }


    public void stop() {
        if (service != null) {
            service.shutdown();
            log.info("stop Thread-Scheduled service .");
        }
    }

    public final ScheduledFuture<?> addEvent(TimerEvent event) {
        if (event == null) {
            log.error("event can not be null");
            return null;
        }
        if (event.getDelay() == 0) {
            return service.schedule(event, event.getFirstDelay(), TimeUnit.MILLISECONDS);
        } else if (event.isLoopFixed()) {
            return service.scheduleAtFixedRate(event, event.getFirstDelay(), event.getDelay(), TimeUnit.MILLISECONDS);
        } else {
            return service.scheduleWithFixedDelay(event, event.getFirstDelay(), event.getDelay(), TimeUnit.MILLISECONDS);
        }

    }

    /**
     * 添加一个包含指定次数的定时事件
     * @param event 定时执行的事件
     * @param count 指定执行次数
     * @return
     */
    public final ScheduledFuture<?> addEvent(TimerEvent event, int count) {
        if (event == null || count <= 0) {
            log.error("event can not be null or  count must > 0");
            return null;
        }
        ScheduledFuture<?> future = null;
        TimerEventWrapper wrapper = new TimerEventWrapper(event, count);
        if (event.getDelay() == 0) {
            future = service.schedule(wrapper, wrapper.event.getFirstDelay(), TimeUnit.MILLISECONDS);
        } else if (event.isLoopFixed()) {
            future = service.scheduleAtFixedRate(wrapper, wrapper.event.getFirstDelay(), wrapper.event.getDelay(), TimeUnit.MILLISECONDS);
        } else {
            future = service.scheduleWithFixedDelay(wrapper, wrapper.event.getFirstDelay(), wrapper.event.getDelay(), TimeUnit.MILLISECONDS);
        }
        wrapper.future = future;
        return future;
    }

    public class TimerEventWrapper implements Runnable {

        /**
         * 包装的事件
         */
        private final TimerEvent event;
        /**
         * 事件执行的Future
         */
        private ScheduledFuture future;

        /**
         * 事件执行的次数
         */
        private volatile int count;

        /**
         * 包装定时器为指定次数
         * @param event
         * @param count
         */
        public TimerEventWrapper(TimerEvent event, int count) {
            this.event = event;
            this.count = count;
        }

        @Override
        public void run() {
            if (count > 0) {
                event.run();
                count--;
            } else {
                future.cancel(false);
            }
        }
    }

    @Data
    public abstract static class TimerEvent implements Runnable {
        /**
         * 首次执行延迟时间  单位：ms
         */
        private long firstDelay;
        /**
         * 循环执行时间周期 单位：ms
         */
        private long delay;

        /**
         * 循环执行是否采用固定频率
         */
        private boolean loopFixed;

        public TimerEvent() {

        }

        /**
         * 创建一个只执行一次的 定时器事件
         * @param delay 延迟执行的时间 单位 ms
         */
        public TimerEvent(long delay) {
            this.firstDelay = delay;
            this.loopFixed = false;
            this.delay = 0;
        }

        /**
         * 创建一个循环执行的计时器事件.
         * @param firstDelay 首次执行的延迟时间（单位：ms）
         * @param delay 循环执行的间隔时间（单位：ms）
         * @param loopFixed 循环执行时是否采用固定频率：
         * <p>              true表示不管上一次执行所花费的时间为何，下一次执行必然在initialDelay + 2 * delay后进行；
         * <p>              false表示每次执行必然延迟给定的时间(delay)，即下一次执行会等待上一次执行完成后再计算延迟时间.
         */
        public TimerEvent(long firstDelay, long delay, boolean loopFixed) {
            this.firstDelay = firstDelay;
            this.delay = delay;
            this.loopFixed = loopFixed;
        }
    }
}
