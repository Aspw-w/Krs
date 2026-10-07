package com.instrumentalist.krs.utils.network;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.ChatUtil;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class FileUtil implements IMinecraft {
    public static final FileUtil INSTANCE = new FileUtil();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long LOCAL_CONFIG_CACHE_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
    private static final int MAX_REMOTE_BODY_BYTES = 1_000_000;
    private static final int MAX_ONLINE_CONFIGS = 512;
    private static final int MAX_LOCAL_CONFIGS = 1024;
    private static final int MAX_REMOTE_CONFIG_NAME_LENGTH = 128;

    private volatile boolean latestClient = true;
    private volatile List<String> onlineCfgs = Collections.emptyList();
    private volatile CachedPaths moduleFiles = CachedPaths.empty();
    private volatile CachedPaths bindFiles = CachedPaths.empty();
    private final Object localConfigCacheLock = new Object();
    private final Object onlineConfigLock = new Object();
    private final AtomicBoolean configListLoadInProgress = new AtomicBoolean();
    private final AtomicBoolean updateCheckInProgress = new AtomicBoolean();
    private OnlineConfigRequest pendingOnlineConfig;
    private boolean onlineConfigWorkerScheduled;
    private final ConcurrentHashMap<String, Long> onlineUpdatedCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> onlineUpdatedQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> onlineUpdatedQueued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean onlineUpdatedDrainScheduled = new AtomicBoolean();
    private final AtomicInteger onlineUpdatedGeneration = new AtomicInteger();
    private final ThreadPoolExecutor networkExecutor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4),
            runnable -> {
                Thread thread = new Thread(runnable, "krs-remote-data");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private FileUtil() {
    }

    public boolean isLatestClient() {
        return latestClient;
    }

    public void setLatestClient(boolean latestClient) {
        this.latestClient = latestClient;
    }

    public List<Path> getModuleFiles() {
        return getJsonFiles("module_configs");
    }

    public List<Path> getBindFiles() {
        return getJsonFiles("bind_configs");
    }

    private List<Path> getJsonFiles(String folder) {
        long now = System.nanoTime();
        CachedPaths cached = folder.equals("module_configs") ? moduleFiles : bindFiles;
        if (now < cached.refreshAfterNanos())
            return cached.paths();

        synchronized (localConfigCacheLock) {
            cached = folder.equals("module_configs") ? moduleFiles : bindFiles;
            now = System.nanoTime();
            if (now < cached.refreshAfterNanos())
                return cached.paths();

            List<Path> paths = scanJsonFiles(folder);
            CachedPaths refreshed = new CachedPaths(paths, System.nanoTime() + LOCAL_CONFIG_CACHE_NANOS);
            if (folder.equals("module_configs"))
                moduleFiles = refreshed;
            else
                bindFiles = refreshed;

            return refreshed.paths();
        }
    }

    private List<Path> scanJsonFiles(String folder) {
        Path configPath = mc.gameDirectory.toPath().resolve(Client.configLocation).resolve(folder);
        ArrayList<Path> jsonFiles = new ArrayList<>();
        if (!Files.isDirectory(configPath))
            return Collections.emptyList();

        try (Stream<Path> paths = Files.list(configPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(FileUtil::isJsonFile)
                    .sorted((first, second) -> first.getFileName().toString().compareToIgnoreCase(second.getFileName().toString()))
                    .limit(MAX_LOCAL_CONFIGS)
                    .forEach(jsonFiles::add);
        } catch (IOException | UncheckedIOException | SecurityException ignored) {
        }

        return List.copyOf(jsonFiles);
    }

    public void invalidateLocalConfigCache() {
        synchronized (localConfigCacheLock) {
            moduleFiles = CachedPaths.empty();
            bindFiles = CachedPaths.empty();
        }
    }

    public String loadOnlineNow(String name) {
        URI uri = createOnlineConfigUri(name);
        return uri == null ? null : get(uri);
    }

    public boolean loadOnlineAsync(String name, Consumer<String> callback) {
        if (callback == null)
            return false;

        synchronized (onlineConfigLock) {
            pendingOnlineConfig = new OnlineConfigRequest(name, callback);
            if (onlineConfigWorkerScheduled)
                return true;

            if (!submitNetworkTask(this::drainOnlineConfigRequests)) {
                pendingOnlineConfig = null;
                return false;
            }

            onlineConfigWorkerScheduled = true;
            return true;
        }
    }

    public void doCfgNetLoaderAsync() {
        submitExclusiveNetworkTask(configListLoadInProgress, this::doCfgNetLoader);
    }

    public void updateCheckAsync() {
        submitExclusiveNetworkTask(updateCheckInProgress, this::updateCheck);
    }

    public void shutdown() {
        networkExecutor.shutdownNow();
        httpClient.shutdownNow();
        synchronized (onlineConfigLock) {
            pendingOnlineConfig = null;
            onlineConfigWorkerScheduled = false;
        }
        configListLoadInProgress.set(false);
        updateCheckInProgress.set(false);
    }

    public void doCfgNetLoader() {
        String body = get("https://uraguchi.okamabeauty.net/krs/cfgs/list.txt");
        if (body != null) {
            onlineCfgs = parseOnlineCfgs(body);
            refreshOnlineConfigUpdatedAt(onlineCfgs);
            ChatUtil.showLog("Loaded online configs");
        } else {
            onlineCfgs = Collections.emptyList();
            ChatUtil.showLog("Skipped online configs");
        }
    }

    public boolean updateCheck() {
        String body = get("https://uraguchi.okamabeauty.net/krs/updts/latest.txt");
        if (body != null) {
            latestClient = body.trim().equals(Client.clientVersion);
            ChatUtil.showLog(latestClient ? "Loaded updates (LATEST)" : "Loaded updates (OUTDATED)");
            return latestClient;
        }

        latestClient = true;
        ChatUtil.showLog("Skipped update check");
        return true;
    }

    public void openInBrowser(String url) {
        Util.getPlatform().openUri(url);
    }

    public List<String> getOnlineCfgs() {
        return onlineCfgs;
    }

    public Long cachedOnlineConfigUpdatedAt(String name) {
        String normalized = normalizeRemoteConfigName(name);
        if (normalized == null)
            return null;

        Long cached = onlineUpdatedCache.get(normalized);
        if (cached == null)
            enqueueOnlineConfigUpdated(normalized);
        return cached == null || cached <= 0L ? null : cached;
    }

    private void refreshOnlineConfigUpdatedAt(List<String> names) {
        onlineUpdatedGeneration.incrementAndGet();
        onlineUpdatedCache.clear();
        onlineUpdatedQueue.clear();
        onlineUpdatedQueued.clear();
        for (String name : names) {
            String normalized = normalizeRemoteConfigName(name);
            if (normalized != null)
                enqueueOnlineConfigUpdated(normalized);
        }
    }

    private void enqueueOnlineConfigUpdated(String normalized) {
        if (!onlineUpdatedQueued.add(normalized))
            return;

        onlineUpdatedQueue.add(normalized);
        if (!onlineUpdatedDrainScheduled.compareAndSet(false, true))
            return;

        int generation = onlineUpdatedGeneration.get();
        if (!submitNetworkTask(() -> drainOnlineConfigUpdated(generation)))
            onlineUpdatedDrainScheduled.set(false);
    }

    private void drainOnlineConfigUpdated(int generation) {
        try {
            String name;
            while ((name = onlineUpdatedQueue.poll()) != null) {
                onlineUpdatedQueued.remove(name);
                if (generation != onlineUpdatedGeneration.get())
                    continue;

                Long updated = readOnlineUpdatedMillis(name);
                if (generation != onlineUpdatedGeneration.get())
                    continue;

                onlineUpdatedCache.put(name, updated == null ? 0L : updated);
            }
        } finally {
            onlineUpdatedDrainScheduled.set(false);
            if (!onlineUpdatedQueue.isEmpty() && onlineUpdatedDrainScheduled.compareAndSet(false, true)) {
                int nextGeneration = onlineUpdatedGeneration.get();
                if (!submitNetworkTask(() -> drainOnlineConfigUpdated(nextGeneration)))
                    onlineUpdatedDrainScheduled.set(false);
            }
        }
    }

    private Long readOnlineUpdatedMillis(String name) {
        return readUpdatedPrefix(loadOnlineNow(name));
    }

    private static Long readUpdatedPrefix(String json) {
        if (json == null || json.isBlank())
            return null;

        int key = json.indexOf("\"_updated\"");
        if (key < 0 || key > 32)
            return null;

        int colon = json.indexOf(':', key + 10);
        if (colon < 0 || colon > key + 24)
            return null;

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start)))
            start++;

        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end)))
            end++;
        if (end == start)
            return null;

        try {
            long updated = Long.parseLong(json.substring(start, end));
            return updated > 0L ? updated : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String get(String url) {
        try {
            return get(URI.create(url));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String get(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.limiting(
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_REMOTE_BODY_BYTES));
            return response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : null;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> parseOnlineCfgs(String configs) {
        if (configs == null)
            return Collections.emptyList();

        LinkedHashSet<String> result = new LinkedHashSet<>();
        var lines = configs.lines().iterator();
        while (lines.hasNext() && result.size() < MAX_ONLINE_CONFIGS) {
            String normalized = normalizeRemoteConfigName(lines.next());
            if (normalized != null)
                result.add(normalized);
        }
        return List.copyOf(result);
    }

    private URI createOnlineConfigUri(String name) {
        String normalized = normalizeRemoteConfigName(name);
        if (normalized == null)
            return null;

        try {
            return new URI("https", "uraguchi.okamabeauty.net", "/krs/cfgs/" + normalized + ".json", null);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private String normalizeRemoteConfigName(String name) {
        String normalized = stripJsonExtension(name);
        if (normalized.isBlank() || normalized.length() > MAX_REMOTE_CONFIG_NAME_LENGTH
                || normalized.equals(".") || normalized.equals(".."))
            return null;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\')
                return null;
        }
        return normalized;
    }

    private static boolean isJsonFile(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private void drainOnlineConfigRequests() {
        while (true) {
            OnlineConfigRequest request;
            synchronized (onlineConfigLock) {
                if (Thread.currentThread().isInterrupted()) {
                    pendingOnlineConfig = null;
                    onlineConfigWorkerScheduled = false;
                    return;
                }

                request = pendingOnlineConfig;
                pendingOnlineConfig = null;
                if (request == null) {
                    onlineConfigWorkerScheduled = false;
                    return;
                }
            }

            String config = loadOnlineNow(request.name());
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    request.callback().accept(config);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private String stripJsonExtension(String name) {
        if (name == null)
            return "";

        String trimmedName = name.trim();
        return endsWithIgnoreCase(trimmedName, ".json") ? trimmedName.substring(0, trimmedName.length() - 5) : trimmedName;
    }

    private boolean endsWithIgnoreCase(String value, String suffix) {
        return value.length() >= suffix.length() && value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private void submitExclusiveNetworkTask(AtomicBoolean inProgress, Runnable task) {
        if (!inProgress.compareAndSet(false, true))
            return;

        if (!submitNetworkTask(() -> {
            try {
                task.run();
            } finally {
                inProgress.set(false);
            }
        })) {
            inProgress.set(false);
        }
    }

    private boolean submitNetworkTask(Runnable task) {
        try {
            networkExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private record CachedPaths(List<Path> paths, long refreshAfterNanos) {
        private static CachedPaths empty() {
            return new CachedPaths(Collections.emptyList(), 0L);
        }
    }

    private record OnlineConfigRequest(String name, Consumer<String> callback) {
    }
}
