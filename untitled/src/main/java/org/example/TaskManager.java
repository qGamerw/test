package org.example;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Менеджер задач, выполняющий их через динамический пул потоков
 * и собирающий статистику выполнения.
 * <p>
 * Использует {@link DynamicThreadPool} для адаптивного масштабирования
 * и {@link CompletableFuture} для асинхронного выполнения с задержкой.
 * Статистика: общее количество, успешные, ошибки, суммарное и среднее время выполнения.
 * </p>
 */
@Slf4j
public class TaskManager {
    /**
     * Executor для запуска задач.
     */
    private final ExecutorService executor;
    /**
     * Внешний динамический пул потоков.
     */
    private final DynamicThreadPool dynamicPool;
    /**
     * Список будущих для ожидания завершения.
     */
    private final List<CompletableFuture<Void>> futures = Collections.synchronizedList(new ArrayList<>());
    /**
     * Очередь названий задач, завершившихся с ошибкой.
     */
    private final Queue<String> errors = new ConcurrentLinkedQueue<>();
    /**
     * Всего добавлено задач.
     */
    private final AtomicInteger total = new AtomicInteger();
    /**
     * Количество успешно завершённых задач.
     */
    private final AtomicInteger success = new AtomicInteger();
    /**
     * Суммарная длительность всех задач в миллисекундах.
     */
    private final LongAdder totalDurationMs = new LongAdder();

    /**
     * Создаёт менеджер задач на базе динамического пула.
     *
     * @param dynamicPool динамический пул потоков, не должен быть null
     * @throws NullPointerException если dynamicPool == null
     */
    public TaskManager(DynamicThreadPool dynamicPool) {
        this.dynamicPool = Objects.requireNonNull(dynamicPool, "dynamicPool не может быть null");
        this.executor = (ExecutorService) dynamicPool.getExecutor();
        log.info("TaskManager инициализирован");
    }

    /**
     * Добавляет задачу на выполнение с именем и неблокирующей задержкой.
     * <p>Статистика и логирование: время старта, выполнение бизнес-логики, задержка,
     * успешное или ошибочное завершение.</p>
     *
     * @param name          читаемое имя задачи, не null
     * @param businessLogic основная логика задачи, не null
     * @param delaySeconds  время задержки после бизнес-логики, >= 0
     * @throws NullPointerException     если name или businessLogic == null
     * @throws IllegalArgumentException если delaySeconds < 0
     */
    public void addNamedTask(String name, Runnable businessLogic, long delaySeconds) {
        Objects.requireNonNull(name, "Имя задачи не может быть null");
        Objects.requireNonNull(businessLogic, "BusinessLogic не может быть null");
        if (delaySeconds < 0) {
            throw new IllegalArgumentException("delaySeconds должен быть >= 0");
        }
        total.incrementAndGet();
        Instant start = Instant.now();
        log.info("Добавлена задача '{}'", name);

        CompletableFuture<Void> cf = CompletableFuture
                .runAsync(() -> {
                    log.info("Начало выполнения задачи '{}'", name);
                    businessLogic.run();
                    log.info("Бизнес-логика задачи '{}' выполнена", name);
                }, executor)
                .thenCompose(ignored -> CompletableFuture
                        .runAsync(() -> log.debug("Задача '{}' задержка {} секунд завершена", name, delaySeconds),
                                CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                        )
                )
                .whenComplete((res, ex) -> {
                    Instant end = Instant.now();
                    long duration = Duration.between(start, end).toMillis();
                    totalDurationMs.add(duration);
                    if (ex != null) {
                        log.error("Задача '{}' завершилась с ошибкой: {}", name, ex.toString(), ex);
                        errors.add(name);
                    } else {
                        success.incrementAndGet();
                        log.info("Задача '{}' успешно завершена за {} мс", name, duration);
                    }
                });

        futures.add(cf);
    }

    /**
     * Ждёт завершения всех добавленных задач и выводит итоговый отчёт.
     * Очищает внутренние коллекции для повторного использования менеджера.
     */
    public void joinAll() {
        int count = total.get();
        log.info("Ожидание завершения всех задач: всего {}", count);
        List<CompletableFuture<Void>> snapshot;
        synchronized (futures) {
            snapshot = new ArrayList<>(futures);
            futures.clear();
        }
        CompletableFuture.allOf(snapshot.toArray(new CompletableFuture[0])).join();
        log.info("Все задачи ({}) завершены", count);
        printReport();
    }

    /**
     * Завершает работу пула и планировщика внутри DynamicThreadPool.
     * Ждёт окончательного завершения задач с таймаутом.
     */
    public void shutdownThreads() {
        log.info("Инициация завершения пула потоков");
        try {
            dynamicPool.shutdown(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Прерывание при shutdown окна пула", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Выводит детализированный отчёт по выполнению задач и сбрасывает статистику.
     */
    private void printReport() {
        int totalCount = total.getAndSet(0);
        int successCount = success.getAndSet(0);
        long sumDuration = totalDurationMs.sumThenReset();
        int errorCount = errors.size();

        log.info("=== Отчёт по выполнению задач ===");
        log.info("Всего задач:           {}", totalCount);
        log.info("Успешных:             {}", successCount);
        log.info("С ошибками:           {}", errorCount);
        log.info("Средняя длительность: {} мс", totalCount > 0 ? sumDuration / totalCount : 0);

        errors.clear();
    }
}
