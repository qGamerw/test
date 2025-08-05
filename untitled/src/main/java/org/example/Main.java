package org.example;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class Main {

    public static void main(String[] args) throws java.lang.InterruptedException {

        DynamicThreadPool dynamicPool = new DynamicThreadPool(
                /*core=*/ 2,
                /*max=*/ 20,
                /*queueCapacity=*/ 6,
                /*keepAliveSec=*/ 30
        );
        TaskManager mgr = new TaskManager(dynamicPool);

        for (int i = 1; i <= 30; i++) {
            String name = "task-" + i;
            long delay = 10;
            mgr.addNamedTask(name,
                    () -> {
                        log.info("Send request - {}, delay - {}", name, delay);
                        try {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(delay/2));
                        } catch (InterruptedException e) {
                        }
                    },
                    delay
            );
        }

        mgr.joinAll();

//        Thread.sleep(20_000L);
//
//        for (int i = 1; i <= 30; i++) {
//            String name = "task-" + i;
//            long delay = 5; // например, разный delay
//            mgr.addNamedTask(name,
//                    () -> {
//                        log.info("Send request - {}, delay - {}", name, delay);
//                        try {
//                            Thread.sleep(5_000L);
//                        } catch (InterruptedException e) {
//                        }
//                    },
//                    delay
//            );
//        }
//
//        mgr.joinAll();
//
//
//        Thread.sleep(20000L);

        mgr.shutdownThreads();
    }
}
