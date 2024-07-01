package com.saeed;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Downloader {
    private static final int NUMBER_OF_PARTS = 4;
    private static final long TIMEOUT_MILLIS = 4000;
    private static final List<TaskInfo> TASK_INFO_LIST = Collections.synchronizedList(new ArrayList<>(List.of(
            new TaskInfo(100, true),
            new TaskInfo(100, true),
            new TaskInfo(100, true),
            new TaskInfo(100, true)
    )));


    private static int completedParts = 0;
    private static boolean errorOccurred = false;

    public static void main(String[] args) {
        cleanUp();
        System.out.println("---Cleanup finished---------------");

        final List<Thread> downloadTasks = new ArrayList<>();
        final Object lock = new Object();

        for (int part = 1; part <= NUMBER_OF_PARTS; part++) {
            Thread thread = new Thread(new DownloadTask(part, lock));
            downloadTasks.add(thread);
            thread.start();
        }

        long startTime = System.currentTimeMillis();

        synchronized (lock) {
            while (completedParts < NUMBER_OF_PARTS && System.currentTimeMillis() - startTime < TIMEOUT_MILLIS && !errorOccurred) {
                try {
                    lock.wait(TIMEOUT_MILLIS - (System.currentTimeMillis() - startTime));
                } catch (InterruptedException e) {
                    System.out.println("Timeout or error occurred: " + e.getClass().getName());
                }
            }

            if (completedParts < NUMBER_OF_PARTS || errorOccurred) {
                System.out.println("Timeout reached or error occurred. Not all parts were downloaded.");
                downloadTasks.stream()
                        .filter(Thread::isAlive)
                        .forEach(Thread::interrupt);
            } else {
                combineParts();
                System.out.println("File download complete.");
            }
        }

        deleteParts();
    }

    static class DownloadTask implements Runnable {
        private final int partId;
        private final Object lock;

        public DownloadTask(int partId, Object lock) {
            this.partId = partId;
            this.lock = lock;
        }

        @Override
        public void run() {
            boolean downloaded = downloadPart();

            synchronized (lock) {
                if (downloaded) {
                    completedParts++;
                    lock.notifyAll();
                }else {
                    errorOccurred = true;
                    lock.notifyAll();
                }
            }
        }

        private boolean downloadPart() {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("part_" + partId + ".txt"))) {
                writer.write("Content of part " + partId);
                var taskInfo = TASK_INFO_LIST.isEmpty()
                        ? new TaskInfo(new Random().nextLong(5000), true)
                        : TASK_INFO_LIST.removeFirst();
                System.out.println("Part " + partId + " download started (" + taskInfo + " ).");
                Thread.sleep(taskInfo.waitTime);
                return checkTaskStatus(taskInfo.completionStatus);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    System.out.println("Download of part " + partId + " was interrupted.");
                } else {
                    e.printStackTrace();
                }
                return false;
            }
        }

        private boolean checkTaskStatus(boolean status) {
            if (!status) {
                System.out.println("Part " + partId + " failed.");
            } else {
                System.out.println("Part " + partId + " downloaded.");
            }

            return status;
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
