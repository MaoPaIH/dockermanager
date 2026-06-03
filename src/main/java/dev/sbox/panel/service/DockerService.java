package dev.sbox.panel.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.command.PullImageResultCallback;
import dev.sbox.panel.domain.DockerStatsSample;
import dev.sbox.panel.domain.NodeInstance;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class DockerService {

    private static final String FSCARMEN_COMPATIBILITY_COMMAND = """
            set -eu
            if [ -f /sing-box/init.sh ] && grep -q 'default_http_client' /sing-box/init.sh; then
              sed -i '/"default_http_client": "http-client-direct",/d' /sing-box/init.sh
              sed -i '/cat > ${WORK_DIR}\\/conf\\/07_http_clients.json << EOF/,/^EOF$/d' /sing-box/init.sh
            fi
            exec ./init.sh
            """;

    private final DockerClient dockerClient;

    public DockerService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String createAndStart(NodeInstance node, List<String> env, boolean applyFscarmenCompatibilityPatch) throws InterruptedException {
        if (node.isPullLatest()) {
            pullImage(node.getImageName());
        }

        var exposedPorts = new ArrayList<ExposedPort>();
        var bindings = new Ports();
        for (int port = node.getStartPort(); port < node.getStartPort() + node.getPortBlockSize(); port++) {
            ExposedPort tcp = ExposedPort.tcp(port);
            ExposedPort udp = ExposedPort.udp(port);
            exposedPorts.add(tcp);
            exposedPorts.add(udp);
            bindings.bind(tcp, Ports.Binding.bindPort(port));
            bindings.bind(udp, Ports.Binding.bindPort(port));
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("sb-panel", "true");
        labels.put("sb-panel.node", node.getName());

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(bindings)
                .withRestartPolicy(RestartPolicy.alwaysRestart());

        try {
            CreateContainerResponse created = createContainer(node, env, exposedPorts, hostConfig, labels, applyFscarmenCompatibilityPatch);
            dockerClient.startContainerCmd(created.getId()).exec();
            return created.getId();
        } catch (NotFoundException e) {
            pullImage(node.getImageName());
            CreateContainerResponse created = createContainer(node, env, exposedPorts, hostConfig, labels, applyFscarmenCompatibilityPatch);
            dockerClient.startContainerCmd(created.getId()).exec();
            return created.getId();
        }
    }

    private CreateContainerResponse createContainer(NodeInstance node, List<String> env, List<ExposedPort> exposedPorts,
                                                    HostConfig hostConfig, Map<String, String> labels,
                                                    boolean applyFscarmenCompatibilityPatch) {
        var createCommand = dockerClient.createContainerCmd(node.getImageName())
                .withName(node.getContainerName())
                .withEnv(env)
                .withExposedPorts(exposedPorts)
                .withHostConfig(hostConfig)
                .withLabels(labels);

        if (shouldApplyFscarmenPatch(node, applyFscarmenCompatibilityPatch)) {
            // Current fscarmen/sb images may pair a 1.14 config template with a 1.13 core.
            createCommand.withCmd("sh", "-c", FSCARMEN_COMPATIBILITY_COMMAND);
        }

        return createCommand.exec();
    }

    private void pullImage(String imageName) throws InterruptedException {
        dockerClient.pullImageCmd(imageName)
                .exec(new PullImageResultCallback())
                .awaitCompletion();
    }

    private static boolean shouldApplyFscarmenPatch(NodeInstance node, boolean enabled) {
        return enabled
                && node.getImageName() != null
                && node.getImageName().toLowerCase(Locale.ROOT).startsWith("fscarmen/sb");
    }

    public void start(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stop(String containerId) {
        dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
    }

    public void restart(String containerId) {
        dockerClient.restartContainerCmd(containerId).withTimeout(10).exec();
    }

    public void remove(String containerId) {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
    }

    public boolean isRunning(String containerId) {
        try {
            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            return inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (NotFoundException e) {
            return false;
        }
    }

    public String stateText(String containerId) {
        try {
            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            return inspect.getState() == null ? "unknown" : inspect.getState().getStatus();
        } catch (NotFoundException e) {
            return "missing";
        }
    }

    public DockerStatsSample stats(String containerId) throws InterruptedException, IOException {
        var latch = new CountDownLatch(1);
        var holder = new DockerStatsHolder();
        ResultCallback<Statistics> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Statistics stats) {
                holder.sample = toSample(stats);
                latch.countDown();
                closeQuietly(this);
            }

            @Override
            public void onError(Throwable throwable) {
                holder.error = throwable;
                latch.countDown();
            }
        };
        dockerClient.statsCmd(containerId).exec(callback);
        boolean received = latch.await(8, TimeUnit.SECONDS);
        closeQuietly(callback);
        if (!received) {
            throw new IOException("读取 Docker stats 超时");
        }
        if (holder.error != null) {
            throw new IOException("读取 Docker stats 失败", holder.error);
        }
        return holder.sample == null ? new DockerStatsSample(0, 0) : holder.sample;
    }

    public String containerLogs(String containerId, int tail) throws InterruptedException, IOException {
        var output = new ByteArrayOutputStream();
        var latch = new CountDownLatch(1);
        ResultCallback<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    output.write(frame.getPayload());
                } catch (IOException ignored) {
                    // The caller will still receive whatever was collected.
                }
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
        };
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail)
                .exec(callback);
        latch.await(8, TimeUnit.SECONDS);
        closeQuietly(callback);
        return output.toString(StandardCharsets.UTF_8);
    }

    public String readSubscriptionFile(String containerId, String name) throws InterruptedException, IOException {
        return execRead(containerId, "cat", "/sing-box/subscribe/" + name);
    }

    public String readListFile(String containerId) throws InterruptedException, IOException {
        return execRead(containerId, "cat", "/sing-box/list");
    }

    public String readBoxLog(String containerId, int tail) throws InterruptedException, IOException {
        return execRead(containerId, "sh", "-c", "tail -n " + tail + " /sing-box/logs/box.log 2>/dev/null || true");
    }

    private String execRead(String containerId, String... command) throws InterruptedException, IOException {
        var output = new ByteArrayOutputStream();
        var latch = new CountDownLatch(1);
        var created = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec();
        ResultCallback<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    output.write(frame.getPayload());
                } catch (IOException ignored) {
                    // The caller will still receive whatever was collected.
                }
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
        };
        dockerClient.execStartCmd(created.getId()).exec(callback);
        latch.await(8, TimeUnit.SECONDS);
        closeQuietly(callback);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static DockerStatsSample toSample(Statistics stats) {
        if (stats.getNetworks() == null || stats.getNetworks().isEmpty()) {
            return new DockerStatsSample(0, 0);
        }
        long rx = 0;
        long tx = 0;
        for (var network : stats.getNetworks().values()) {
            if (network.getRxBytes() != null) {
                rx += network.getRxBytes();
            }
            if (network.getTxBytes() != null) {
                tx += network.getTxBytes();
            }
        }
        return new DockerStatsSample(rx, tx);
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Best-effort cleanup of Docker streaming callbacks.
        }
    }

    private static class DockerStatsHolder {
        DockerStatsSample sample;
        Throwable error;
    }
}
