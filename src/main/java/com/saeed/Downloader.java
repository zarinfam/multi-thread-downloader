package com.saeed;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Downloader {
    private static final int NUMBER_OF_PARTS = 4;
    private static final long TIMEOUT_MILLIS = 4000;
    private static final List<TaskInfo> TASK_INFO_LIST = Collections.synchronizedList(new ArrayList<>(List.of(
            new TaskInfo(1000, true),
            new TaskInfo(2000, true),
            new TaskInfo(4700, true),
            new TaskInfo(100, false)
    )));

    public static void main(String[] args) {
        cleanUp();
        System.out.println("---Cleanup finished---------------");

        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_PARTS);
        CyclicBarrier barrier = new CyclicBarrier(NUMBER_OF_PARTS + 1, () -> {
            combineParts();
            System.out.println("File download complete.");
        });

        for (int i = 1; i <= NUMBER_OF_PARTS; i++) {
            executorService.submit(new DownloadTask(i, barrier));
        }

        try {
            barrier.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            System.out.println("Timeout or error occurred: " + e.getClass().getName());
        } finally {
            executorService.shutdownNow();
            deleteParts();
        }
    }

    static class DownloadTask implements Runnable {
        private int partId;
        private CyclicBarrier barrier;

        public DownloadTask(int partId, CyclicBarrier barrier) {
            this.partId = partId;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                downloadPart();
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                System.out.println("Task " + partId + " : " + e.getClass().getName());
            }
        }

        private void downloadPart() {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("part_" + partId + ".txt"))) {
                writer.write("Content of part " + partId);
                var taskInfo = TASK_INFO_LIST.isEmpty()
                        ? new TaskInfo(new Random().nextLong(5000), true)
                        : TASK_INFO_LIST.removeFirst();
                System.out.println("Part " + partId + " download started (" + taskInfo + " ).");
                Thread.sleep(taskInfo.waitTime);
                checkTaskStatus(taskInfo.completionStatus);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    System.out.println("Download of part " + partId + " was interrupted.");
                } else {
                    e.printStackTrace();
                }
            }
        }

        private void checkTaskStatus(boolean status) {
            if (!status) {
                Thread.currentThread().interrupt();
            } else {
                System.out.println("Part " + partId + " downloaded.");
            }
        }
    }

    record TaskInfo(long waitTime, boolean completionStatus) {
    }

    private static void combineParts() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("complete_file.txt"))) {
            for (int i = 1; i <= NUMBER_OF_PARTS; i++) {
                writePart(writer, i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("File parts combined successfully.");
    }

    private static void writePart(BufferedWriter writer, int part) {
        try (BufferedReader reader = new BufferedReader(new FileReader("part_" + part + ".txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void deleteParts() {
        for (int i = 1; i <= NUMBER_OF_PARTS; i++) {
            File file = new File("part_" + i + ".txt");
            deleteFile(file);
        }
    }

    private static void deleteFile(File file) {
        if (file.exists()) {
            file.delete();
            System.out.println("Deleted file: " + file.getName());
        }
    }

    private static void cleanUp() {
        deleteParts();
        deleteFile(new File("complete_file.txt"));
    }
}
