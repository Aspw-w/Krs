package com.instrumentalist.krs.utils.network;

import com.instrumentalist.krs.hacks.features.nulling.PluginsDetector;
import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerLookup implements IMinecraft {

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Set<Integer> MINECRAFT_PORTS = Set.of(
            25565, 25566, 25567, 25568, 25569, 25570, 25571, 25572,
            25575, 25577, 25580, 25599, 25600, 19132, 19133
    );

    private ServerLookup() {
    }

    public static String start(String requestedAddress, Consumer<List<String>> callback) {
        if (callback == null)
            return "Lookup callback is missing";

        if (!RUNNING.compareAndSet(false, true))
            return "A server lookup is already running";

        LocalSnapshot snapshot;
        try {
            snapshot = captureLocal(requestedAddress);
        } catch (RuntimeException exception) {
            RUNNING.set(false);
            return "Failed to read local server data";
        }

        if (snapshot.address == null || snapshot.address.isBlank()) {
            RUNNING.set(false);
            return "No server address. Connect first or use server <address>";
        }

        Thread thread = new Thread(() -> {
            try {
                callback.accept(lookup(snapshot));
            } catch (Exception exception) {
                callback.accept(List.of("Server lookup failed: " + safeMessage(exception)));
            } finally {
                RUNNING.set(false);
            }
        }, "krs-server-lookup");
        thread.setDaemon(true);
        thread.start();
        return null;
    }

    private static LocalSnapshot captureLocal(String requestedAddress) {
        String address = blankToNull(requestedAddress);
        ServerData serverData = mc.getCurrentServer();
        ClientPacketListener listener = mc.getConnection();
        Connection connection = listener == null ? null : listener.getConnection();

        if (address == null && serverData != null)
            address = blankToNull(serverData.ip);

        if (address == null && connection != null)
            address = socketToHostPort(connection.getRemoteAddress());

        ServerAddress parsed = parseAddress(address);
        String host = parsed == null ? address : parsed.getHost();
        int port = parsed == null ? 25565 : parsed.getPort();

        List<String> localPlayers = new ArrayList<>();
        if (listener != null) {
            for (PlayerInfo player : listener.getListedOnlinePlayers()) {
                if (player != null && player.getProfile() != null && player.getProfile().name() != null)
                    localPlayers.add(player.getProfile().name());
            }
        }

        List<String> serverLinks = new ArrayList<>();
        if (listener != null) {
            ServerLinks links = listener.serverLinks();
            if (links != null && !links.isEmpty()) {
                for (ServerLinks.Entry entry : links.entries()) {
                    if (entry == null || entry.link() == null)
                        continue;
                    serverLinks.add(entry.displayName().getString() + " -> " + entry.link());
                }
            }
        }

        int ping = -1;
        if (listener != null && mc.player != null) {
            PlayerInfo self = listener.getPlayerInfo(mc.player.getUUID());
            if (self != null)
                ping = self.getLatency();
        }

        return new LocalSnapshot(
                address,
                host,
                port,
                serverData,
                listener,
                connection,
                connection == null ? null : connection.getRemoteAddress(),
                listener == null ? null : blankToNull(listener.serverBrand()),
                listener != null && listener.onlineMode(),
                connection != null && ((IConnection) connection).krs$isEncrypted(),
                connection != null && connection.isConnected(),
                ping,
                serverData == null ? null : serverData.getResourcePackStatus() == null ? null : serverData.getResourcePackStatus().name(),
                localPlayers,
                serverLinks,
                PluginsDetector.plugins == null ? List.of() : List.of(PluginsDetector.plugins),
                PluginsDetector.getAntiCheats() == null ? List.of() : PluginsDetector.getAntiCheats()
        );
    }

    private static List<String> lookup(LocalSnapshot local) {
        DnsInfo dns = resolveDns(local.host);
        JSONObject javaStatus = fetchJson("https://api.mcsrvstat.us/3/" + encodeAddress(local.host, local.port));
        final String resolvedIp = firstNonBlank(
                jsonString(javaStatus, "ip"),
                socketIp(local.remote),
                first(dns.ipv4),
                looksLikeIp(local.host) ? local.host : null
        );
        int remotePort = javaStatus != null && javaStatus.has("port") ? javaStatus.optInt("port", local.port) : local.port;

        CompletableFuture<JSONObject> geoFuture = resolvedIp == null ? completedJson() :
                CompletableFuture.supplyAsync(() -> fetchJson("https://ipwho.is/" + resolvedIp));
        CompletableFuture<JSONObject> shodanFuture = resolvedIp == null ? completedJson() :
                CompletableFuture.supplyAsync(() -> fetchJson("https://internetdb.shodan.io/" + resolvedIp));
        CompletableFuture<List<String>> reverseFuture = resolvedIp == null ? CompletableFuture.completedFuture(List.of()) :
                CompletableFuture.supplyAsync(() -> fetchReverseHosts(resolvedIp));
        CompletableFuture<JSONObject> mcStatusFuture = CompletableFuture.supplyAsync(() ->
                fetchJson("https://api.mcstatus.io/v2/status/java/" + encodeAddress(local.host, local.port)));

        JSONObject geo = joinJson(geoFuture);
        JSONObject shodan = joinJson(shodanFuture);
        JSONObject mcStatus = joinJson(mcStatusFuture);
        List<String> reverseHosts = joinList(reverseFuture);

        String rawIp = firstNonBlank(resolvedIp, jsonString(mcStatus, "ip_address"), jsonString(geo, "ip"));

        List<RelatedServer> related = findRelatedServers(local, rawIp, remotePort, shodan, reverseHosts, javaStatus, mcStatus);
        List<String> lines = new ArrayList<>();
        lines.add("===== Server =====");
        lines.add("Address: " + displayAddress(local.host, local.port));
        lines.add("Raw IPv4: " + joinOrUnknown(collectIpv4(rawIp, dns, local.remote, javaStatus, mcStatus)));
        lines.add("Raw IPv6: " + joinOrUnknown(collectIpv6(dns, local.remote)));
        lines.add("Reverse DNS: " + firstNonBlank(reverseName(rawIp), nestedString(geo, "connection", "domain"), "Unknown"));
        lines.add("SRV: " + formatSrv(dns, javaStatus, mcStatus));
        lines.add("Remote socket: " + (local.remote == null ? "Not connected" : local.remote.toString()));
        lines.add("Online: " + yesNo(javaStatus != null && javaStatus.optBoolean("online", false) || mcStatus != null && mcStatus.optBoolean("online", false) || local.connected));
        lines.add("MOTD: " + firstNonBlank(cleanMotd(javaStatus), cleanMotdMcStatus(mcStatus), componentText(local.serverData == null ? null : local.serverData.motd), "Unknown"));
        lines.add("Version: " + formatVersion(javaStatus, mcStatus, local.serverData));
        lines.add("Software: " + firstNonBlank(jsonString(javaStatus, "software"), jsonString(mcStatus, "software"), inferSoftware(local), "Unknown"));
        lines.add("Players: " + formatPlayers(javaStatus, mcStatus, local));
        String playerSample = formatPlayerSample(javaStatus, mcStatus, local);
        if (playerSample != null)
            lines.add("Sample: " + playerSample);
        lines.add("Map: " + firstNonBlank(nestedString(javaStatus, "map", "clean"), nestedString(javaStatus, "map", "raw"), "Unknown"));
        lines.add("Query / ping / SRV used: " + formatDebug(javaStatus));
        lines.add("EULA blocked: " + yesNo(javaStatus != null && javaStatus.optBoolean("eula_blocked", false) || mcStatus != null && mcStatus.optBoolean("eula_blocked", false)));
        lines.add("Plugins: " + formatNamedList(collectPlugins(javaStatus, mcStatus, local)));
        lines.add("Mods: " + formatNamedList(collectNamed(javaStatus, "mods")));
        lines.add("Brand: " + firstNonBlank(local.brand, "Unknown"));
        lines.add("Online mode: " + (local.listener == null ? "Unknown" : yesNo(local.onlineMode)));
        lines.add("Encrypted: " + (local.connection == null ? "Unknown" : yesNo(local.encrypted)));
        lines.add("Ping: " + (local.ping >= 0 ? local.ping + " ms" : local.serverData != null && local.serverData.ping >= 0 ? local.serverData.ping + " ms" : "Unknown"));
        lines.add("Resource pack: " + firstNonBlank(prettyEnum(local.resourcePackStatus), "Unknown"));
        lines.add("Server links: " + (local.serverLinks.isEmpty() ? "None" : String.join(" | ", local.serverLinks)));
        if (!local.detectedPlugins.isEmpty())
            lines.add("Detected plugins: " + joinCapped(local.detectedPlugins, 20));
        if (!local.detectedAntiCheats.isEmpty())
            lines.add("Detected AC: " + String.join(", ", local.detectedAntiCheats));

        lines.add("--- Geo ---");
        if (geo != null && geo.optBoolean("success", false)) {
            lines.add(joinNonBlank(" / ", geo.optString("country", null), geo.optString("region", null), geo.optString("city", null)));
            JSONObject connection = geo.optJSONObject("connection");
            if (connection != null) {
                lines.add("ISP: " + firstNonBlank(connection.optString("isp", null), "Unknown")
                        + " | Org: " + firstNonBlank(connection.optString("org", null), "Unknown")
                        + " | ASN: " + (connection.has("asn") ? String.valueOf(connection.optInt("asn")) : "Unknown"));
            }
            JSONObject timezone = geo.optJSONObject("timezone");
            if (timezone != null)
                lines.add("Timezone: " + firstNonBlank(timezone.optString("id", null), "Unknown"));
        } else {
            lines.add("Unavailable");
        }

        lines.add("--- Host ---");
        List<Integer> ports = jsonIntList(shodan, "ports");
        lines.add("Open ports: " + (ports.isEmpty() ? "Unknown" : joinInts(ports)));
        List<String> hostnames = unique(concat(jsonStringList(shodan, "hostnames"), reverseHosts, dns.hostnames));
        lines.add("Hostnames: " + (hostnames.isEmpty() ? "Unknown" : joinCapped(hostnames, 12)));
        List<String> tags = jsonStringList(shodan, "tags");
        List<String> cpes = jsonStringList(shodan, "cpes");
        if (!tags.isEmpty())
            lines.add("Tags: " + String.join(", ", tags));
        if (!cpes.isEmpty())
            lines.add("CPEs: " + joinCapped(cpes, 8));

        lines.add("--- Vulnerabilities ---");
        List<String> vulns = analyzeVulnerabilities(local, javaStatus, mcStatus, shodan, ports, collectPlugins(javaStatus, mcStatus, local));
        if (vulns.isEmpty())
            lines.add("No obvious issues from available data");
        else
            lines.addAll(vulns);

        lines.add("--- Related / linked ---");
        if (related.isEmpty()) {
            lines.add("None found");
        } else {
            for (RelatedServer server : related)
                lines.add(server.describe());
        }

        return lines;
    }

    private static List<RelatedServer> findRelatedServers(
            LocalSnapshot local,
            String rawIp,
            int currentPort,
            JSONObject shodan,
            List<String> reverseHosts,
            JSONObject javaStatus,
            JSONObject mcStatus
    ) {
        LinkedHashSet<RelatedServer> related = new LinkedHashSet<>();

        for (String link : local.serverLinks)
            related.add(new RelatedServer("Official link", link, null, null));

        JSONObject srv = mcStatus == null ? null : mcStatus.optJSONObject("srv_record");
        if (srv != null) {
            String host = srv.optString("host", "");
            int port = srv.optInt("port", 25565);
            if (!host.isBlank())
                related.add(new RelatedServer("SRV target", host + ":" + port, null, null));
        }

        addInfoServers(related, javaStatus);
        addInfoServers(related, mcStatus == null ? null : wrapMotd(mcStatus));

        List<Integer> ports = jsonIntList(shodan, "ports");
        List<Integer> extraPorts = new ArrayList<>();
        for (int port : ports) {
            if (port == currentPort)
                continue;
            if (MINECRAFT_PORTS.contains(port) || port >= 25500 && port <= 25650 || port == 19132 || port == 19133)
                extraPorts.add(port);
        }
        extraPorts.sort(Integer::compareTo);
        int pinged = 0;
        for (int port : extraPorts) {
            if (pinged >= 5)
                break;
            if (rawIp == null)
                break;
            pinged++;
            JSONObject status = fetchJson("https://api.mcsrvstat.us/3/" + encodeAddress(rawIp, port));
            related.add(describePing("Same IP port", rawIp, port, status));
        }

        String rootDomain = rootDomain(local.host);
        List<String> candidateHosts = new ArrayList<>();
        for (String hostname : unique(concat(jsonStringList(shodan, "hostnames"), reverseHosts))) {
            if (hostname.equalsIgnoreCase(local.host))
                continue;
            if (isLikelyMinecraftHost(hostname, rootDomain))
                candidateHosts.add(hostname);
        }
        candidateHosts.sort(Comparator
                .comparingInt((String host) -> isPriorityHost(host) ? 0 : 1)
                .thenComparing(host -> host.toLowerCase(Locale.ROOT)));
        int hostPinged = 0;
        for (String hostname : candidateHosts) {
            if (hostPinged >= 4)
                break;
            if (hostname.equalsIgnoreCase(local.host))
                continue;
            hostPinged++;
            JSONObject status = fetchJson("https://api.mcsrvstat.us/3/" + hostname);
            related.add(describePing("Linked hostname", hostname, status != null ? status.optInt("port", 25565) : 25565, status));
        }

        if (ports.contains(19132) || ports.contains(19133)) {
            String bedrockHost = rawIp == null ? local.host : rawIp;
            JSONObject bedrock = fetchJson("https://api.mcsrvstat.us/bedrock/3/" + bedrockHost);
            if (bedrock != null)
                related.add(describePing("Bedrock", bedrockHost, bedrock.optInt("port", 19132), bedrock));
        }

        List<RelatedServer> result = new ArrayList<>(related);
        if (result.size() > 16)
            return result.subList(0, 16);
        return result;
    }

    private static void addInfoServers(Set<RelatedServer> related, JSONObject status) {
        if (status == null)
            return;

        JSONObject info = status.optJSONObject("info");
        List<String> lines = new ArrayList<>();
        if (info != null)
            lines.addAll(jsonStringList(info, "clean"));
        lines.addAll(motdLines(status));
        for (String line : lines) {
            Matcher matcher = IPV4_PATTERN.matcher(line);
            while (matcher.find())
                related.add(new RelatedServer("Mentioned IP", matcher.group(), null, line));
        }
    }

    private static JSONObject wrapMotd(JSONObject mcStatus) {
        JSONObject wrapped = new JSONObject();
        if (mcStatus != null && mcStatus.has("motd"))
            wrapped.put("motd", mcStatus.get("motd"));
        return wrapped;
    }

    private static RelatedServer describePing(String kind, String host, int port, JSONObject status) {
        if (status == null)
            return new RelatedServer(kind, host + ":" + port, "unknown", null);

        boolean online = status.optBoolean("online", false);
        String version = firstNonBlank(status.optString("version", null), nestedString(status, "protocol", "name"));
        String software = jsonString(status, "software");
        String players = formatPlayers(status, null, null);
        if ("Unknown".equals(players))
            players = null;
        String detail = joinNonBlank(" ", online ? "online" : "offline", version, software, players);
        return new RelatedServer(kind, host + ":" + port, detail, null);
    }

    private static List<String> analyzeVulnerabilities(
            LocalSnapshot local,
            JSONObject javaStatus,
            JSONObject mcStatus,
            JSONObject shodan,
            List<Integer> ports,
            List<String> plugins
    ) {
        List<String> vulns = new ArrayList<>();
        String versionText = firstNonBlank(
                jsonString(javaStatus, "version"),
                nestedString(javaStatus, "protocol", "name"),
                mcStatus == null ? null : nestedString(mcStatus, "version", "name_clean"),
                componentText(local.serverData == null ? null : local.serverData.version)
        );
        String software = firstNonBlank(jsonString(javaStatus, "software"), jsonString(mcStatus, "software"), inferSoftware(local), "").toLowerCase(Locale.ROOT);
        boolean query = javaStatus != null && javaStatus.optJSONObject("debug") != null && javaStatus.getJSONObject("debug").optBoolean("query", false);

        VersionSpan span = parseVersionSpan(versionText);
        if (span.overlaps(1, 7, 0, 1, 18, 1))
            vulns.add("[!] Log4Shell (CVE-2021-44228) possible: version range includes 1.7-1.18.1");

        if (local.listener != null && !local.onlineMode)
            vulns.add("[!] Offline mode / cracked: names and UUIDs can be spoofed");

        if (local.connection != null && local.connected && !local.encrypted)
            vulns.add("[!] Connection is not encrypted");

        if (query)
            vulns.add("[!] Query protocol enabled: plugins and player list can leak");

        if (ports.contains(25575))
            vulns.add("[!] Port 25575 open: possible exposed RCON");

        boolean proxy = containsAny(software, "bungee", "waterfall", "velocity", "flamecord", "hexacord");
        boolean hasGuard = containsPlugin(plugins, "bungeeguard", "bungee-guard", "ipwhitelist", "onlyproxyjoin", "tcpshield", "antibot");
        if (proxy && !hasGuard)
            vulns.add("[!] Proxy software without BungeeGuard/IP whitelist: backend UUID spoof if ports are exposed");

        if (ports.stream().anyMatch(port -> port != 25565 && (MINECRAFT_PORTS.contains(port) || port >= 25500 && port <= 25650)))
            vulns.add("[!] Extra Minecraft-like ports on the same IP: possible exposed backend/network servers");

        if (containsAny(software, "spigot", "craftbukkit", "bukkit") && !containsAny(software, "paper", "purpur", "pufferfish", "folia"))
            vulns.add("[*] Vanilla Bukkit/Spigot software: older builds are commonly outdated");

        if (javaStatus != null && javaStatus.optJSONObject("debug") != null && javaStatus.getJSONObject("debug").optBoolean("animatedmotd", false))
            vulns.add("[*] Animated MOTD detected: ping payload is being mutated");

        addPluginRisks(vulns, plugins);
        addPluginRisks(vulns, local.detectedPlugins);

        List<String> cves = jsonStringList(shodan, "vulns");
        for (String cve : cves) {
            if (vulns.size() >= 14)
                break;
            vulns.add("[!] " + cve);
        }

        if (local.detectedAntiCheats != null && !local.detectedAntiCheats.isEmpty())
            vulns.add("[*] Anti-cheats visible: " + String.join(", ", local.detectedAntiCheats));

        return vulns;
    }

    private static void addPluginRisks(List<String> vulns, Collection<String> plugins) {
        for (String plugin : plugins) {
            String name = plugin.toLowerCase(Locale.ROOT).replace(" ", "");
            String line = switch (name) {
                case "authme", "nlogin", "loginsecurity", "ultrauthenticator", "jpremium", "fastlogin" ->
                        "[!] Auth plugin (" + plugin + "): cracked-auth bypass surface";
                case "plugman", "plugmanx" ->
                        "[!] " + plugin + ": plugin load/reload can become RCE with operator access";
                case "worldedit", "fastasyncworldedit", "fawe" ->
                        "[!] " + plugin + ": //calc and schematic handling are common exploit surfaces";
                case "skript" ->
                        "[!] Skript: script execution if a writeable path or op is available";
                case "luckperms" ->
                        "[*] LuckPerms: check that the web editor / exposed API is not public";
                case "viaversion", "viabackwards", "viarewind" ->
                        "[*] Via* installed: wide version range, old-protocol attacks stay relevant";
                case "geyser", "floodgate", "geyser-spigot", "floodgate-spigot" ->
                        "[*] Geyser/Floodgate: Bedrock bridge and floodgate auth path are extra attack surface";
                case "protocolib" ->
                        "[*] ProtocolLib: often outdated and targeted";
                case "vault" ->
                        "[*] Vault: economy/permission bridge, useful after another plugin hole";
                default -> null;
            };
            if (line != null && !vulns.contains(line))
                vulns.add(line);
        }
    }

    private static DnsInfo resolveDns(String host) {
        List<String> ipv4 = new ArrayList<>();
        List<String> ipv6 = new ArrayList<>();
        List<String> hostnames = new ArrayList<>();
        List<String> srv = new ArrayList<>();

        if (host == null || host.isBlank())
            return new DnsInfo(ipv4, ipv6, hostnames, srv);

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address instanceof Inet6Address)
                    addUnique(ipv6, address.getHostAddress());
                else
                    addUnique(ipv4, address.getHostAddress());
                if (address.getCanonicalHostName() != null)
                    addUnique(hostnames, address.getCanonicalHostName());
            }
        } catch (Exception ignored) {
        }

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns:");
            InitialDirContext context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute attribute = attributes.get("SRV");
            if (attribute != null) {
                for (int i = 0; i < attribute.size(); i++)
                    srv.add(String.valueOf(attribute.get(i)));
            }
            context.close();
        } catch (Exception ignored) {
        }

        return new DnsInfo(ipv4, ipv6, hostnames, srv);
    }

    private static ServerAddress parseAddress(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return ServerAddress.parseString(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JSONObject fetchJson(String url) {
        Optional<String> body = HttpUtils.builder(url).acceptJson().get();
        if (body.isEmpty())
            return null;
        try {
            String text = body.get().trim();
            if (!text.startsWith("{"))
                return null;
            return new JSONObject(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<String> fetchReverseHosts(String ip) {
        Optional<String> body = HttpUtils.builder("https://api.hackertarget.com/reverseiplookup/?q=" + url(ip)).get();
        if (body.isEmpty())
            return List.of();

        String text = body.get().trim();
        if (text.isEmpty() || text.toLowerCase(Locale.ROOT).contains("error") || text.toLowerCase(Locale.ROOT).contains("api count"))
            return List.of();

        List<String> hosts = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String host = line.trim();
            if (!host.isEmpty() && !host.equals(ip))
                addUnique(hosts, host);
            if (hosts.size() >= 80)
                break;
        }
        return hosts;
    }

    private static List<String> collectPlugins(JSONObject javaStatus, JSONObject mcStatus, LocalSnapshot local) {
        LinkedHashSet<String> plugins = new LinkedHashSet<>();
        plugins.addAll(collectNamed(javaStatus, "plugins"));
        plugins.addAll(collectNamed(mcStatus, "plugins"));
        plugins.addAll(local.detectedPlugins);
        return new ArrayList<>(plugins);
    }

    private static List<String> collectNamed(JSONObject status, String key) {
        List<String> values = new ArrayList<>();
        if (status == null || !status.has(key))
            return values;

        Object raw = status.get(key);
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object entry = array.get(i);
                if (entry instanceof JSONObject object) {
                    String name = object.optString("name", "");
                    String version = object.optString("version", "");
                    if (!name.isBlank())
                        values.add(version.isBlank() ? name : name + " " + version);
                } else if (entry != null) {
                    values.add(String.valueOf(entry));
                }
            }
        }
        return values;
    }

    private static String formatNamedList(List<String> values) {
        if (values == null || values.isEmpty())
            return "None / hidden";
        return joinCapped(values, 18);
    }

    private static String formatPlayers(JSONObject javaStatus, JSONObject mcStatus, LocalSnapshot local) {
        JSONObject players = javaStatus == null ? null : javaStatus.optJSONObject("players");
        if (players != null && players.has("online"))
            return players.optInt("online") + " / " + players.optInt("max");

        JSONObject mcPlayers = mcStatus == null ? null : mcStatus.optJSONObject("players");
        if (mcPlayers != null && mcPlayers.has("online"))
            return mcPlayers.optInt("online") + " / " + mcPlayers.optInt("max");

        if (local != null && local.serverData != null && local.serverData.players != null)
            return local.serverData.players.online() + " / " + local.serverData.players.max();

        if (local != null && !local.localPlayers.isEmpty())
            return local.localPlayers.size() + " listed";

        return "Unknown";
    }

    private static String formatPlayerSample(JSONObject javaStatus, JSONObject mcStatus, LocalSnapshot local) {
        List<String> names = new ArrayList<>();
        JSONObject players = javaStatus == null ? null : javaStatus.optJSONObject("players");
        if (players != null && players.has("list")) {
            JSONArray list = players.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject player = list.optJSONObject(i);
                    if (player != null)
                        addUnique(names, player.optString("name", null));
                }
            }
        }

        JSONObject mcPlayers = mcStatus == null ? null : mcStatus.optJSONObject("players");
        if (mcPlayers != null && mcPlayers.has("list")) {
            JSONArray list = mcPlayers.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject player = list.optJSONObject(i);
                    if (player != null)
                        addUnique(names, firstNonBlank(player.optString("name_clean", null), player.optString("name_raw", null)));
                }
            }
        }

        if (names.isEmpty() && local != null)
            names.addAll(local.localPlayers);

        if (names.isEmpty())
            return null;
        return joinCapped(names, 12);
    }

    private static String formatVersion(JSONObject javaStatus, JSONObject mcStatus, ServerData serverData) {
        String version = firstNonBlank(jsonString(javaStatus, "version"), nestedString(mcStatus, "version", "name_clean"), componentText(serverData == null ? null : serverData.version));
        String protocolName = firstNonBlank(nestedString(javaStatus, "protocol", "name"), nestedString(mcStatus, "version", "name_clean"));
        int protocol = 0;
        if (javaStatus != null && javaStatus.optJSONObject("protocol") != null)
            protocol = javaStatus.getJSONObject("protocol").optInt("version", 0);
        else if (mcStatus != null && mcStatus.optJSONObject("version") != null)
            protocol = mcStatus.getJSONObject("version").optInt("protocol", 0);
        else if (serverData != null)
            protocol = serverData.protocol;

        if (version == null && protocol == 0)
            return "Unknown";
        if (protocol == 0)
            return version;
        if (version == null)
            return "protocol " + protocol + (protocolName == null ? "" : " / " + protocolName);
        return version + " (protocol " + protocol + (protocolName == null || protocolName.equals(version) ? "" : " / " + protocolName) + ")";
    }

    private static String formatDebug(JSONObject javaStatus) {
        if (javaStatus == null || javaStatus.optJSONObject("debug") == null)
            return "Unknown";
        JSONObject debug = javaStatus.getJSONObject("debug");
        return "query=" + yesNo(debug.optBoolean("query", false))
                + " ping=" + yesNo(debug.optBoolean("ping", false))
                + " srv=" + yesNo(debug.optBoolean("srv", false))
                + " bedrock=" + yesNo(debug.optBoolean("bedrock", false));
    }

    private static String formatSrv(DnsInfo dns, JSONObject javaStatus, JSONObject mcStatus) {
        if (!dns.srv.isEmpty())
            return String.join(" | ", dns.srv);

        JSONObject srv = mcStatus == null ? null : mcStatus.optJSONObject("srv_record");
        if (srv != null)
            return srv.optString("host", "?") + ":" + srv.optInt("port", 25565);

        if (javaStatus != null && javaStatus.optJSONObject("debug") != null && javaStatus.getJSONObject("debug").optBoolean("srv", false))
            return "Present";
        return "None";
    }

    private static String cleanMotd(JSONObject status) {
        List<String> lines = motdLines(status);
        if (lines.isEmpty())
            return null;
        return String.join(" / ", lines).replaceAll("\\s+", " ").trim();
    }

    private static List<String> motdLines(JSONObject status) {
        List<String> lines = new ArrayList<>();
        if (status == null)
            return lines;
        JSONObject motd = status.optJSONObject("motd");
        if (motd == null)
            return lines;
        lines.addAll(jsonStringList(motd, "clean"));
        if (lines.isEmpty() && motd.has("clean") && motd.get("clean") instanceof String text)
            lines.add(text);
        return lines;
    }

    private static String cleanMotdMcStatus(JSONObject status) {
        if (status == null)
            return null;
        JSONObject motd = status.optJSONObject("motd");
        if (motd == null)
            return null;
        return blankToNull(motd.optString("clean", null));
    }

    private static String inferSoftware(LocalSnapshot local) {
        if (local.brand == null)
            return null;
        return local.brand;
    }

    private static List<String> collectIpv4(String rawIp, DnsInfo dns, SocketAddress remote, JSONObject javaStatus, JSONObject mcStatus) {
        List<String> values = new ArrayList<>(dns.ipv4);
        addUnique(values, sanitizeIp(rawIp));
        addUnique(values, socketIp(remote));
        addUnique(values, sanitizeIp(jsonString(javaStatus, "ip")));
        addUnique(values, sanitizeIp(jsonString(mcStatus, "ip_address")));
        values.removeIf(value -> value.contains(":"));
        return values;
    }

    private static List<String> collectIpv6(DnsInfo dns, SocketAddress remote) {
        List<String> values = new ArrayList<>(dns.ipv6);
        String remoteIp = socketIp(remote);
        if (remoteIp != null && remoteIp.contains(":"))
            addUnique(values, remoteIp);
        return values;
    }

    private static String reverseName(String ip) {
        if (ip == null)
            return null;
        try {
            String name = InetAddress.getByName(ip).getCanonicalHostName();
            if (name == null || name.equals(ip))
                return null;
            return name;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String socketToHostPort(SocketAddress address) {
        if (!(address instanceof InetSocketAddress socket))
            return address == null ? null : address.toString();
        String host = socket.getAddress() == null ? socket.getHostString() : socket.getAddress().getHostAddress();
        return host + ":" + socket.getPort();
    }

    private static String socketIp(SocketAddress address) {
        if (!(address instanceof InetSocketAddress socket) || socket.getAddress() == null)
            return null;
        return sanitizeIp(socket.getAddress().getHostAddress());
    }

    private static String sanitizeIp(String ip) {
        if (ip == null || ip.isBlank())
            return null;
        String value = ip.trim();
        if (value.startsWith("/"))
            value = value.substring(1);
        int scope = value.indexOf('%');
        if (scope >= 0)
            value = value.substring(0, scope);
        if (value.startsWith("[") && value.endsWith("]"))
            value = value.substring(1, value.length() - 1);
        return value;
    }

    private static String encodeAddress(String host, int port) {
        String address = host;
        if (host != null && host.contains(":") && !host.startsWith("["))
            address = "[" + host + "]";
        if (port > 0 && port != 25565)
            address = address + ":" + port;
        return address.replace(" ", "");
    }

    private static String displayAddress(String host, int port) {
        if (host == null)
            return "Unknown";
        return host + ":" + port;
    }

    private static boolean looksLikeIp(String value) {
        return value != null && (IPV4_PATTERN.matcher(value).matches() || value.contains(":"));
    }

    private static boolean isLikelyMinecraftHost(String hostname, String rootDomain) {
        String value = hostname.toLowerCase(Locale.ROOT);
        if (rootDomain != null && value.endsWith(rootDomain))
            return true;
        return value.startsWith("play.")
                || value.startsWith("mc.")
                || value.startsWith("go.")
                || value.startsWith("hub.")
                || value.startsWith("proxy.")
                || value.startsWith("bungee.")
                || value.startsWith("velocity.")
                || value.startsWith("survival.")
                || value.startsWith("skyblock.")
                || value.startsWith("bedwars.")
                || value.contains("minecraft")
                || value.contains(".hypixel.")
                || value.contains("mine.");
    }

    private static boolean isPriorityHost(String hostname) {
        String value = hostname.toLowerCase(Locale.ROOT);
        return value.startsWith("play.") || value.startsWith("mc.") || value.startsWith("go.") || value.startsWith("hub.");
    }

    private static String rootDomain(String host) {
        if (host == null || looksLikeIp(host))
            return null;
        String[] parts = host.toLowerCase(Locale.ROOT).split("\\.");
        if (parts.length < 2)
            return host.toLowerCase(Locale.ROOT);
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static VersionSpan parseVersionSpan(String text) {
        if (text == null)
            return VersionSpan.none();
        Matcher matcher = VERSION_PATTERN.matcher(text);
        Integer min = null;
        Integer max = null;
        while (matcher.find()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            int packed = major * 1_000_000 + minor * 1_000 + patch;
            min = min == null ? packed : Math.min(min, packed);
            max = max == null ? packed : Math.max(max, packed);
        }
        return min == null ? VersionSpan.none() : new VersionSpan(min, max);
    }

    private static boolean containsPlugin(Collection<String> plugins, String... names) {
        for (String plugin : plugins) {
            String value = plugin.toLowerCase(Locale.ROOT).replace(" ", "");
            for (String name : names) {
                if (value.contains(name))
                    return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... parts) {
        if (value == null)
            return false;
        for (String part : parts) {
            if (value.contains(part))
                return true;
        }
        return false;
    }

    private static String componentText(Component component) {
        if (component == null)
            return null;
        return blankToNull(component.getString());
    }

    private static String prettyEnum(String value) {
        if (value == null)
            return null;
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String jsonString(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key))
            return null;
        String value = object.optString(key, null);
        return blankToNull(value);
    }

    private static String nestedString(JSONObject object, String parent, String key) {
        if (object == null)
            return null;
        JSONObject child = object.optJSONObject(parent);
        return jsonString(child, key);
    }

    private static List<String> jsonStringList(JSONObject object, String key) {
        List<String> values = new ArrayList<>();
        if (object == null || !object.has(key))
            return values;
        Object raw = object.get(key);
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++)
                addUnique(values, blankToNull(String.valueOf(array.get(i))));
        } else if (raw instanceof String text) {
            addUnique(values, blankToNull(text));
        }
        return values;
    }

    private static List<Integer> jsonIntList(JSONObject object, String key) {
        List<Integer> values = new ArrayList<>();
        if (object == null || !(object.opt(key) instanceof JSONArray array))
            return values;
        for (int i = 0; i < array.length(); i++)
            values.add(array.optInt(i));
        return values;
    }

    private static CompletableFuture<JSONObject> completedJson() {
        return CompletableFuture.completedFuture(null);
    }

    private static JSONObject joinJson(CompletableFuture<JSONObject> future) {
        try {
            return future.get(12, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> joinList(CompletableFuture<List<String>> future) {
        try {
            List<String> value = future.get(12, TimeUnit.SECONDS);
            return value == null ? List.of() : value;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String joinOrUnknown(List<String> values) {
        return values == null || values.isEmpty() ? "Unknown" : String.join(", ", values);
    }

    private static String joinCapped(List<String> values, int max) {
        if (values.size() <= max)
            return String.join(", ", values);
        return String.join(", ", values.subList(0, max)) + " (+" + (values.size() - max) + " more)";
    }

    private static String joinInts(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0)
                builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String joinNonBlank(String separator, String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank())
                continue;
            if (!builder.isEmpty())
                builder.append(separator);
            builder.append(part);
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> values = new ArrayList<>();
        for (List<String> list : lists) {
            if (list != null)
                values.addAll(list);
        }
        return values;
    }

    private static List<String> unique(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank())
                unique.add(value);
        }
        return new ArrayList<>(unique);
    }

    private static void addUnique(List<String> values, String value) {
        if (value == null || value.isBlank() || values.contains(value))
            return;
        values.add(value);
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record LocalSnapshot(
            String address,
            String host,
            int port,
            ServerData serverData,
            ClientPacketListener listener,
            Connection connection,
            SocketAddress remote,
            String brand,
            boolean onlineMode,
            boolean encrypted,
            boolean connected,
            int ping,
            String resourcePackStatus,
            List<String> localPlayers,
            List<String> serverLinks,
            List<String> detectedPlugins,
            List<String> detectedAntiCheats
    ) {
    }

    private record DnsInfo(List<String> ipv4, List<String> ipv6, List<String> hostnames, List<String> srv) {
    }

    private record RelatedServer(String kind, String target, String detail, String note) {
        private String describe() {
            StringBuilder builder = new StringBuilder(kind).append(": ").append(target);
            if (detail != null && !detail.isBlank())
                builder.append(" [").append(detail).append("]");
            if (note != null && !note.isBlank())
                builder.append(" (").append(note.replaceAll("\\s+", " ").trim()).append(")");
            return builder.toString();
        }
    }

    private record VersionSpan(Integer min, Integer max) {
        private static VersionSpan none() {
            return new VersionSpan(null, null);
        }

        private boolean overlaps(int minMajor, int minMinor, int minPatch, int maxMajor, int maxMinor, int maxPatch) {
            if (min == null || max == null)
                return false;
            int rangeMin = minMajor * 1_000_000 + minMinor * 1_000 + minPatch;
            int rangeMax = maxMajor * 1_000_000 + maxMinor * 1_000 + maxPatch;
            return min <= rangeMax && max >= rangeMin;
        }
    }
}
