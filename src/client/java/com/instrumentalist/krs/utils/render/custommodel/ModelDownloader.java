package com.instrumentalist.krs.utils.render.custommodel;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.IMinecraft;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelDownloader implements IMinecraft {
    private static final String BASE_URL = "https://krsclient.download/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private enum Status { PENDING, READY, MISSING }

    private static final Map<String, Status> STATUS = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile Path cacheDir;
    private static HttpClient client;

    private ModelDownloader() {
    }

    public static void startAsync() {
        if (!STARTED.compareAndSet(false, true))
            return;
        cacheDir = mc.gameDirectory.toPath().resolve(Client.configLocation).resolve("custom_models");
        for (ModelProfile profile : ModelCatalog.MODELS)
            STATUS.put(profile.file, Status.PENDING);

        Thread thread = new Thread(ModelDownloader::downloadAll, "Krs Custom Model Downloader");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean isReady(ModelProfile profile) {
        return STATUS.get(profile.file) == Status.READY;
    }

    public static boolean isMissing(ModelProfile profile) {
        return STATUS.get(profile.file) == Status.MISSING;
    }

    static Path glbPath(ModelProfile profile) {
        return cacheDir.resolve("models").resolve(profile.file + ".glb");
    }

    static Path pmxDir(ModelProfile profile) {
        return cacheDir.resolve("krs_pmx").resolve(profile.file);
    }

    static byte[] readPmxFile(ModelProfile profile, String relativePath) throws IOException {
        Path dir = pmxDir(profile);
        Path file = dir.resolve(relativePath).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file))
            return null;
        return Files.readAllBytes(file);
    }

    private static void downloadAll() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        int downloaded = 0;
        for (ModelProfile profile : ModelCatalog.MODELS) {
            boolean fresh;
            try {
                fresh = profile.pmx ? downloadPmx(profile) : download("models/" + profile.file + ".glb", glbPath(profile));
            } catch (IOException | InterruptedException e) {
                fresh = false;
            }
            boolean cached = profile.pmx ? Files.isRegularFile(pmxDir(profile).resolve(profile.file + ".pmx")) : Files.isRegularFile(glbPath(profile));
            STATUS.put(profile.file, cached ? Status.READY : Status.MISSING);
            if (fresh)
                downloaded++;
            else if (!cached)
                ChatUtil.showLog("[Custom Model] Could not download " + profile.label);
        }
        ChatUtil.showLog("[Custom Model] Downloaded " + downloaded + "/" + ModelCatalog.MODELS.size() + " models");
    }

    private static boolean downloadPmx(ModelProfile profile) throws IOException, InterruptedException {
        Path dir = pmxDir(profile);
        String remoteDir = "krs_pmx/" + profile.file + "/";
        if (!download(remoteDir + profile.file + ".pmx", dir.resolve(profile.file + ".pmx")))
            return false;

        PmxModel pmx = PmxModel.load(Files.readAllBytes(dir.resolve(profile.file + ".pmx")), path -> null);
        for (String texture : pmx.texturePaths) {
            Path target = dir.resolve(texture).normalize();
            if (texture.isEmpty() || !target.startsWith(dir))
                continue;
            download(remoteDir + texture, target);
        }
        return true;
    }

    private static boolean download(String remotePath, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + encodePath(remotePath)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            return false;
        }
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/")) {
            if (!encoded.isEmpty())
                encoded.append('/');
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
