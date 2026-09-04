package uk.co.mmuminecraftsociety.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

record DeploymentState(String id, long startedAt) {
    private static final Path STATE = Path.of("/server/deployment.properties");
    private static final Path ACK = Path.of("/server/deployment-drained");

    static DeploymentState read() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(STATE)) {
            properties.load(reader);
        } catch (NoSuchFileException ignored) {
            return null;
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid deployment state", error);
        }
        if ("false".equals(properties.getProperty("updating"))) return null;
        try {
            String id = properties.getProperty("id", "");
            long startedAt = Long.parseLong(properties.getProperty("startedAt", ""));
            if (!"true".equals(properties.getProperty("updating")) || id.isBlank() || startedAt <= 0) {
                throw new IllegalArgumentException("Invalid deployment state");
            }
            return new DeploymentState(id, startedAt);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid deployment state", error);
        }
    }

    void acknowledge(boolean hadPlayers, boolean ready) throws IOException {
        Path temporary = ACK.resolveSibling("deployment-drained.tmp");
        Files.writeString(temporary, id + " " + hadPlayers + " " + ready + "\n");
        Files.move(temporary, ACK, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
