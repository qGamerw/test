package org.example;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Управляет динамическим пулом потоков, автоматически масштабируя его
 * на основе текущей активности (активных потоков) и загрузки очереди заданий.
 */
@Slf4j
public class DynamicThreadPool {
    /**
     * Минимальное количество основных потоков (начальное значение core).
     */
    private final int minCore;
    /**
     * Исполнитель, управляющий пулом потоков.
     */
    private final ThreadPoolExecutor executor;
    /**
     * Планировщик для регулярного запуска проверки и корректировки размера пула.
     */
    private final ScheduledExecutorService scaler;

    /**
     * Создает динамический пул потоков.
     *
     * @param core          начальное количество основных потоков в пуле
     * @param max           максимальное количество потоков в пуле
     * @param queueCapacity вместимость очереди заданий
     * @param keepAliveSec  время (в секундах), через которое неактивные потоки сверх core будут завершены
     */
    public DynamicThreadPool(int core, int max, int queueCapacity, long keepAliveSec) {
        if (core < 1 || max < core || queueCapacity < 1 || keepAliveSec < 0) {
            throw new IllegalArgumentException("Некорректные параметры пула потоков");
        }
        this.minCore = core;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("dyn-pool-thread-%d")
                .setDaemon(false)
                .build();
        this.executor = new ThreadPoolExecutor(
                core, max,
                keepAliveSec, TimeUnit.SECONDS,
                queue,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        // Позволяем завершать неактивные потоки сверх core
        executor.allowCoreThreadTimeOut(true);
        // Планируем проверку и корректировку размера пула каждую секунду
        this.scaler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scaler-thread");
            t.setDaemon(true); // делаем планировщик демоном, чтобы не блокировать завершение JVM
            return t;
        });
        scaler.scheduleAtFixedRate(this::adjustPoolSize, 1, 1, TimeUnit.SECONDS);
        log.info("Динамический пул потоков запущен с minCore={}, max={}, queueCapacity={}, keepAliveSec={} seconds",
                core, max, queueCapacity, keepAliveSec);
    }

    /**
     * Возвращает исполнитель для отправки в него задач.
     *
     * @return экземпляр {@link Executor}
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Останавливает планировщик масштабирования и завершает работу пула потоков.
     * Ждет завершения активных задач в течение указанного таймаута.
     *
     * @param timeout время ожидания завершения перед принудительным завершением
     * @param unit    единица измерения таймаута
     * @throws InterruptedException если поток ожидания был прерван
     */
    public void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        scaler.shutdown();
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            log.warn("Принудительное завершение пула потоков после ожидания {}", timeout);
            executor.shutdownNow();
        }
        log.info("Динамический пул потоков и планировщик масштабирования остановлены");
    }

    /**
     * Проверяет текущую активность пула и загрузку очереди, корректируя corePoolSize:
     * <ul>
     *   <li>Если одновременно активно больше потоков, чем corePoolSize, и core < max — увеличивает corePoolSize на 1.</li>
     *   <li>Если очередь заданий заполнена более чем наполовину и core < max — увеличивает corePoolSize на 1.</li>
     *   <li>Если очередь пуста и core > minCore — уменьшает corePoolSize на 1.</li>
     * </ul>
     */
    private void adjustPoolSize() {
        int active = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        int core = executor.getCorePoolSize();
        int max = executor.getMaximumPoolSize();
        int capacity = queueSize + executor.getQueue().remainingCapacity();

        if (active > core && core < max) {
            int newCore = Math.min(core + 1, max);
            executor.setCorePoolSize(newCore);
            log.info("Увеличено core по активности до {}", newCore);
        } else if (queueSize > capacity / 2 && core < max) {
            int newCore = Math.min(core + 1, max);
            executor.setCorePoolSize(newCore);
            log.info("Увеличено core по очереди до {}", newCore);
        } else if (queueSize == 0 && core > minCore) {
            int newCore = core - 1;
            executor.setCorePoolSize(newCore);
            log.info("Уменьшено core до {}", newCore);
        }
    }
}
