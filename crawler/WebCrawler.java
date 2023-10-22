package info.kgeorgiy.ja.shpraidun.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final ExecutorService executorForDownloads;
    private final ExecutorService executorForExtractors;

    @SuppressWarnings("unused")
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.executorForDownloads = Executors.newFixedThreadPool(downloaders);
        this.executorForExtractors = Executors.newFixedThreadPool(extractors);
    }

    @Override
    public Result download(String url, int depth) {
        Map<String, IOException> errors = new ConcurrentHashMap<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> downloaded = ConcurrentHashMap.newKeySet();
        visited.add(url);
        allDownload(url, depth, errors, visited, downloaded);
        return new Result(new ArrayList<>(downloaded), errors);
    }

    private void allDownload( String url, int depth, Map<String, IOException> errors, Set<String> visited,
                              Set<String> downloaded) {
        Set<String> urlsSet = ConcurrentHashMap.newKeySet();
        Set<String> newUrlsSet = ConcurrentHashMap.newKeySet();
        urlsSet.add(url);
        Phaser phaser = new Phaser(1);
        for (int i = 0; i < depth; i++) {
            phaser.bulkRegister(2 * urlsSet.size());
            for (final String currentUrl : urlsSet) {
                executorForDownloads.execute(() -> {
                    try {
                        Document document = downloader.download(currentUrl);
                        downloaded.add(currentUrl);
                        executorForExtractors.execute(() -> {
                            try {
                                newUrlsSet.addAll(document.extractLinks().stream()
                                        .filter(visited::add)
                                        .collect(Collectors.toSet()));
                            } catch (IOException ignored) {
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        });
                    } catch (IOException exception) {
                        phaser.arriveAndDeregister();
                        errors.put(currentUrl, exception);
                    } finally {
                        phaser.arriveAndDeregister();
                    }
                });
            }
            phaser.arriveAndAwaitAdvance();
            urlsSet.clear();
            urlsSet.addAll(newUrlsSet);
            newUrlsSet.clear();
        }
    }

    @Override
    public void close() {
//        executorForExtractors.close();
//        executorForDownloads.close();
        executorForExtractors.shutdownNow();
        executorForDownloads.shutdownNow();
    }

    private static int getArg(String[] args, int idx) {
        return (args.length > idx && Integer.parseInt(args[idx]) > 0) ? Integer.parseInt(args[idx]) : 1;
    }

    public static void main(final String[] args) {
        if (args == null || args.length < 1 || args.length > 5 || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: WebCrawler url [depth [downloads [extractors [perHost]]]]");
            return;
        }
        try(Crawler crawler = new WebCrawler(new CachingDownloader(1), getArg(args, 2), getArg(args, 3), getArg(args, 4))) {
            crawler.download(args[0], Integer.parseInt(args[1])).getDownloaded().forEach(System.out::println);
        } catch (IOException e) {
            System.err.println("CachingDownloader constructor error : " + e.getMessage());
        }
    }
}
