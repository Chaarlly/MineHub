package com.br.minehub.main.service;

import com.br.minehub.main.model.RemoteFileItem;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SftpService {

    private SSHClient ssh;
    private SFTPClient sftp;

    public void connect(String host, int port, String username, String password) throws IOException {
        ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());

        ssh.connect(host, port);
        ssh.authPassword(username, password);

        sftp = ssh.newSFTPClient();
    }

    public boolean isConnected() {
        return ssh != null && ssh.isConnected() && ssh.isAuthenticated() && sftp != null;
    }

    public List<RemoteFileItem> listFiles(String path) throws IOException {
        List<RemoteFileItem> files = new ArrayList<>();

        for (RemoteResourceInfo info : sftp.ls(path)) {
            String name = info.getName();

            if (name.equals(".") || name.equals("..")) {
                continue;
            }

            boolean dir = info.isDirectory();

            files.add(new RemoteFileItem(
                    name,
                    getType(name, dir),
                    dir ? "-" : formatSize(info.getAttributes().getSize()),
                    dir
            ));
        }

        files.sort(
                Comparator.comparing(RemoteFileItem::isDirectory).reversed()
                        .thenComparing(RemoteFileItem::getName, String.CASE_INSENSITIVE_ORDER)
        );

        return files;
    }

    private String getType(String name, boolean dir) {
        if (dir) return "Pasta";

        String lower = name.toLowerCase();

        if (lower.endsWith(".jar")) return "Plugin/JAR";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "Config YAML";
        if (lower.endsWith(".log")) return "Log";
        if (lower.endsWith(".txt")) return "Texto";
        if (lower.endsWith(".zip")) return "ZIP";
        if (lower.endsWith(".rar")) return "RAR";
        if (lower.endsWith(".json")) return "JSON";
        if (lower.endsWith(".properties")) return "Properties";

        return "Arquivo";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    public String readFile(String remotePath) throws IOException {
        Path tempFile = Files.createTempFile("minehub-read-", ".tmp");

        sftp.get(remotePath, tempFile.toString());

        String content = Files.readString(tempFile, StandardCharsets.UTF_8);

        Files.deleteIfExists(tempFile);

        return content;
    }

    public void writeFile(String remotePath, String content) throws IOException {
        Path tempFile = Files.createTempFile("minehub-write-", ".tmp");

        Files.writeString(tempFile, content, StandardCharsets.UTF_8);

        sftp.put(tempFile.toString(), remotePath);

        Files.deleteIfExists(tempFile);
    }

    public void disconnect() throws IOException {
        if (sftp != null) sftp.close();
        if (ssh != null) ssh.disconnect();
    }
}