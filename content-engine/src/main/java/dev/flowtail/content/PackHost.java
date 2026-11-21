package dev.flowtail.content;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;

/** Built-in HTTP endpoint for the exact generated resource-pack bytes. */
final class PackHost implements AutoCloseable {
    private final HttpServer server;
    private final String url;
    private final byte[] sha1;

    PackHost(ContentSettings settings, PackGenerator.Result pack) throws IOException {
        Path zip = pack.zip().toAbsolutePath().normalize();
        byte[] bytes = Files.readAllBytes(zip);
        String calculated = hex(digest("SHA-1", bytes));
        String recorded = Files.readString(zip.getParent().resolve("pack.sha1")).trim();
        if (!calculated.equalsIgnoreCase(recorded) || !calculated.equalsIgnoreCase(pack.sha1())) {
            throw new IllegalStateException("Resource pack SHA-1 mismatch at startup: calculated=" + calculated
                    + " file=" + recorded + " generator=" + pack.sha1());
        }
        this.sha1 = digest("SHA-1", bytes);
        this.server = HttpServer.create(new InetSocketAddress(settings.hostPort()), 0);
        this.server.createContext("/pack.zip", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        this.server.start();
        String base = settings.publicUrl();
        if (base.equalsIgnoreCase("auto")) base = detectLanAddress();
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
        this.url = base.endsWith(".zip") ? base : base.replaceAll("/+$", "") + "/pack.zip";
    }

    String url() { return url; }
    byte[] sha1() { return sha1.clone(); }

    @Override public void close() { server.stop(0); }

    private String detectLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        return "http://" + address.getHostAddress() + ":" + server.getAddress().getPort();
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not detect a LAN address for the resource pack URL", error);
        }
        throw new IllegalStateException("No LAN IPv4 address is available; set pack.host.public-url explicitly");
    }

    private static byte[] digest(String algorithm, byte[] bytes) {
        try { return MessageDigest.getInstance(algorithm).digest(bytes); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }
}
